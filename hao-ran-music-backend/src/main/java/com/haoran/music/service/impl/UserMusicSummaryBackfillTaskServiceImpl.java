


package com.haoran.music.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haoran.music.entity.UserMusicSummaryBackfillTask;
import com.haoran.music.mapper.UserMusicSummaryBackfillTaskMapper;
import com.haoran.music.service.UserMusicSummaryBackfillTaskService;
import com.haoran.music.service.UserMusicSummaryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Date;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;





@Slf4j
@Service
public class UserMusicSummaryBackfillTaskServiceImpl implements UserMusicSummaryBackfillTaskService {

    private static final String DAILY = "daily";
    private static final String MONTHLY = "monthly";
    private static final String PENDING = "pending";
    private static final String RUNNING = "running";
    private static final String SUCCESS = "success";
    private static final int MAX_ATTEMPTS = 3;
    private static final int MAX_DAILY_PERIODS = 366;
    private static final int MAX_MONTHLY_PERIODS = 60;

    @Resource
    private UserMusicSummaryBackfillTaskMapper taskMapper;

    @Resource
    private UserMusicSummaryService summaryService;

    @Resource
    private ObjectMapper objectMapper;

    @Resource(name = "asyncExecutor")
    private Executor asyncExecutor;

    @Resource
    private ThreadPoolTaskScheduler taskScheduler;

    @Override
    public Map<String, Object> submitDaily(LocalDate startDate, LocalDate endDate, Long operatorId) {
        LocalDate latest = LocalDate.now().minusDays(1);
        LocalDate start = startDate == null ? latest : startDate;
        LocalDate end = endDate == null ? start : endDate;
        validateRange(start, end, latest, MAX_DAILY_PERIODS, "日");
        long periods = ChronoUnit.DAYS.between(start, end) + 1;
        return submitInternal(DAILY, start.toString(), end.toString(), (int) periods, operatorId);
    }

    @Override
    public Map<String, Object> submitMonthly(YearMonth startMonth, YearMonth endMonth, Long operatorId) {
        YearMonth current = YearMonth.now();
        YearMonth start = startMonth == null ? current : startMonth;
        YearMonth end = endMonth == null ? start : endMonth;
        if (end.isAfter(current)) {
            throw new IllegalArgumentException("月汇总结束月份不能晚于当前月份");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("结束月份不能早于开始月份");
        }
        long periods = ChronoUnit.MONTHS.between(start, end) + 1;
        if (periods > MAX_MONTHLY_PERIODS) {
            throw new IllegalArgumentException("单次月汇总回填最多 " + MAX_MONTHLY_PERIODS + " 个月");
        }
        return submitInternal(MONTHLY, start.toString(), end.toString(), (int) periods, operatorId);
    }

    @Override
    public Map<String, Object> getTask(String taskId) {
        if (taskId == null || taskId.trim().isEmpty()) {
            return null;
        }
        return toMap(taskMapper.selectActiveByTaskId(taskId.trim()));
    }

    @Override
    public Map<String, Object> retry(String taskId, Long operatorId) {
        if (operatorId == null || operatorId <= 0) {
            throw new IllegalArgumentException("请先登录");
        }
        if (taskId == null || taskId.trim().isEmpty()) {
            throw new IllegalArgumentException("taskId 不能为空");
        }
        String normalizedTaskId = taskId.trim();
        UserMusicSummaryBackfillTask task = taskMapper.selectActiveByTaskId(normalizedTaskId);
        if (task == null) {
            return null;
        }
        if (SUCCESS.equals(task.getStatus()) || RUNNING.equals(task.getStatus())) {
            return toMap(task);
        }
        if (task.getAttemptCount() != null && task.getMaxAttempts() != null
                && task.getAttemptCount() >= task.getMaxAttempts()) {
            throw new IllegalStateException("汇总回填任务已达到最大重试次数");
        }
        if (taskMapper.requestRetry(normalizedTaskId, operatorId) == 1) {
            submitExecution(normalizedTaskId);
        }
        return getTask(normalizedTaskId);
    }

