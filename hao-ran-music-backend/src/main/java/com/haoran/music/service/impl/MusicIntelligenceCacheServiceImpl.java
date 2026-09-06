   
                      
   
package com.haoran.music.service.impl;

import com.haoran.music.common.util.RedisUtils;
import com.haoran.music.service.MusicIntelligenceCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class MusicIntelligenceCacheServiceImpl implements MusicIntelligenceCacheService {

    private static final String CANDIDATE_VERSION_KEY = "audio:intelligence:candidate-version";
    private static final String CANDIDATE_VERSION_META_KEY = "audio:intelligence:candidate-version:meta";
    private static final String RECOMMEND_VERSION_KEY = "audio:intelligence:recommend-version";
    private static final String RECOMMEND_VERSION_META_KEY = "audio:intelligence:recommend-version:meta";
    private static final String RANKING_VERSION_KEY = "audio:intelligence:ranking-version";
    private static final String RANKING_VERSION_META_KEY = "audio:intelligence:ranking-version:meta";
    private static final String SEARCH_VERSION_KEY = "audio:intelligence:search-version";
    private static final String SEARCH_VERSION_META_KEY = "audio:intelligence:search-version:meta";
    private static final long DEFAULT_VERSION = 1L;

    @Resource
    private RedisUtils redisUtils;

    @Override
    public long currentCandidateCacheVersion() {
        try {
            Object value = redisUtils.get(CANDIDATE_VERSION_KEY);
            if (value == null) {
                return DEFAULT_VERSION;
            }
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            log.warn("读取音乐智能候选缓存版本失败，使用默认版本: error={}", e.getClass().getSimpleName());
            return DEFAULT_VERSION;
        }
    }

    @Override
    public String candidateVersionSegment() {
        return "v" + currentCandidateCacheVersion() + ":";
    }

    @Override
    public long bumpCandidateCacheVersion(String reason, Long operatorId) {
        try {
            if (!redisUtils.hasKey(CANDIDATE_VERSION_KEY)) {
                redisUtils.set(CANDIDATE_VERSION_KEY, DEFAULT_VERSION);
            }
            Long version = redisUtils.increment(CANDIDATE_VERSION_KEY);
            long resolvedVersion = version == null ? DEFAULT_VERSION + 1 : version;
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("version", resolvedVersion);
            metadata.put("reason", reason == null ? "" : reason);
            metadata.put("operatorId", operatorId);
            metadata.put("updatedAt", LocalDateTime.now().toString());
            redisUtils.set(CANDIDATE_VERSION_META_KEY, metadata, 30, TimeUnit.DAYS);
            log.info("音乐智能候选缓存版本已更新: version={}, operatorId={}, reason={}",
                    resolvedVersion, operatorId, reason);
            return resolvedVersion;
        } catch (Exception e) {
            log.warn("更新音乐智能候选缓存版本失败: operatorId={}, reason={}, error={}",
                    operatorId, reason, e.getClass().getSimpleName());
            throw new IllegalStateException("候选缓存版本更新失败");
        }
    }

    @Override
    public Map<String, Object> getCandidateCacheStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("version", currentCandidateCacheVersion());
        try {
            Object metadata = redisUtils.get(CANDIDATE_VERSION_META_KEY);
            if (metadata != null) {
                result.put("metadata", metadata);
            }
        } catch (Exception e) {
            result.put("metadataError", e.getMessage());
        }
        result.put("versionKey", CANDIDATE_VERSION_KEY);
        return result;
    }

    @Override
    public long currentRecommendCacheVersion() {
        return currentVersion(RECOMMEND_VERSION_KEY, "推荐");
    }

    @Override
    public String recommendVersionSegment() {
        return "v" + currentRecommendCacheVersion() + ":";
    }

    @Override
    public long bumpRecommendCacheVersion(String reason, Long operatorId) {
        return bumpVersion(RECOMMEND_VERSION_KEY, RECOMMEND_VERSION_META_KEY, "推荐", reason, operatorId);
    }

    @Override
    public long currentRankingCacheVersion() {
        return currentVersion(RANKING_VERSION_KEY, "排行榜");
    }

    @Override
    public String rankingVersionSegment() {
        return "v" + currentRankingCacheVersion() + ":";
    }

    @Override
    public long bumpRankingCacheVersion(String reason, Long operatorId) {
        return bumpVersion(RANKING_VERSION_KEY, RANKING_VERSION_META_KEY, "排行榜", reason, operatorId);
    }

    @Override
    public long currentSearchCacheVersion() {
        return currentVersion(SEARCH_VERSION_KEY, "搜索");
    }

    @Override
    public String searchVersionSegment() {
        return "v" + currentSearchCacheVersion() + ":";
    }

    @Override
    public long bumpSearchCacheVersion(String reason, Long operatorId) {
        return bumpVersion(SEARCH_VERSION_KEY, SEARCH_VERSION_META_KEY, "搜索", reason, operatorId);
    }

    @Override
    public Map<String, Object> getCacheStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("candidate", getCandidateCacheStatus());
        result.put("recommend", getVersionStatus(RECOMMEND_VERSION_KEY, RECOMMEND_VERSION_META_KEY, "推荐"));
        result.put("ranking", getVersionStatus(RANKING_VERSION_KEY, RANKING_VERSION_META_KEY, "排行榜"));
        result.put("search", getVersionStatus(SEARCH_VERSION_KEY, SEARCH_VERSION_META_KEY, "搜索"));
        return result;
    }

    private long currentVersion(String key, String label) {
        try {
            Object value = redisUtils.get(key);
            return value == null ? DEFAULT_VERSION : Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            log.warn("读取{}缓存版本失败，使用默认版本: error={}", label, e.getClass().getSimpleName());
            return DEFAULT_VERSION;
        }
    }

    private long bumpVersion(String key, String metaKey, String label, String reason, Long operatorId) {
        try {
            if (!redisUtils.hasKey(key)) {
                redisUtils.set(key, DEFAULT_VERSION);
            }
            Long version = redisUtils.increment(key);
            long resolvedVersion = version == null ? DEFAULT_VERSION + 1 : version;
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("version", resolvedVersion);
            metadata.put("reason", reason == null ? "" : reason);
            metadata.put("operatorId", operatorId);
            metadata.put("updatedAt", LocalDateTime.now().toString());
            redisUtils.set(metaKey, metadata, 30, TimeUnit.DAYS);
            log.info("{}缓存版本已更新: version={}, operatorId={}, reason={}",
                    label, resolvedVersion, operatorId, reason);
            return resolvedVersion;
        } catch (Exception e) {
            log.warn("更新{}缓存版本失败: operatorId={}, reason={}, error={}",
                    label, operatorId, reason, e.getClass().getSimpleName());
            throw new IllegalStateException(label + "缓存版本更新失败");
        }
    }

    private Map<String, Object> getVersionStatus(String key, String metaKey, String label) {
        Map<String, Object> result = new HashMap<>();
        result.put("version", currentVersion(key, label));
        try {
            Object metadata = redisUtils.get(metaKey);
            if (metadata != null) {
                result.put("metadata", metadata);
            }
        } catch (Exception e) {
            result.put("metadataError", e.getMessage());
        }
        result.put("versionKey", key);
        return result;
    }
}
