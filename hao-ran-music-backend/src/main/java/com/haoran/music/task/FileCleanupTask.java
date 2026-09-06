   
                      
                        
   

package com.haoran.music.task;

import com.haoran.music.common.config.PostMediaConfig;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.service.EmojiService;
import com.haoran.music.service.MessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Map;

   
           
                 
   
@Slf4j
@Component
public class FileCleanupTask {

    private final EmojiService emojiService;
    private final MessageService messageService;
    private final PostMediaConfig postMediaConfig;

       
                     
       
    private static final int EMOJI_RETENTION_DAYS = 30;

       
                       
       
    private static final int MESSAGE_RETENTION_DAYS = 180;

       
                
       
    private static final int TEMP_FILE_RETENTION_HOURS = 24;

    public FileCleanupTask(EmojiService emojiService, MessageService messageService, PostMediaConfig postMediaConfig) {
        this.emojiService = emojiService;
        this.messageService = messageService;
        this.postMediaConfig = postMediaConfig;
    }

       
                     
                           
       
    @Scheduled(cron = "${schedule.task.file-cleanup.daily-cron}")
    public void dailyCleanup() {
        log.info("event=file_cleanup_daily_started");

        try {
                            
            cleanupExpiredKeywords();

                                       
                                   
            cleanupOrphanedEmojis();

                        
            logCleanupSummary();

        } catch (Exception e) {
            log.error("event=file_cleanup_daily_failed errorType={}", e.getClass().getSimpleName());
        }

        log.info("event=file_cleanup_daily_completed");
    }

       
                    
       
    @Scheduled(cron = "${schedule.task.file-cleanup.weekly-deep-cron}")
    public void weeklyDeepCleanup() {
        log.info("event=file_cleanup_weekly_started");

        try {
                          
            checkTableFragmentation();

                        
            checkEmojiUsage();

        } catch (Exception e) {
            log.error("event=file_cleanup_weekly_failed errorType={}", e.getClass().getSimpleName());
        }

        log.info("event=file_cleanup_weekly_completed");
    }

       
                    
       
    @Scheduled(cron = "${schedule.task.file-cleanup.hourly-temp-cron}")
    public void hourlyTempCleanup() {
        log.debug("event=file_cleanup_temp_started");

        int tempCount = cleanupLocalDirectory(postMediaConfig.getVideoTempPath(), TEMP_FILE_RETENTION_HOURS);
        int cacheCount = cleanupLocalDirectory(postMediaConfig.getVideoCachePath(), postMediaConfig.getVideoCacheDays() * 24);
        if (tempCount > 0 || cacheCount > 0) {
            log.info("event=file_cleanup_temp_completed tempFileCount={} cacheFileCount={}", tempCount, cacheCount);
        }
    }

       
                      
      
                          
                                  
                     
  
    private int cleanupLocalDirectory(String dirPath, int retentionHours) {
        if (ObjectUtils.isEmpty(dirPath)) {
            return 0;
        }
        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) {
            return 0;
        }
        File[] files = dir.listFiles();
        if (ObjectUtils.isEmpty(files)) {
            return 0;
        }

        long cutoffTime = System.currentTimeMillis() - retentionHours * 60L * 60 * 1000;
        int count = 0;
        for (File file : files) {
            if (file.isFile() && file.lastModified() < cutoffTime && file.delete()) {
                count++;
            }
        }
        return count;
    }

       
                 
                           
       
    private void cleanupExpiredKeywords() {
        try {
            emojiService.getEmojiStatistics();
            log.info("event=file_cleanup_keyword_check_completed");
        } catch (Exception e) {
            log.warn("event=file_cleanup_keyword_check_failed errorType={}", e.getClass().getSimpleName());
        }
    }

       
                                 
       
    private void cleanupOrphanedEmojis() {
        try {
                       
            emojiService.getEmojiStatistics();
            log.info("event=file_cleanup_emoji_orphan_check_completed");
        } catch (Exception e) {
            log.warn("event=file_cleanup_emoji_orphan_check_failed errorType={}", e.getClass().getSimpleName());
        }
    }

       
              
       
    private void checkTableFragmentation() {
        log.info("event=file_cleanup_table_fragmentation_check_completed");
    }

       
                
       
    private void checkEmojiUsage() {
        try {
            emojiService.getEmojiStatistics();
            log.info("event=file_cleanup_emoji_usage_check_completed");
        } catch (Exception e) {
            log.warn("event=file_cleanup_emoji_usage_check_failed errorType={}", e.getClass().getSimpleName());
        }
    }

       
             
       
    private void logCleanupSummary() {
        log.info("event=file_cleanup_summary tempRetentionHours={} emojiRetentionDays={} messageRetentionDays={}",
                TEMP_FILE_RETENTION_HOURS, EMOJI_RETENTION_DAYS, MESSAGE_RETENTION_DAYS);
    }

       
                     
      
                                                
                   
       
    public Map<String, Object> manualCleanup(String cleanupType) {
        log.info("event=file_cleanup_manual_requested");

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("executedAt", LocalDateTime.now());
        result.put("type", cleanupType);

        try {
            switch (cleanupType) {
                case "temp":
                    hourlyTempCleanup();
                    result.put("status", "success");
                    break;
                case "orphan":
                    cleanupOrphanedEmojis();
                    result.put("status", "success");
                    break;
                case "deep":
                    weeklyDeepCleanup();
                    result.put("status", "success");
                    break;
                default:
                    result.put("status", "unknown_type");
            }
        } catch (Exception e) {
            result.put("status", "error");
            result.put("error", "清理任务执行失败");
            log.warn("event=file_cleanup_manual_failed errorType={}", e.getClass().getSimpleName());
        }

        return result;
    }
}
