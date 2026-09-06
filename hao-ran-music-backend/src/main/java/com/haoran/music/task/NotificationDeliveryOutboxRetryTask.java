package com.haoran.music.task;

import com.haoran.music.service.NotificationDeliveryOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;






@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDeliveryOutboxRetryTask {

    private final NotificationDeliveryOutboxService outboxService;

    @Value("${schedule.task.notification-delivery-outbox-limit:50}")
    private int limit;




    @Scheduled(cron = "${schedule.task.notification-delivery-outbox-cron:0 */1 * * * ?}")
    public void retryDueEvents() {
        try {
            int delivered = outboxService.retryDueEvents(limit);
            if (delivered > 0) {
                log.info("event=notification_delivery_batch_completed count={}", delivered);
            }
        } catch (Exception exception) {
            log.warn("event=notification_delivery_scan_failed category=SCAN_ERROR");
        }
    }
}
