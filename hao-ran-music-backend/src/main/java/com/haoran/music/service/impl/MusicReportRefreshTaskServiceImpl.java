


package com.haoran.music.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haoran.music.common.util.RedisUtils;
import com.haoran.music.entity.UserMusicReportRefreshTask;
import com.haoran.music.mapper.UserMusicReportRefreshTaskMapper;
import com.haoran.music.service.MusicHealthReportService;
import com.haoran.music.service.MusicReportRefreshTaskService;
import com.haoran.music.service.TimeMachineRecommendService;
import com.haoran.music.service.UserMusicReportSnapshotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Date;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.dao.DuplicateKeyException;





@Slf4j
@Service
public class MusicReportRefreshTaskServiceImpl implements MusicReportRefreshTaskService {

    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_RUNNING = "running";
    private static final String STATUS_SUCCESS = "success";
    private static final String HEALTH_REPORT = "health_report";
    private static final String YEARLY_REPORT = "yearly_report";
    private static final String YEARLY_MEMORY = "yearly_memory";
    private static final int MAX_ATTEMPTS = 3;
    private static final int DUE_TASK_LIMIT = 20;
    private static final int MIN_REPORT_YEAR = 2000;

    @Resource
    private UserMusicReportRefreshTaskMapper taskMapper;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private MusicHealthReportService musicHealthReportService;

    @Resource
    private TimeMachineRecommendService timeMachineRecommendService;

    @Resource
    private UserMusicReportSnapshotService reportSnapshotService;

    @Resource
    private RedisUtils redisUtils;

    @Resource(name = "asyncExecutor")
    private Executor asyncExecutor;

    @Resource
    private ThreadPoolTaskScheduler taskScheduler;

    @Override
    public Map<String, Object> refreshNow(Long userId, String reportType, Integer year) {
        String normalizedType = normalizeReportType(reportType);
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }

        String periodKey;
        Map<String, Object> refreshed;
        if (HEALTH_REPORT.equals(normalizedType)) {
            LocalDate today = LocalDate.now();
            periodKey = today.toString();
            deleteHealthCache(userId, today);
            int invalidated = reportSnapshotService.invalidateSnapshot(userId, normalizedType, periodKey);
            refreshed = musicHealthReportService.generateReport(userId);
            return buildRefreshResult(normalizedType, periodKey, invalidated, refreshed);
        }

