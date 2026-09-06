   
                            
  
                      
   
package com.haoran.music.service.impl;

import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.SecurityCheckUtil;
import com.haoran.music.entity.NotificationBroadcastTask;
import com.haoran.music.mapper.NotificationBroadcastTaskMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.NotificationBroadcastTaskService;
import com.haoran.music.service.NotificationService;
import com.haoran.music.service.PushNotificationService;
import com.haoran.music.vo.push.PushNotificationVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Slf4j
@Service
public class NotificationBroadcastTaskServiceImpl implements NotificationBroadcastTaskService {

    private static final String PENDING = "pending";
    private static final String RUNNING = "running";
    private static final String SUCCESS = "success";
    private static final String FAILED = "failed";
    private static final int MAX_ATTEMPTS = 3;
    private static final int BATCH_SIZE = 500;
    private static final String DEFAULT_ANNOUNCEMENT_LINK = "/announcements";

    @Resource
    private NotificationBroadcastTaskMapper taskMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private NotificationService notificationService;

    @Resource
    private PushNotificationService pushNotificationService;

    @Resource(name = CommonConstants.TASK_EXECUTOR)
    private Executor taskExecutor;

    @Override
    public Map<String, Object> submit(String title, String content, String link, String coverUrl, Long operatorId) {
        if (operatorId == null) {
            throw new IllegalArgumentException("请先登录");
        }
        String normalizedTitle = required(title, "标题不能为空");
        String normalizedContent = required(content, "内容不能为空");
        if (normalizedTitle.length() > 120) {
            throw new IllegalArgumentException("标题不能超过120个字符");
        }
        if (normalizedContent.length() > 2000) {
            throw new IllegalArgumentException("内容不能超过2000个字符");
        }
        if (link != null && link.trim().length() > 500) {
            throw new IllegalArgumentException("链接不能超过500个字符");
        }
        String normalizedCoverUrl = optionalPublicUrl(coverUrl, "封面地址", 1000);
        SecurityCheckUtil.CheckResult contentCheck =
                SecurityCheckUtil.comprehensiveCheck(normalizedContent, "公告内容");
        if (!contentCheck.isSafe()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, contentCheck.getMessage());
        }
        SecurityCheckUtil.CheckResult linkCheck = SecurityCheckUtil.checkUrl(link);
        if (!linkCheck.isSafe()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, linkCheck.getMessage());
        }

        NotificationBroadcastTask task = new NotificationBroadcastTask();
        task.setTaskId("notification-broadcast-" + System.currentTimeMillis() + "-"
                + UUID.randomUUID().toString().replace("-", ""));
        task.setOperatorId(operatorId);
        task.setTitle(normalizedTitle);
        task.setContent(normalizedContent);
        task.setLink(link == null ? null : link.trim());
        task.setCoverUrl(normalizedCoverUrl);
        task.setTotalUsers(userMapper.countActiveUsers());
        task.setStatus(PENDING);
        task.setMaxAttempts(MAX_ATTEMPTS);
        task.setDeleted(CommonConstants.NOT_DELETED);
        if (taskMapper.insertTask(task) != 1) {
            throw new BusinessException("通知广播任务创建失败");
        }
        submitExecution(task.getTaskId());
        return getTask(task.getTaskId());
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
        if (operatorId == null) {
            throw new IllegalArgumentException("请先登录");
        }
        NotificationBroadcastTask task = taskMapper.selectActiveByTaskId(taskId);
        if (task == null) {
            return null;
        }
        if (SUCCESS.equals(task.getStatus()) || RUNNING.equals(task.getStatus())) {
            return toMap(task);
        }
        if (safeInt(task.getAttemptCount()) >= safeInt(task.getMaxAttempts(), MAX_ATTEMPTS)) {
            throw new IllegalStateException("广播任务已达到最大重试次数");
        }
        if (taskMapper.requestRetry(taskId, operatorId) != 1) {
            throw new IllegalStateException("通知广播任务状态已变化");
        }
        submitExecution(taskId);
        return getTask(taskId);
    }

    @Override
    public Map<String, Object> getTaskStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("statuses", taskMapper.selectStatusSummary());
        result.put("maxAttempts", MAX_ATTEMPTS);
        result.put("batchSize", BATCH_SIZE);
        return result;
    }

    @Override
    public int retryDueTasks(int limit) {
        int safeLimit = limit <= 0 ? 5 : Math.min(limit, 5);
        int recovered = taskMapper.recoverStaleRunningTasks();
        if (recovered > 0) {
            log.warn("event=notification_broadcast_stale_tasks_recovered count={}", recovered);
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
            taskExecutor.execute(() -> executeTask(taskId));
            return true;
        } catch (RejectedExecutionException e) {
            int marked = taskMapper.markSubmissionRejected(taskId, "异步执行队列已满，任务将在稍后自动重试");
            log.warn("event=notification_broadcast_submission_rejected taskId={} stateMarked={} errorType={}",
                    taskId, marked == 1, e.getClass().getSimpleName());
            return false;
        }
    }

       
                                                 
       
    private void executeTask(String taskId) {
        if (taskMapper.claimTask(taskId) <= 0) {
            return;
        }
        NotificationBroadcastTask task = taskMapper.selectActiveByTaskId(taskId);
        if (task == null) {
            log.warn("event=notification_broadcast_claimed_task_missing taskId={}", taskId);
            return;
        }

        Long cursor = task.getCursorUserId() == null ? 0L : task.getCursorUserId();
        int successCount = safeInt(task.getSuccessCount());
        int failCount = safeInt(task.getFailCount());
        int batchCount = safeInt(task.getBatchCount());
        try {
            while (true) {
                List<Long> userIds = userMapper.selectActiveUserIdsAfter(cursor, BATCH_SIZE);
                if (userIds == null || userIds.isEmpty()) {
                    completeTask(task);
                    return;
                }
                batchCount++;
                String notificationLink = announcementLink(task.getLink());
                for (Long userId : userIds) {
                    notificationService.sendSystemNotificationOnce(
                            userId, task.getTitle(), task.getContent(), notificationLink,
                            broadcastBusinessKey(taskId, userId));
                    cursor = userId;
                    successCount++;
                    requireTaskWrite(taskMapper.updateProgress(
                            taskId, cursor, successCount, failCount, batchCount),
                            "通知广播进度持久化失败");
                }
                if (userIds.size() < BATCH_SIZE) {
                    completeTask(task);
                    return;
                }
            }
        } catch (Exception e) {
            failCount++;
            int attempts = safeInt(task.getAttemptCount());
            Integer retryDelayMinutes = attempts < safeInt(task.getMaxAttempts(), MAX_ATTEMPTS)
                    ? Math.toIntExact(Math.min(60L, 5L * Math.max(1, attempts))) : null;
            int marked = taskMapper.markFailed(taskId, cursor, successCount, failCount, batchCount,
                    "通知广播执行失败", retryDelayMinutes);
            log.warn("event=notification_broadcast_execution_failed taskId={} cursorUserId={} stateMarked={} errorType={}",
                    taskId, cursor, marked == 1, e.getClass().getSimpleName());
        }
    }

       
                                             
                                           
       
    private void completeTask(NotificationBroadcastTask task) {
        PushNotificationVO push = new PushNotificationVO();
        push.setPushId(publicAnnouncementPushId(task.getTaskId()));
        push.setType("announcement");
        push.setTitle(task.getTitle());
        push.setDescription(task.getContent());
        push.setLink(announcementLink(task.getLink()));
        push.setCoverUrl(task.getCoverUrl());
        push.setBadge("官方公告");
        push.setPriority(0);
        push.setStartTime(task.getCreateTime());
        String pushId = pushNotificationService.createPushNotification(push);
        if (pushId == null) {
            throw new IllegalStateException("公开公告投影写入失败");
        }
        requireTaskWrite(taskMapper.markSuccess(task.getTaskId()), "通知广播完成状态写入失败");
    }

    private String publicAnnouncementPushId(String taskId) {
        int separator = taskId == null ? -1 : taskId.lastIndexOf('-');
        String suffix = separator >= 0 ? taskId.substring(separator + 1) : taskId;
        if (suffix == null || suffix.isEmpty() || suffix.length() > 54) {
            throw new IllegalStateException("通知广播任务ID无法生成公告投影ID");
        }
        return "broadcast:" + suffix;
    }

       
                                           
       
    private String announcementLink(String link) {
        return link == null || link.trim().isEmpty() ? DEFAULT_ANNOUNCEMENT_LINK : link;
    }

    private String broadcastBusinessKey(String taskId, Long userId) {
        String businessKey = "notification-broadcast:" + taskId + ":" + userId;
        if (businessKey.length() > 128) {
            throw new IllegalStateException("通知广播幂等键超过存储上限");
        }
        return businessKey;
    }

    private void requireTaskWrite(int changed, String message) {
        if (changed != 1) {
            throw new IllegalStateException(message);
        }
    }

    private Map<String, Object> toMap(NotificationBroadcastTask task) {
        if (task == null) {
            return null;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("taskId", task.getTaskId());
        result.put("operatorId", task.getOperatorId());
        result.put("title", task.getTitle());
        result.put("content", task.getContent());
        result.put("link", task.getLink());
        result.put("coverUrl", task.getCoverUrl());
        result.put("cursorUserId", task.getCursorUserId());
        result.put("totalUsers", task.getTotalUsers());
        result.put("successCount", task.getSuccessCount());
        result.put("failCount", task.getFailCount());
        result.put("batchCount", task.getBatchCount());
        result.put("status", task.getStatus());
        result.put("attemptCount", task.getAttemptCount());
        result.put("maxAttempts", task.getMaxAttempts());
        result.put("errorMessage", task.getErrorMessage());
        result.put("nextRetryTime", task.getNextRetryTime());
        result.put("startedAt", task.getStartedAt());
        result.put("completedAt", task.getCompletedAt());
        result.put("createdAt", task.getCreateTime());
        result.put("updatedAt", task.getUpdateTime());
        return result;
    }

    private String required(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String optionalPublicUrl(String value, String fieldName, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "不能超过" + maxLength + "个字符");
        }
        SecurityCheckUtil.CheckResult urlCheck = SecurityCheckUtil.checkUrl(normalized);
        if (!urlCheck.isSafe()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, urlCheck.getMessage());
        }
        boolean siteRelative = normalized.startsWith("/") && !normalized.startsWith("//");
        boolean httpUrl = normalized.matches("(?i)^https?://[^\\s]+$");
        if (!siteRelative && !httpUrl) {
            throw new IllegalArgumentException(fieldName + "仅支持站内路径或 HTTP/HTTPS 地址");
        }
        return normalized;
    }

    private int safeInt(Integer value) {
        return value == null || value < 0 ? 0 : value;
    }

    private int safeInt(Integer value, int fallback) {
        return value == null || value < 0 ? fallback : value;
    }

}
