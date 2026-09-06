package com.haoran.music.task;

import com.haoran.music.service.search.SearchIndexService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;






@Slf4j
@Component
public class SearchIndexRebuildTask {

    @Resource
    private SearchIndexService searchIndexService;

    @Value("${search.elasticsearch.enabled:false}")
    private boolean elasticsearchEnabled;

    @Value("${search.elasticsearch.rebuild-enabled:false}")
    private boolean rebuildEnabled;

    @Value("${search.engine:mysql}")
    private String searchEngine;

    @Value("${search.elasticsearch.bootstrap-rebuild-enabled:true}")
    private boolean bootstrapRebuildEnabled;

    @Value("${search.elasticsearch.bootstrap-min-documents:2}")
    private long bootstrapMinDocuments;





    @EventListener(ApplicationReadyEvent.class)
    public void bootstrapIndexIfNeeded() {
        if (!isElasticsearchSelected() || !bootstrapRebuildEnabled) {
            return;
        }
        try {
            long documentCount = searchIndexService.documentCount();
            if (documentCount >= 0 && documentCount < Math.max(1L, bootstrapMinDocuments)) {
                Map<String, Object> result = searchIndexService.rebuildAll();
                log.info("event=search_index_bootstrap_completed previousDocumentCount={} resourceTypeCount={}",
                        documentCount, result == null ? 0 : result.size());
            }
        } catch (Exception e) {
            log.error("event=search_index_bootstrap_failed errorType={}",
                    e.getClass().getSimpleName());
        }
    }

    @Scheduled(cron = "${search.elasticsearch.rebuild-cron:0 0 3 * * ?}")
    public void rebuildIndex() {
        if (!isElasticsearchSelected() || !rebuildEnabled) {
            return;
        }
        try {
            Map<String, Object> result = searchIndexService.rebuildAll();
            log.info("event=scheduled_search_index_rebuild_completed resourceTypeCount={}",
                    result == null ? 0 : result.size());
        } catch (Exception e) {
            log.error("event=scheduled_search_index_rebuild_failed errorType={}",
                    e.getClass().getSimpleName());
        }
    }

    private boolean isElasticsearchSelected() {
        return elasticsearchEnabled && "elasticsearch".equalsIgnoreCase(searchEngine);
    }
}
