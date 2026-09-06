   
                      
   
package com.haoran.music.service;

import java.util.Map;

public interface MusicIntelligenceCacheService {

    long currentCandidateCacheVersion();

    String candidateVersionSegment();

    long bumpCandidateCacheVersion(String reason, Long operatorId);

    Map<String, Object> getCandidateCacheStatus();

    long currentRecommendCacheVersion();

    String recommendVersionSegment();

    long bumpRecommendCacheVersion(String reason, Long operatorId);

    long currentRankingCacheVersion();

    String rankingVersionSegment();

    long bumpRankingCacheVersion(String reason, Long operatorId);

    long currentSearchCacheVersion();

    String searchVersionSegment();

    long bumpSearchCacheVersion(String reason, Long operatorId);

    Map<String, Object> getCacheStatus();
}
