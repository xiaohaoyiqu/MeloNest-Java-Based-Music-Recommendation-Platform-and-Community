   
                      
   
package com.haoran.music.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

   
                      
   
public interface UserMusicSummaryBackfillTaskService {

    Map<String, Object> submitDaily(LocalDate startDate, LocalDate endDate, Long operatorId);

    Map<String, Object> submitMonthly(YearMonth startMonth, YearMonth endMonth, Long operatorId);

    Map<String, Object> getTask(String taskId);

    Map<String, Object> retry(String taskId, Long operatorId);

    Map<String, Object> getTaskStatus();

    int retryDueTasks(int limit);
}
