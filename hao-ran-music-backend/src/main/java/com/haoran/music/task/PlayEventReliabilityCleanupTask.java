   
                      
   
package com.haoran.music.task;

import com.haoran.music.mapper.PlayEventDeadLetterMapper;
import com.haoran.music.mapper.PlayEventReceiptMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

   
                   
   
@Slf4j
@Component
public class PlayEventReliabilityCleanupTask {

    private static final int BATCH_LIMIT = 1000;
    private static final int RECEIPT_RETENTION_DAYS = 7;
    private static final int STALE_PROCESSING_RETENTION_DAYS = 3;
    private static final int DEAD_LETTER_RESOLVED_RETENTION_DAYS = 90;

    @Resource
    private PlayEventReceiptMapper receiptMapper;
    @Resource
    private PlayEventDeadLetterMapper deadLetterMapper;

    @Scheduled(cron = "${schedule.task.play-event-reliability-cleanup-cron:0 25 3 * * ?}")
    public void cleanup() {
        try {
            int receipts = receiptMapper.deleteOldProcessed(RECEIPT_RETENTION_DAYS, BATCH_LIMIT);
            int staleReceipts = receiptMapper.deleteStaleProcessing(
                    STALE_PROCESSING_RETENTION_DAYS, BATCH_LIMIT);
            int deadLetters = deadLetterMapper.deleteOldResolved(
                    DEAD_LETTER_RESOLVED_RETENTION_DAYS, BATCH_LIMIT);
            if (receipts > 0 || staleReceipts > 0 || deadLetters > 0) {
                log.info("播放事件可靠性表清理完成: receipts={}, staleReceipts={}, resolvedDeadLetters={}",
                        receipts, staleReceipts, deadLetters);
            }
        } catch (Exception e) {
            log.warn("event=play_event_reliability_cleanup_failed errorType={}",
                    e.getClass().getSimpleName());
        }
    }
}
