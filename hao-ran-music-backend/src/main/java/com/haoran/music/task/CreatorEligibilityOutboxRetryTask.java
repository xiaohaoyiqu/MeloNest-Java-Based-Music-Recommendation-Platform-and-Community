   
                      
   
package com.haoran.music.task;

import com.haoran.music.service.CreatorEligibilityOutboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

   
                          
   
@Slf4j
@Component
public class CreatorEligibilityOutboxRetryTask {

    @Resource
    private CreatorEligibilityOutboxService outboxService;

    @Scheduled(cron = "${schedule.task.creator-eligibility-outbox-retry-cron:15 * * * * ?}")
    public void retryDueEvents() {
        try {
            int claimed = outboxService.retryDueEvents(20);
            if (claimed > 0) {
                log.info("event=creator_eligibility_outbox_retry_batch_completed claimed={}", claimed);
            }
        } catch (Exception e) {
            log.warn("event=creator_eligibility_outbox_retry_scan_failed errorType={}",
                    e.getClass().getSimpleName());
        }
    }
}
