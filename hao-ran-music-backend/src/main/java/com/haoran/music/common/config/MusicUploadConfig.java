package com.haoran.music.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

   
                                                                                    
  
                      
   
@Data
@Component
@ConfigurationProperties(prefix = "music.upload")
public class MusicUploadConfig {

    private long imageMaxFileSize = 5L * 1024 * 1024;
    private int imageMaxWidth = 4096;
    private int imageMaxHeight = 4096;
    private List<String> allowedImageTypes = new ArrayList<>(Arrays.asList(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp"
    ));
    private List<String> allowedImageExtensions = new ArrayList<>(Arrays.asList(
            "jpg", "jpeg", "png", "gif", "webp"
    ));

    private boolean compressEnabled = true;
    private long compressThreshold = 50L * 1024;
    private boolean virusScanEnabled = true;

    private String privateAttachmentPath = System.getProperty("java.io.tmpdir")
            + "/haoran-private-attachments";
    private int privateAttachmentSessionMinutes = 30;
    private int privateAttachmentReclaimGraceHours = 24;

    private int maxFiles = 20;
    private long maxTotalSize = 500L * 1024 * 1024;
    private long singleFileMaxSize = 500L * 1024 * 1024;
    private long archiveMaxFileSize = 500L * 1024 * 1024;
    private int submissionDailyMaxFilesPerUser = 100;
    private long submissionDailyMaxBytesPerUser = 1024L * 1024 * 1024;
    private int submissionMaxConcurrentUploads = 2;
    private int submissionProcessingMinutes = 30;

    private long emojiMaxFileSize = 5L * 1024 * 1024;
    private int emojiMaxFiles = 20;
    private int emojiMaxWidth = 4096;
    private int emojiMaxHeight = 4096;
    private boolean emojiCompressEnabled = true;
    private long emojiCompressThreshold = 100L * 1024;
    private boolean emojiVirusScanEnabled = true;
    private int emojiRetentionDays = 30;
    private int emojiTempRetentionHours = 24;
    private int emojiMaxEditablePackagesPerUser = 5;
    private int emojiMaxPendingPackagesPerUser = 2;
    private int emojiDailyMaxFilesPerUser = 80;
    private long emojiDailyMaxBytesPerUser = 200L * 1024 * 1024;
    private int emojiMaxProcessingBatchesPerUser = 1;
    private int emojiMaxConcurrentUploads = 2;
    private int emojiBatchProcessingMinutes = 30;

       
                                                                      
       
    public boolean isAllowedImageType(String contentType) {
        if (contentType == null) {
            return false;
        }
        for (String allowedType : allowedImageTypes) {
            if (contentType.equalsIgnoreCase(allowedType)) {
                return true;
            }
        }
        return false;
    }
}
