package com.haoran.music.task;

import com.haoran.music.service.SearchIndexSyncOutboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;






@Slf4j
@Component
public class SearchIndexSyncOutboxRetryTask {

    private final SearchIndexSyncOutboxService outboxService;

    @Value("${search.sync-outbox.retry-batch-size:50}")
    private int retryBatchSize;

    public SearchIndexSyncOutboxRetryTask(SearchIndexSyncOutboxService outboxService) {
        this.outboxService = outboxService;
    }

    @Scheduled(fixedDelayString = "${search.sync-outbox.retry-delay-ms:15000}")
    public void retryDueEvents() {
        try {
            int completed = outboxService.retryDueEvents(retryBatchSize);
            if (completed > 0) {
                log.info("event=search_index_sync_retry_completed completedCount={}", completed);
            }
        } catch (Exception exception) {
            log.error("event=search_index_sync_retry_failed errorType={}",
                    exception.getClass().getSimpleName());
        }
    }
}
