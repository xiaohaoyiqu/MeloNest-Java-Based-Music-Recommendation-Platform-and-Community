   
                      
   
package com.haoran.music.task;

import com.haoran.music.service.MusicReportRefreshTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

   
                              
   
@Slf4j
@Component
public class MusicReportRefreshRetryTask {

    @Resource
    private MusicReportRefreshTaskService taskService;

    @Scheduled(cron = "${schedule.task.music-intelligence.report-refresh-retry-cron:0 */5 * * * ?}")
    public void retryDueTasks() {
        try {
            int submitted = taskService.retryDueTasks(20);
            if (submitted > 0) {
                log.info("event=music_report_refresh_retry_batch_submitted count={}", submitted);
            }
        } catch (Exception e) {
            log.warn("event=music_report_refresh_retry_scan_failed errorType={}",
                    e.getClass().getSimpleName());
        }
    }
}
