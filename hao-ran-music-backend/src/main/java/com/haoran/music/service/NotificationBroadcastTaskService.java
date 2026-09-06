


package com.haoran.music.service;

import java.util.Map;

public interface NotificationBroadcastTaskService {

    Map<String, Object> submit(String title, String content, String link, String coverUrl, Long operatorId);

    Map<String, Object> getTask(String taskId);

    Map<String, Object> retry(String taskId, Long operatorId);

    Map<String, Object> getTaskStatus();

    int retryDueTasks(int limit);
}