    @Override
    public Map<String, Object> getTaskStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("statuses", taskMapper.selectStatusSummary());
        result.put("maxAttempts", MAX_ATTEMPTS);
        return result;
    }

    @Override
    public int retryDueTasks(int limit) {
        int safeLimit = limit <= 0 ? 10 : Math.min(limit, 10);
        int recovered = taskMapper.recoverStaleRunningTasks();
        if (recovered > 0) {
            log.warn("event=user_music_summary_backfill_stale_tasks_recovered count={}", recovered);
        }
        int submitted = 0;
        for (String taskId : taskMapper.selectDueTaskIds(safeLimit)) {
            if (submitExecution(taskId)) {
                submitted++;
            }
        }
        return submitted;
    }

    private Map<String, Object> submitInternal(String type, String start, String end,
                                               int totalPeriods, Long operatorId) {
        if (operatorId == null || operatorId <= 0) {
            throw new IllegalArgumentException("请先登录");
        }
        String businessKey = type + ":" + start + ":" + end;
        UserMusicSummaryBackfillTask existing = taskMapper.selectByBusinessKey(businessKey);
        if (existing != null) {
            return resetAndSubmitExisting(existing, operatorId);
        }

        UserMusicSummaryBackfillTask task = new UserMusicSummaryBackfillTask();
        task.setTaskId("summary-backfill-" + System.currentTimeMillis() + "-"
                + UUID.randomUUID().toString().replace("-", ""));
        task.setBusinessKey(businessKey);
        task.setOperatorId(operatorId);
        task.setSummaryType(type);
        task.setStartPeriod(start);
        task.setEndPeriod(end);
        task.setCursorPeriod(start);
        task.setTotalPeriods(totalPeriods);
        task.setCompletedPeriods(0);
        task.setAffectedRows(0);
        task.setStatus(PENDING);
        task.setMaxAttempts(MAX_ATTEMPTS);
        task.setDeleted(0);
        try {
            if (taskMapper.insertTask(task) != 1) {
                throw new IllegalStateException("音乐汇总回填任务写入失败");
            }
        } catch (DuplicateKeyException e) {
            UserMusicSummaryBackfillTask raced = taskMapper.selectByBusinessKey(businessKey);
            if (raced == null) {
                throw e;
            }
            return resetAndSubmitExisting(raced, operatorId);
        }
        submitExecution(task.getTaskId());
        return getTask(task.getTaskId());
    }

    private Map<String, Object> resetAndSubmitExisting(UserMusicSummaryBackfillTask task, Long operatorId) {
        if (!RUNNING.equals(task.getStatus())
                && taskMapper.resetByBusinessKey(task.getBusinessKey(), operatorId, MAX_ATTEMPTS) == 1) {
            submitExecution(task.getTaskId());
        }
        return getTask(task.getTaskId());
    }

    private boolean submitExecution(String taskId) {
        try {
            asyncExecutor.execute(() -> executeTask(taskId));
            return true;
        } catch (RejectedExecutionException e) {
            int marked = taskMapper.markSubmissionRejected(taskId, "音乐汇总回填执行队列已满");
            log.warn("event=user_music_summary_backfill_submission_rejected taskId={} stateMarked={} errorType={}",
                    taskId, marked == 1, e.getClass().getSimpleName());
            return false;
        }
    }

    private boolean executeTask(String taskId) {
        String workerId = UUID.randomUUID().toString();
        if (taskMapper.claimTask(taskId, workerId) <= 0) {
            return false;
        }
        AtomicBoolean leaseLost = new AtomicBoolean(false);
        ScheduledFuture<?> heartbeat;
        try {
            heartbeat = taskScheduler.scheduleAtFixedRate(() -> {
                try {
                    if (taskMapper.heartbeat(taskId, workerId) <= 0) {
                        leaseLost.set(true);
                    }
                } catch (Exception ex) {
                    leaseLost.set(true);
                    log.warn("event=user_music_summary_backfill_heartbeat_failed taskId={} workerId={} errorType={}",
                            taskId, workerId, ex.getClass().getSimpleName());
                }
            }, new Date(System.currentTimeMillis() + 60_000L), 60_000L);
        } catch (Exception ex) {
            int marked = taskMapper.markFailed(taskId, workerId, "音乐汇总回填租约启动失败", 1);
            log.warn("event=user_music_summary_backfill_heartbeat_start_failed taskId={} workerId={} stateMarked={} errorType={}",
                    taskId, workerId, marked == 1, ex.getClass().getSimpleName());
            return false;
        }
        UserMusicSummaryBackfillTask task = taskMapper.selectActiveByTaskId(taskId);
        if (task == null) {
            heartbeat.cancel(false);
            log.warn("event=user_music_summary_backfill_claimed_task_missing taskId={} workerId={}",
                    taskId, workerId);
            return false;
        }

        int completed = safeInt(task.getCompletedPeriods());
        int affected = safeInt(task.getAffectedRows());
        try {
            validateClaimedTask(task);
            List<String> failures = readFailuresStrict(task);
            if (DAILY.equals(task.getSummaryType())) {
                LocalDate cursor = LocalDate.parse(task.getCursorPeriod());
                LocalDate end = LocalDate.parse(task.getEndPeriod());
                while (!cursor.isAfter(end)) {
                    requireLease(leaseLost);
                    String period = cursor.toString();
                    try {
                        affected += summaryService.refreshDailySummary(cursor);
                        failures.remove(period);
                    } catch (Exception e) {
                        failures.clear();
                        failures.add(period);
                        persistProgress(taskId, workerId, period, completed, affected, failures, leaseLost);
                        log.warn("event=user_music_summary_backfill_period_failed taskId={} summaryType={} period={} errorType={}",
                                taskId, DAILY, period, e.getClass().getSimpleName());
                        throw new IllegalStateException("音乐汇总回填周期执行失败");
                    }
                    completed++;
                    cursor = cursor.plusDays(1);
                    persistProgress(taskId, workerId, cursor.toString(), completed, affected, failures, leaseLost);
                }
            } else {
                YearMonth cursor = YearMonth.parse(task.getCursorPeriod());
                YearMonth end = YearMonth.parse(task.getEndPeriod());
                while (!cursor.isAfter(end)) {
                    requireLease(leaseLost);
                    String period = cursor.toString();
                    try {
                        affected += summaryService.refreshMonthlySummary(cursor);
                        failures.remove(period);
                    } catch (Exception e) {
                        failures.clear();
                        failures.add(period);
                        persistProgress(taskId, workerId, period, completed, affected, failures, leaseLost);
                        log.warn("event=user_music_summary_backfill_period_failed taskId={} summaryType={} period={} errorType={}",
                                taskId, MONTHLY, period, e.getClass().getSimpleName());
                        throw new IllegalStateException("音乐汇总回填周期执行失败");
                    }
                    completed++;
                    cursor = cursor.plusMonths(1);
                    persistProgress(taskId, workerId, cursor.toString(), completed, affected, failures, leaseLost);
                }
            }

            if (failures.isEmpty()) {
                int marked = taskMapper.markSuccess(taskId, workerId);
                if (marked != 1) {
                    log.warn("event=user_music_summary_backfill_completion_rejected taskId={} workerId={}",
                            taskId, workerId);
                    return false;
                }
                log.info("event=user_music_summary_backfill_completed taskId={} summaryType={} completedPeriods={} affectedRows={}",
                        taskId, task.getSummaryType(), completed, affected);
                return true;
            } else {
                int attempts = safeInt(task.getAttemptCount());
                Integer retryDelay = attempts < safeInt(task.getMaxAttempts(), MAX_ATTEMPTS)
                        ? Math.toIntExact(Math.min(60L, 5L * Math.max(1, attempts))) : null;
                int marked = taskMapper.markFailed(
                        taskId, workerId, "音乐汇总回填存在未完成周期", retryDelay);
                log.warn("event=user_music_summary_backfill_incomplete taskId={} summaryType={} failedPeriods={} retryDelayMinutes={} stateMarked={}",
                        taskId, task.getSummaryType(), failures.size(), retryDelay, marked == 1);
                return false;
            }
        } catch (IllegalArgumentException e) {
            int marked = taskMapper.markTerminalFailed(
                    taskId, workerId, "音乐汇总回填任务参数无效");
            log.warn("event=user_music_summary_backfill_terminal_failed taskId={} summaryType={} category=INVALID_TASK_ARGUMENT stateMarked={}",
                    taskId, task.getSummaryType(), marked == 1);
            return false;
        } catch (Exception e) {
            int attempts = safeInt(task.getAttemptCount());
            Integer retryDelay = attempts < safeInt(task.getMaxAttempts(), MAX_ATTEMPTS)
                    ? Math.toIntExact(Math.min(60L, 5L * Math.max(1, attempts))) : null;
            int marked = taskMapper.markFailed(
                    taskId, workerId, "音乐汇总回填执行失败", retryDelay);
            log.warn("event=user_music_summary_backfill_processing_failed taskId={} summaryType={} retryDelayMinutes={} stateMarked={} errorType={}",
                    taskId, task.getSummaryType(), retryDelay, marked == 1,
                    e.getClass().getSimpleName());
            return false;
        } finally {
            heartbeat.cancel(false);
        }
    }

    private void persistProgress(String taskId,
                                 String workerId,
                                 String cursorPeriod,
                                 int completed,
                                 int affected,
                                 List<String> failures,
                                 AtomicBoolean leaseLost) throws Exception {
        if (taskMapper.updateProgress(taskId, workerId, cursorPeriod, completed, affected,
                objectMapper.writeValueAsString(failures)) != 1) {
            leaseLost.set(true);
            requireLease(leaseLost);
        }
    }

    private void requireLease(AtomicBoolean leaseLost) {
        if (leaseLost.get()) {
            throw new IllegalStateException("汇总回填任务租约已失效");
        }
    }

    private void validateRange(LocalDate start, LocalDate end, LocalDate latest,
                               int maxPeriods, String label) {
        if (end.isAfter(latest)) {
            throw new IllegalArgumentException(label + "汇总只回填完整日期，结束日期不能晚于昨天");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("结束日期不能早于开始日期");
        }
        long periods = ChronoUnit.DAYS.between(start, end) + 1;
        if (periods > maxPeriods) {
            throw new IllegalArgumentException("单次" + label + "汇总回填最多 " + maxPeriods + " 天");
        }
    }

    private List<String> readFailuresForResponse(UserMusicSummaryBackfillTask task) {
        String value = task.getFailedPeriodsJson();
        if (value == null || value.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            List<String> stored = objectMapper.readValue(value, new TypeReference<List<String>>() {
            });
            List<String> periods = new ArrayList<>();
            for (String item : stored) {
                if (item == null) {
                    continue;
                }
                String period = item.contains(":") ? item.substring(0, item.indexOf(':')) : item;
                if (period.matches("\\d{4}-\\d{2}(-\\d{2})?") && !periods.contains(period)) {
                    periods.add(period);
                }
            }
            return periods;
        } catch (Exception e) {
            log.warn("event=user_music_summary_backfill_failed_periods_parse_failed taskId={} errorType={}",
                    task.getTaskId(), e.getClass().getSimpleName());
            return new ArrayList<>();
        }
    }

    private List<String> readFailuresStrict(UserMusicSummaryBackfillTask task) {
        List<String> failures;
        if (task.getFailedPeriodsJson() == null || task.getFailedPeriodsJson().trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            failures = objectMapper.readValue(task.getFailedPeriodsJson(), new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            throw new IllegalArgumentException("回填失败周期格式无效");
        }
        for (String period : failures) {
            if (DAILY.equals(task.getSummaryType())) {
                LocalDate value = LocalDate.parse(period);
                if (value.isBefore(LocalDate.parse(task.getStartPeriod()))
                        || value.isAfter(LocalDate.parse(task.getEndPeriod()))) {
                    throw new IllegalArgumentException("回填失败周期超出任务范围");
                }
            } else {
                YearMonth value = YearMonth.parse(period);
                if (value.isBefore(YearMonth.parse(task.getStartPeriod()))
                        || value.isAfter(YearMonth.parse(task.getEndPeriod()))) {
                    throw new IllegalArgumentException("回填失败周期超出任务范围");
                }
            }
        }
        if (new HashSet<>(failures).size() != failures.size()) {
            throw new IllegalArgumentException("回填失败周期重复");
        }
        return new ArrayList<>(failures);
    }

    private void validateClaimedTask(UserMusicSummaryBackfillTask task) {
        if (task.getSummaryType() == null || task.getStartPeriod() == null
                || task.getEndPeriod() == null || task.getCursorPeriod() == null
                || task.getTotalPeriods() == null || task.getCompletedPeriods() == null
                || task.getAffectedRows() == null || task.getAttemptCount() == null
                || task.getMaxAttempts() == null) {
            throw new IllegalArgumentException("回填任务字段不完整");
        }
        int expectedTotal;
        int expectedCompleted;
        if (DAILY.equals(task.getSummaryType())) {
            LocalDate start = LocalDate.parse(task.getStartPeriod());
            LocalDate end = LocalDate.parse(task.getEndPeriod());
            LocalDate cursor = LocalDate.parse(task.getCursorPeriod());
            validateRange(start, end, LocalDate.now().minusDays(1), MAX_DAILY_PERIODS, "日");
            expectedTotal = Math.toIntExact(ChronoUnit.DAYS.between(start, end) + 1);
            if (cursor.isBefore(start) || cursor.isAfter(end.plusDays(1))) {
                throw new IllegalArgumentException("日回填游标超出任务范围");
            }
            expectedCompleted = Math.toIntExact(ChronoUnit.DAYS.between(start, cursor));
        } else if (MONTHLY.equals(task.getSummaryType())) {
            YearMonth start = YearMonth.parse(task.getStartPeriod());
            YearMonth end = YearMonth.parse(task.getEndPeriod());
            YearMonth cursor = YearMonth.parse(task.getCursorPeriod());
            if (end.isAfter(YearMonth.now()) || end.isBefore(start)) {
                throw new IllegalArgumentException("月回填范围无效");
            }
            expectedTotal = Math.toIntExact(ChronoUnit.MONTHS.between(start, end) + 1);
            if (expectedTotal > MAX_MONTHLY_PERIODS
                    || cursor.isBefore(start) || cursor.isAfter(end.plusMonths(1))) {
                throw new IllegalArgumentException("月回填游标或范围无效");
            }
            expectedCompleted = Math.toIntExact(ChronoUnit.MONTHS.between(start, cursor));
        } else {
            throw new IllegalArgumentException("回填任务类型无效");
        }
        String expectedBusinessKey = task.getSummaryType() + ":"
                + task.getStartPeriod() + ":" + task.getEndPeriod();
        if (!expectedBusinessKey.equals(task.getBusinessKey())
                || task.getTotalPeriods() != expectedTotal
                || task.getCompletedPeriods() != expectedCompleted
                || task.getAffectedRows() < 0 || task.getAttemptCount() <= 0
                || task.getMaxAttempts() <= 0 || task.getAttemptCount() > task.getMaxAttempts()) {
            throw new IllegalArgumentException("回填任务状态不一致");
        }
    }

    private Map<String, Object> toMap(UserMusicSummaryBackfillTask task) {
        if (task == null) {
            return null;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("taskId", task.getTaskId());
        result.put("businessKey", task.getBusinessKey());
        result.put("operatorId", task.getOperatorId());
        result.put("summaryType", task.getSummaryType());
        result.put("startPeriod", task.getStartPeriod());
        result.put("endPeriod", task.getEndPeriod());
        result.put("cursorPeriod", task.getCursorPeriod());
        result.put("totalPeriods", task.getTotalPeriods());
        result.put("completedPeriods", task.getCompletedPeriods());
        result.put("affectedRows", task.getAffectedRows());
        result.put("failedPeriods", readFailuresForResponse(task));
        result.put("status", task.getStatus());
        result.put("attemptCount", task.getAttemptCount());
        result.put("maxAttempts", task.getMaxAttempts());
        result.put("errorMessage", task.getErrorMessage());
        result.put("nextRetryTime", task.getNextRetryTime());
        result.put("startedAt", task.getStartedAt());
        result.put("workerId", task.getWorkerId());
        result.put("leaseUntil", task.getLeaseUntil());
        result.put("completedAt", task.getCompletedAt());
        result.put("createdAt", task.getCreateTime());
        result.put("updatedAt", task.getUpdateTime());
        return result;
    }

    private int safeInt(Integer value) {
        return value == null || value < 0 ? 0 : value;
    }

    private int safeInt(Integer value, int fallback) {
        return value == null || value < 0 ? fallback : value;
    }

}
