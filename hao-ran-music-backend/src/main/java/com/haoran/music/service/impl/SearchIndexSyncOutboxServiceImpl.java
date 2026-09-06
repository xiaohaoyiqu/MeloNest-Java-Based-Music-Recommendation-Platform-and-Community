package com.haoran.music.service.impl;

import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.entity.SearchIndexSyncOutboxEvent;
import com.haoran.music.mapper.SearchIndexSyncOutboxMapper;
import com.haoran.music.service.SearchIndexSyncOutboxService;
import com.haoran.music.service.search.SearchIndexService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

   
                                                
  
                      
   
@Slf4j
@Service
public class SearchIndexSyncOutboxServiceImpl implements SearchIndexSyncOutboxService {

    private static final int MAX_ATTEMPTS = 8;
    private static final int MAX_BATCH_SIZE = 100;
    private static final int LEASE_SECONDS = 60;
    private static final Set<String> SUPPORTED_TYPES = new HashSet<>(Arrays.asList(
            "song", "album", "artist", "playlist", "mv", "user"));

    private final SearchIndexSyncOutboxMapper outboxMapper;
    private final SearchIndexService searchIndexService;

    public SearchIndexSyncOutboxServiceImpl(SearchIndexSyncOutboxMapper outboxMapper,
                                             @Lazy SearchIndexService searchIndexService) {
        this.outboxMapper = outboxMapper;
        this.searchIndexService = searchIndexService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String record(String resourceType, Long resourceId) {
        String normalizedType = normalizeType(resourceType);
        if (resourceId == null || resourceId <= 0) {
            throw new BusinessException("搜索索引同步事件缺少有效资源ID");
        }
        SearchIndexSyncOutboxEvent event = new SearchIndexSyncOutboxEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setResourceType(normalizedType);
        event.setResourceId(resourceId);
        event.setMaxAttempts(MAX_ATTEMPTS);
        if (outboxMapper.insertEvent(event) != 1) {
            throw new BusinessException("搜索索引同步事件写入失败");
        }
        return event.getEventId();
    }

    @Override
    public boolean dispatchEvent(String eventId) {
        if (!isUuid(eventId)) {
            return false;
        }
        String workerId = UUID.randomUUID().toString();
        if (outboxMapper.claimEvent(eventId, workerId, LEASE_SECONDS) != 1) {
            return false;
        }
        SearchIndexSyncOutboxEvent event = outboxMapper.selectClaimedEvent(eventId, workerId);
        if (ObjectUtils.isEmpty(event)) {
            log.warn("event=search_index_sync_lease_lost eventId={} stage=load", eventId);
            return false;
        }
        try {
            String resourceType = normalizeType(event.getResourceType());
            if (event.getResourceId() == null || event.getResourceId() <= 0) {
                throw new BusinessException("搜索索引同步事件资源ID无效");
            }
            searchIndexService.applyOutboxSync(resourceType, event.getResourceId());
            if (outboxMapper.markSuccess(eventId, workerId) != 1) {
                log.warn("event=search_index_sync_lease_lost eventId={} stage=mark_success", eventId);
                return false;
            }
            log.info("event=search_index_sync_succeeded eventId={} resourceType={} resourceId={} attempt={}",
                    eventId, resourceType, event.getResourceId(), safeAttempt(event));
            return true;
        } catch (Exception exception) {
            int attempt = safeAttempt(event);
            if (exception instanceof BusinessException) {
                int marked = outboxMapper.markTerminalFailed(
                        eventId, workerId, "INVARIANT_VIOLATION");
                log.warn("event=search_index_sync_terminal_failed eventId={} resourceType={} "
                                + "resourceId={} stateMarked={}",
                        eventId, event.getResourceType(), event.getResourceId(), marked == 1);
                return false;
            }
            Integer retryDelay = attempt < safeMaxAttempts(event)
                    ? Math.toIntExact(retryDelaySeconds(attempt)) : null;
            int marked = outboxMapper.markFailed(eventId, workerId,
                    "DEPENDENCY_ERROR", retryDelay);
            if (marked != 1) {
                log.warn("event=search_index_sync_lease_lost eventId={} stage=mark_failed", eventId);
                return false;
            }
            log.warn("event=search_index_sync_failed eventId={} resourceType={} resourceId={} "
                            + "attempt={} errorType={}",
                    eventId, event.getResourceType(), event.getResourceId(), attempt,
                    exception.getClass().getSimpleName());
            return false;
        }
    }

    @Override
    public int retryDueEvents(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_BATCH_SIZE));
        List<String> eventIds = outboxMapper.selectDueEventIds(safeLimit);
        if (ObjectUtils.isEmpty(eventIds)) {
            return 0;
        }
        int completed = 0;
        for (String eventId : eventIds) {
            if (dispatchEvent(eventId)) {
                completed++;
            }
        }
        return completed;
    }

    @Override
    public boolean retryFailedEvent(String eventId) {
        if (!isUuid(eventId) || outboxMapper.requeueFailed(eventId) != 1) {
            return false;
        }
        log.info("event=search_index_sync_requeued eventId={}", eventId);
        return dispatchEvent(eventId);
    }

    @Override
    public Map<String, Object> getStatusSummary() {
        Map<String, Object> result = new HashMap<>();
        result.put("statuses", outboxMapper.selectStatusSummary());
        result.put("maxAttempts", MAX_ATTEMPTS);
        result.put("maxBatchSize", MAX_BATCH_SIZE);
        result.put("leaseSeconds", LEASE_SECONDS);
        return result;
    }

    @Override
    public List<Map<String, Object>> getRecentFailures(int limit) {
        return outboxMapper.selectRecentFailures(Math.max(1, Math.min(limit, MAX_BATCH_SIZE)));
    }

    private String normalizeType(String resourceType) {
        String normalized = resourceType == null
                ? "" : resourceType.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_TYPES.contains(normalized)) {
            throw new BusinessException("不支持的搜索索引资源类型");
        }
        return normalized;
    }

    private boolean isUuid(String value) {
        if (ObjectUtils.isEmpty(value)) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private int safeAttempt(SearchIndexSyncOutboxEvent event) {
        return event.getAttemptCount() == null ? 1 : Math.max(1, event.getAttemptCount());
    }

    private int safeMaxAttempts(SearchIndexSyncOutboxEvent event) {
        return event.getMaxAttempts() == null ? MAX_ATTEMPTS : event.getMaxAttempts();
    }

    private long retryDelaySeconds(int attempt) {
        return Math.min(900L, 15L * (1L << Math.min(6, Math.max(0, attempt - 1))));
    }

}
