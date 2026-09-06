   
                      
   

package com.haoran.music.task;

import com.haoran.music.service.EmojiPackageUploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EmojiUploadRecoveryTask {

    private final EmojiPackageUploadService uploadService;

    public EmojiUploadRecoveryTask(EmojiPackageUploadService uploadService) {
        this.uploadService = uploadService;
    }

    @Scheduled(fixedDelayString = "${music.upload.emoji-cleanup-delay-ms:300000}")
    public void cleanupFailedUploads() {
        int count = uploadService.cleanupRecoverableBatches();
        if (count > 0) {
            log.info("event=emoji_upload_recovery_completed cleanedBatchCount={}", count);
        }
    }
}
