


package com.haoran.music.task;

import com.haoran.music.service.UserMusicSummaryBackfillTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;




@Slf4j
@Component
public class UserMusicSummaryBackfillRetryTask {

    @Resource
    private UserMusicSummaryBackfillTaskService taskService;

    @Scheduled(cron = "${schedule.task.music-intelligence.summary-backfill-retry-cron:0 */5 * * * ?}")
    public void retryDueTasks() {
        try {
            int submitted = taskService.retryDueTasks(10);
            if (submitted > 0) {
                log.info("event=user_music_summary_backfill_retry_batch_submitted count={}", submitted);
            }
        } catch (Exception e) {
            log.warn("event=user_music_summary_backfill_retry_scan_failed errorType={}",
                    e.getClass().getSimpleName());
        }
    }
}
