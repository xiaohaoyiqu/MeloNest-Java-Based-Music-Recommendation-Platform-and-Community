   
                      
   
package com.haoran.music.service;

import java.util.Map;

   
                      
   
public interface MusicReportRefreshTaskService {

    Map<String, Object> refreshNow(Long userId, String reportType, Integer year);

    Map<String, Object> submit(Long userId, String reportType, Integer year, Long operatorId);

    Map<String, Object> getTask(String taskId);

    Map<String, Object> retry(String taskId, Long operatorId);

    Map<String, Object> getTaskStatus();

    int retryDueTasks(int limit);
}
