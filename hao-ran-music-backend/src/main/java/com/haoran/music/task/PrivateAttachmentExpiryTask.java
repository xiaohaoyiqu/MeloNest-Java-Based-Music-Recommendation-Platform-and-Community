package com.haoran.music.task;

import com.haoran.music.service.PrivateAttachmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;






@Slf4j
@Component
@RequiredArgsConstructor
public class PrivateAttachmentExpiryTask {

    private final PrivateAttachmentService privateAttachmentService;

    @Value("${schedule.task.private-attachment-expiry-limit:500}")
    private int expiryLimit;




    @Scheduled(cron = "${schedule.task.private-attachment-expiry-cron:0 */10 * * * ?}")
    public void expireSessions() {
        int expired = privateAttachmentService.expireSessions(expiryLimit);
        if (expired > 0) {
            log.info("Private attachment sessions expired: count={}", expired);
        }
    }
}
