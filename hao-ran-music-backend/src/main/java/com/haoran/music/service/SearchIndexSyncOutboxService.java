package com.haoran.music.service;

import java.util.List;
import java.util.Map;






public interface SearchIndexSyncOutboxService {

    String record(String resourceType, Long resourceId);

    boolean dispatchEvent(String eventId);

    int retryDueEvents(int limit);

    boolean retryFailedEvent(String eventId);

    Map<String, Object> getStatusSummary();

    List<Map<String, Object>> getRecentFailures(int limit);
}
