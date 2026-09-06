




package com.haoran.music.service.impl;

import com.haoran.music.common.config.SecurityConfig;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.RedisUtils;
import com.haoran.music.service.ContentAccessLimitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;







@Slf4j
@Service
public class ContentAccessLimitServiceImpl implements ContentAccessLimitService {

    @Resource
    private RedisUtils redisUtils;

    @Resource
    private SecurityConfig securityConfig;

    private static final String PAID_VIEW_KEY = "paid:view:";
    private static final String PAID_DOWNLOAD_KEY = "paid:download:";
    private static final String PAID_RECENT_KEY = "paid:recent:";
    private static final String CREATOR_ACCESS_KEY = "creator:access:";
    private static final String CREATOR_BATCH_KEY = "creator:batch:";

    @Override
    public boolean canAccessPaidContent(Long userId, String resourceType, Long resourceId) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(resourceId)) {
            return false;
        }

        try {
            String key = PAID_VIEW_KEY + userId + ":" + resourceType + ":" + resourceId;
            long current = getLongValueStrict(key);
            int limit = securityConfig.getPaidContentDailyViewLimit();
            if (current >= limit) {
                log.warn("event=paid_content_daily_view_limit_reached userId={} resourceType={} resourceId={} count={} limit={}",
                        userId, resourceType, resourceId, current, limit);
                return false;
            }

            if (securityConfig.isPaidContentHardBlockEnabled()) {
                int distinct = getRecentPaidDistinctCount(userId, "view");
                int blockThreshold = securityConfig.getPaidContentDistinctBlockThreshold();
                if (distinct >= blockThreshold) {
                    log.warn("event=paid_content_cross_resource_view_limit_reached userId={} distinct={} threshold={}",
                            userId, distinct, blockThreshold);
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            log.error("event=paid_content_view_limit_check_failed userId={} resourceType={} resourceId={} errorType={}",
                    userId, resourceType, resourceId, e.getClass().getSimpleName());
            return false;
        }
    }

    @Override
    public boolean canDownloadPaidContent(Long userId, String resourceType, Long resourceId) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(resourceId)) {
            return false;
        }

        try {
            String key = PAID_DOWNLOAD_KEY + userId + ":" + resourceType + ":" + resourceId;
            long current = getLongValueStrict(key);
            int limit = securityConfig.getPaidContentDownloadLimit();
            if (current >= limit) {
                log.warn("event=paid_content_download_limit_reached userId={} resourceType={} resourceId={} count={} limit={}",
                        userId, resourceType, resourceId, current, limit);
                return false;
            }

            if (securityConfig.isPaidContentHardBlockEnabled()) {
                int distinct = getRecentPaidDistinctCount(userId, "download");
                int blockThreshold = Math.max(10, securityConfig.getPaidContentDistinctBlockThreshold() / 3);
                if (distinct >= blockThreshold) {
                    log.warn("event=paid_content_cross_resource_download_limit_reached userId={} distinct={} threshold={}",
                            userId, distinct, blockThreshold);
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            log.error("event=paid_content_download_limit_check_failed userId={} resourceType={} resourceId={} errorType={}",
                    userId, resourceType, resourceId, e.getClass().getSimpleName());
            return false;
        }
    }

    @Override
    public void recordPaidContentAccess(Long userId, String resourceType, Long resourceId, String accessType) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(resourceId)) {
            return;
        }

        try {
            String key = "view".equals(accessType)
                    ? PAID_VIEW_KEY + userId + ":" + resourceType + ":" + resourceId
                    : PAID_DOWNLOAD_KEY + userId + ":" + resourceType + ":" + resourceId;

            Long count = redisUtils.increment(key);
            if (count != null && count == 1) {
                redisUtils.expire(key, getSecondsUntilEndOfDay(), TimeUnit.SECONDS);
            }

            recordRecentPaidAccess(userId, resourceType, resourceId, accessType);
            int recentDistinct = getRecentPaidDistinctCount(userId, accessType);
            int warnThreshold = securityConfig.getPaidContentDistinctWarnThreshold();
            if (recentDistinct >= warnThreshold) {
                log.warn("event=paid_content_high_frequency_access_observed userId={} accessType={} distinct={} warnThreshold={}",
                        userId, accessType, recentDistinct, warnThreshold);
            }

            log.debug("event=paid_content_access_recorded userId={} resourceType={} resourceId={} accessType={} count={}",
                    userId, resourceType, resourceId, accessType, count);
        } catch (Exception e) {
            log.error("event=paid_content_access_record_failed errorType={}", e.getClass().getSimpleName());
        }
    }

    @Override
    public boolean checkCreatorBatchAccess(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return false;
        }

        try {
            int distinct = getRecentCreatorDistinctCount(userId);
            int threshold = securityConfig.getCreatorBatchAccessThresholdCount();
            boolean batch = distinct >= threshold;
            if (batch) {
                log.warn("event=creator_content_batch_access_detected userId={} distinct={} threshold={}",
                        userId, distinct, threshold);
            }
            return !batch;
        } catch (Exception e) {
            log.error("event=creator_content_batch_access_check_failed userId={} errorType={}",
                    userId, e.getClass().getSimpleName());
            return false;
        }
    }

    @Override
    public void recordCreatorContentAccess(Long userId, Long contentId, String contentType) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(contentId)) {
            return;
        }

        try {
            long now = System.currentTimeMillis();
            String entry = now + ":" + contentType + ":" + contentId;

            String accessKey = CREATOR_ACCESS_KEY + userId;
            redisUtils.sAdd(accessKey, entry);
            redisUtils.expire(accessKey, 60, TimeUnit.SECONDS);

            String batchKey = CREATOR_BATCH_KEY + userId;
            redisUtils.sAdd(batchKey, entry);
            redisUtils.expire(batchKey, Math.max(60, securityConfig.getCreatorBatchAccessThresholdSeconds()), TimeUnit.SECONDS);

            log.debug("event=creator_content_access_recorded userId={} contentId={} contentType={}",
                    userId, contentId, contentType);
        } catch (Exception e) {
            log.error("event=creator_content_access_record_failed errorType={}", e.getClass().getSimpleName());
        }
    }

    @Override
    public int getPaidContentAccessCount(Long userId, String resourceType, Long resourceId) {
        if (ObjectUtils.isEmpty(userId)) {
            return 0;
        }
        return (int) getLongValue(PAID_VIEW_KEY + userId + ":" + resourceType + ":" + resourceId);
    }

    @Override
    public int getPaidContentRemainingAccess(Long userId, String resourceType, Long resourceId) {
        int limit = securityConfig.getPaidContentDailyViewLimit();
        int used = getPaidContentAccessCount(userId, resourceType, resourceId);
        return Math.max(0, limit - used);
    }

    @Override
    public int getPaidContentDownloadCount(Long userId, String resourceType, Long resourceId) {
        return (int) getLongValue(PAID_DOWNLOAD_KEY + userId + ":" + resourceType + ":" + resourceId);
    }

    @Override
    public int getPaidContentRemainingDownload(Long userId, String resourceType, Long resourceId) {
        int limit = securityConfig.getPaidContentDownloadLimit();
        int used = getPaidContentDownloadCount(userId, resourceType, resourceId);
        return Math.max(0, limit - used);
    }

    private void recordRecentPaidAccess(Long userId, String resourceType, Long resourceId, String accessType) {
        String key = PAID_RECENT_KEY + userId;
        String entry = System.currentTimeMillis() + ":" + accessType + ":" + resourceType + ":" + resourceId;
        redisUtils.sAdd(key, entry);
        redisUtils.expire(key, Math.max(60, securityConfig.getPaidContentDistinctWindowSeconds()), TimeUnit.SECONDS);
    }

    private int getRecentPaidDistinctCount(Long userId, String accessType) {
        Set<Object> recent = redisUtils.sMembers(PAID_RECENT_KEY + userId);
        if (recent == null || recent.isEmpty()) {
            return 0;
        }
        long windowStart = System.currentTimeMillis() - securityConfig.getPaidContentDistinctWindowSeconds() * 1000L;
        Set<String> distinctResources = new HashSet<>();
        for (Object item : recent) {
            String[] parts = String.valueOf(item).split(":", 4);
            if (parts.length < 4 || !accessType.equals(parts[1])) {
                continue;
            }
            long timestamp = parseLong(parts[0]);
            if (timestamp >= windowStart) {
                distinctResources.add(parts[2] + ":" + parts[3]);
            }
        }
        return distinctResources.size();
    }

    private int getRecentCreatorDistinctCount(Long userId) {
        Set<Object> recentSet = redisUtils.sMembers(CREATOR_BATCH_KEY + userId);
        if (recentSet == null || recentSet.isEmpty()) {
            return 0;
        }

        long windowStart = System.currentTimeMillis() - securityConfig.getCreatorBatchAccessThresholdSeconds() * 1000L;
        Set<String> distinctContent = new HashSet<>();
        for (Object obj : recentSet) {
            String entry = String.valueOf(obj);
            String[] parts = entry.split(":", 3);
            long timestamp = parseLong(parts[0]);
            if (timestamp < windowStart) {
                continue;
            }
            if (parts.length >= 3) {
                distinctContent.add(parts[1] + ":" + parts[2]);
            } else {
                distinctContent.add(entry);
            }
        }
        return distinctContent.size();
    }

    private long getLongValue(String key) {
        try {
            Object value = redisUtils.get(key);
            return value == null ? 0L : Long.parseLong(value.toString());
        } catch (Exception e) {
            log.warn("event=content_access_counter_read_failed errorType={}",
                    e.getClass().getSimpleName());
            return 0L;
        }
    }

    private long getLongValueStrict(String key) {
        Object value = redisUtils.get(key);
        return value == null ? 0L : Long.parseLong(value.toString());
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return 0L;
        }
    }




    private long getSecondsUntilEndOfDay() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endOfDay = now.toLocalDate().atTime(23, 59, 59);
        return java.time.Duration.between(now, endOfDay).getSeconds() + 1;
    }
}