        int resolvedYear = resolveYear(year);
        periodKey = String.valueOf(resolvedYear);
        int invalidated;
        if (YEARLY_REPORT.equals(normalizedType)) {
            safeDelete("audio:health:yearly:" + userId + ":" + resolvedYear);
            invalidated = reportSnapshotService.invalidateSnapshot(userId, normalizedType, periodKey);
            refreshed = musicHealthReportService.getYearlyReport(userId, resolvedYear);
        } else {
            safeDelete("audio:timemachine:yearly-memory:" + userId + ":" + resolvedYear);
            invalidated = reportSnapshotService.invalidateSnapshot(userId, normalizedType, periodKey);
            refreshed = timeMachineRecommendService.getYearlyMemory(userId, resolvedYear);
        }
        return buildRefreshResult(normalizedType, periodKey, invalidated, refreshed);
    }

    @Override
    public Map<String, Object> submit(Long userId, String reportType, Integer year, Long operatorId) {
        if (operatorId == null) {
            throw new IllegalArgumentException("请先登录");
        }
        String normalizedType = normalizeReportType(reportType);
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }

        String periodKey = resolvePeriodKey(normalizedType, year);
        String businessKey = buildBusinessKey(userId, normalizedType, periodKey);
        UserMusicReportRefreshTask existing = taskMapper.selectByBusinessKey(businessKey);
        if (existing != null) {
            return resetAndSubmitExisting(existing, operatorId);
        }

        UserMusicReportRefreshTask task = new UserMusicReportRefreshTask();
        task.setTaskId("report-" + System.currentTimeMillis() + "-"
                + UUID.randomUUID().toString().replace("-", ""));
        task.setBusinessKey(businessKey);
        task.setUserId(userId);
        task.setOperatorId(operatorId);
        task.setReportType(normalizedType);
        task.setPeriodKey(periodKey);
        task.setStatus(STATUS_PENDING);
        task.setMaxAttempts(MAX_ATTEMPTS);
        task.setDeleted(0);
        try {
            if (taskMapper.insertTask(task) != 1) {
                throw new IllegalStateException("音乐报告刷新任务写入失败");
            }
        } catch (DuplicateKeyException e) {
            UserMusicReportRefreshTask raced = taskMapper.selectByBusinessKey(businessKey);
            if (raced == null) {
                throw e;
            }
            return resetAndSubmitExisting(raced, operatorId);
        }

        submitExecution(task.getTaskId());
        return toMap(taskMapper.selectActiveByTaskId(task.getTaskId()));
    }

    private Map<String, Object> resetAndSubmitExisting(UserMusicReportRefreshTask task, Long operatorId) {
        if (!STATUS_RUNNING.equals(task.getStatus())
                && taskMapper.resetByBusinessKey(task.getBusinessKey(), operatorId, MAX_ATTEMPTS) == 1) {
            submitExecution(task.getTaskId());
        }
        return toMap(taskMapper.selectActiveByTaskId(task.getTaskId()));
    }

    @Override
    public Map<String, Object> getTask(String taskId) {
        if (taskId == null || taskId.trim().isEmpty()) {
            return null;
        }
        return toMap(taskMapper.selectActiveByTaskId(taskId.trim()));
    }

    @Override
    public Map<String, Object> getTaskStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("statuses", taskMapper.selectStatusSummary());
        result.put("maxAttempts", MAX_ATTEMPTS);
        result.put("dueLimit", DUE_TASK_LIMIT);
        return result;
    }

    @Override
    public Map<String, Object> retry(String taskId, Long operatorId) {
        if (operatorId == null) {
            throw new IllegalArgumentException("请先登录");
        }
        if (taskId == null || taskId.trim().isEmpty()) {
            throw new IllegalArgumentException("taskId 不能为空");
        }
        String normalizedTaskId = taskId.trim();
        UserMusicReportRefreshTask task = taskMapper.selectActiveByTaskId(normalizedTaskId);
        if (task == null) {
            return null;
        }
        if (STATUS_SUCCESS.equals(task.getStatus()) || STATUS_RUNNING.equals(task.getStatus())) {
            return toMap(task);
        }
        if (task.getAttemptCount() != null && task.getMaxAttempts() != null
                && task.getAttemptCount() >= task.getMaxAttempts()) {
            throw new IllegalStateException("刷新任务已达到最大重试次数");
        }
        if (taskMapper.requestRetry(normalizedTaskId, operatorId) == 1) {
            submitExecution(normalizedTaskId);
        }
        return toMap(taskMapper.selectActiveByTaskId(normalizedTaskId));
    }

    @Override
    public int retryDueTasks(int limit) {
        int safeLimit = limit <= 0 ? DUE_TASK_LIMIT : Math.min(limit, DUE_TASK_LIMIT);
        int recovered = taskMapper.recoverStaleRunningTasks();
        if (recovered > 0) {
            log.warn("event=music_report_refresh_stale_tasks_recovered count={}", recovered);
        }
        int submitted = 0;
        for (String taskId : taskMapper.selectDueTaskIds(safeLimit)) {
            if (submitExecution(taskId)) {
                submitted++;
            }
        }
        return submitted;
    }

    private boolean submitExecution(String taskId) {
        try {
            asyncExecutor.execute(() -> executeTask(taskId));
            return true;
        } catch (RejectedExecutionException e) {
            int marked = taskMapper.markSubmissionRejected(
                    taskId, "音乐报告刷新执行队列已满");
            log.warn("event=music_report_refresh_submission_rejected taskId={} stateMarked={} errorType={}",
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
                    log.warn("event=music_report_refresh_heartbeat_failed taskId={} workerId={} errorType={}",
                            taskId, workerId, ex.getClass().getSimpleName());
                }
            }, new Date(System.currentTimeMillis() + 60_000L), 60_000L);
        } catch (Exception ex) {
            int marked = taskMapper.markFailed(taskId, workerId, "音乐报告刷新租约启动失败", 1);
            log.warn("event=music_report_refresh_heartbeat_start_failed taskId={} workerId={} stateMarked={} errorType={}",
                    taskId, workerId, marked == 1, ex.getClass().getSimpleName());
            return false;
        }
        UserMusicReportRefreshTask task = taskMapper.selectActiveByTaskId(taskId);
        if (task == null) {
            heartbeat.cancel(false);
            log.warn("event=music_report_refresh_claimed_task_missing taskId={} workerId={}",
                    taskId, workerId);
            return false;
        }
        try {
            validateClaimedTask(task);
            Map<String, Object> result = refreshNow(task.getUserId(), task.getReportType(),
                    parseYear(task.getPeriodKey(), task.getReportType()));
            requireLease(leaseLost);
            Map<String, Object> summary = summarizeRefreshResult(result);
            int marked = taskMapper.markSuccess(
                    taskId, workerId, objectMapper.writeValueAsString(summary));
            if (marked != 1) {
                log.warn("event=music_report_refresh_completion_rejected taskId={} workerId={} userId={} reportType={}",
                        taskId, workerId, task.getUserId(), task.getReportType());
                return false;
            }
            log.info("event=music_report_refresh_completed taskId={} userId={} reportType={} attempt={}",
                    taskId, task.getUserId(), task.getReportType(), task.getAttemptCount());
            return true;
        } catch (IllegalArgumentException e) {
            int marked = taskMapper.markTerminalFailed(
                    taskId, workerId, "音乐报告刷新任务参数无效");
            log.warn("event=music_report_refresh_terminal_failed taskId={} userId={} reportType={} category=INVALID_TASK_ARGUMENT stateMarked={}",
                    taskId, task.getUserId(), task.getReportType(), marked == 1);
            return false;
        } catch (Exception e) {
            int attempts = task.getAttemptCount() == null ? 1 : task.getAttemptCount();
            Integer retryDelay = attempts < safeInt(task.getMaxAttempts(), MAX_ATTEMPTS)
                    ? Math.toIntExact(retryDelayMinutes(attempts)) : null;
            int marked = taskMapper.markFailed(
                    taskId, workerId, "音乐报告刷新执行失败", retryDelay);
            log.warn("event=music_report_refresh_processing_failed taskId={} userId={} reportType={} attempt={} retryDelayMinutes={} stateMarked={} errorType={}",
                    taskId, task.getUserId(), task.getReportType(), attempts, retryDelay,
                    marked == 1, e.getClass().getSimpleName());
            return false;
        } finally {
            heartbeat.cancel(false);
        }
    }

    private void requireLease(AtomicBoolean leaseLost) {
        if (leaseLost.get()) {
            throw new IllegalStateException("音乐报告刷新任务租约已失效");
        }
    }

    private Map<String, Object> buildRefreshResult(String reportType, String periodKey,
                                                   int invalidated, Map<String, Object> refreshed) {
        Map<String, Object> result = new HashMap<>();
        result.put("reportType", reportType);
        result.put("periodKey", periodKey);
        result.put("invalidatedSnapshots", invalidated);
        result.put("refreshed", refreshed);
        return result;
    }

    private Map<String, Object> summarizeRefreshResult(Map<String, Object> result) {
        Map<String, Object> summary = new HashMap<>();
        if (result == null) {
            return summary;
        }
        summary.put("reportType", result.get("reportType"));
        summary.put("periodKey", result.get("periodKey"));
        summary.put("invalidatedSnapshots", result.get("invalidatedSnapshots"));
        Object refreshed = result.get("refreshed");
        if (refreshed instanceof Map) {
            Map<?, ?> refreshedMap = (Map<?, ?>) refreshed;
            summary.put("generatedAt", refreshedMap.get("generatedAt"));
            summary.put("dataUntil", refreshedMap.get("dataUntil"));
            summary.put("calculationVersion", refreshedMap.get("calculationVersion"));
            Object snapshot = refreshedMap.get("snapshot");
            if (snapshot instanceof Map) {
                Map<?, ?> snapshotMap = (Map<?, ?>) snapshot;
                Map<String, Object> snapshotSummary = new HashMap<>();
                snapshotSummary.put("status", snapshotMap.get("status"));
                snapshotSummary.put("stale", snapshotMap.get("stale"));
                snapshotSummary.put("ageSeconds", snapshotMap.get("ageSeconds"));
                snapshotSummary.put("dataLagSeconds", snapshotMap.get("dataLagSeconds"));
                snapshotSummary.put("generationDurationMs", snapshotMap.get("generationDurationMs"));
                snapshotSummary.put("coverage", snapshotMap.get("coverage"));
                summary.put("snapshot", snapshotSummary);
            }
        }
        return summary;
    }

    private Map<String, Object> toMap(UserMusicReportRefreshTask task) {
        if (task == null) {
            return null;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("taskId", task.getTaskId());
        result.put("businessKey", task.getBusinessKey());
        result.put("userId", task.getUserId());
        result.put("operatorId", task.getOperatorId());
        result.put("reportType", task.getReportType());
        result.put("periodKey", task.getPeriodKey());
        result.put("status", task.getStatus());
        result.put("attemptCount", task.getAttemptCount());
        result.put("maxAttempts", task.getMaxAttempts());
        result.put("errorMessage", task.getErrorMessage());
        result.put("nextRetryTime", task.getNextRetryTime());
        result.put("startedAt", task.getStartedAt());
        result.put("workerId", task.getWorkerId());
        result.put("leaseUntil", task.getLeaseUntil());
        result.put("completedAt", task.getCompletedAt());
        result.put("durationMs", task.getStartedAt() == null || task.getCompletedAt() == null
                ? null : Math.max(0L, Duration.between(task.getStartedAt(), task.getCompletedAt()).toMillis()));
        result.put("createdAt", task.getCreateTime());
        result.put("updatedAt", task.getUpdateTime());
        if (task.getResultJson() != null && !task.getResultJson().trim().isEmpty()) {
            try {
                result.put("result", objectMapper.readValue(task.getResultJson(),
                        new TypeReference<Map<String, Object>>() {
                        }));
            } catch (Exception e) {
                result.put("resultUnavailable", true);
                log.warn("event=music_report_refresh_result_parse_failed taskId={} errorType={}",
                        task.getTaskId(), e.getClass().getSimpleName());
            }
        }
        return result;
    }

    private String normalizeReportType(String reportType) {
        String normalized = reportType == null ? "" : reportType.trim();
        if (!HEALTH_REPORT.equals(normalized) && !YEARLY_REPORT.equals(normalized)
                && !YEARLY_MEMORY.equals(normalized)) {
            throw new IllegalArgumentException("reportType 只支持 health_report、yearly_report、yearly_memory");
        }
        return normalized;
    }

    private String resolvePeriodKey(String reportType, Integer year) {
        return HEALTH_REPORT.equals(reportType) ? LocalDate.now().toString() : String.valueOf(resolveYear(year));
    }

    private String buildBusinessKey(Long userId, String reportType, String periodKey) {
        return userId + ":" + reportType + ":" + periodKey;
    }

    private Integer parseYear(String periodKey, String reportType) {
        if (HEALTH_REPORT.equals(reportType)) {
            return null;
        }
        try {
            return Integer.valueOf(periodKey);
        } catch (Exception e) {
            throw new IllegalArgumentException("报告任务周期格式无效");
        }
    }

    private void validateClaimedTask(UserMusicReportRefreshTask task) {
        String reportType = normalizeReportType(task.getReportType());
        if (task.getUserId() == null || task.getPeriodKey() == null) {
            throw new IllegalArgumentException("报告任务缺少用户或周期");
        }
        if (HEALTH_REPORT.equals(reportType)) {
            try {
                LocalDate.parse(task.getPeriodKey());
            } catch (Exception e) {
                throw new IllegalArgumentException("报告任务周期格式无效");
            }
        } else {
            resolveYear(parseYear(task.getPeriodKey(), reportType));
        }
        String expectedBusinessKey = buildBusinessKey(task.getUserId(), reportType, task.getPeriodKey());
        if (!expectedBusinessKey.equals(task.getBusinessKey())) {
            throw new IllegalArgumentException("报告任务业务键不一致");
        }
    }

    private int resolveYear(Integer year) {
        int resolved = year == null ? LocalDate.now().getYear() - 1 : year;
        if (resolved < MIN_REPORT_YEAR || resolved > LocalDate.now().getYear()) {
            throw new IllegalArgumentException("报告年份必须在2000到当前年份之间");
        }
        return resolved;
    }

    private int safeInt(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private long retryDelayMinutes(int attempts) {
        return Math.min(60L, 5L * Math.max(1, attempts));
    }

    private void deleteHealthCache(Long userId, LocalDate today) {
        safeDelete("audio:health:report:" + userId + ":" + today);
        safeDelete("audio:health:summary:" + userId + ":" + today);
        safeDelete("audio:health:exploration-score:" + userId + ":" + today);
        safeDelete("audio:health:time-distribution:" + userId + ":" + today);
        safeDelete("audio:health:fingerprint:" + userId + ":" + today);
        safeDelete("audio:health:yearly:" + userId + ":" + (today.getYear() - 1));
    }

    private void safeDelete(String key) {
        try {
            redisUtils.delete(key);
        } catch (Exception e) {
            log.debug("event=music_report_refresh_cache_delete_failed cacheKey={} errorType={}",
                    key, e.getClass().getSimpleName());
        }
    }
}
