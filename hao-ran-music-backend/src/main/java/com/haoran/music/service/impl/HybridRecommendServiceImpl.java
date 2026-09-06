package com.haoran.music.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haoran.music.common.util.CacheHelper;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.RecommendationModelArtifactValidator;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.*;
import com.haoran.music.mapper.*;
import com.haoran.music.service.HybridRecommendService;
import com.haoran.music.service.MusicIntelligenceCacheService;
import com.haoran.music.vo.recommend.RecommendVO;
import com.haoran.music.vo.recommend.RecommendedSongVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

   
           
  
          
                                  
                             
                              
                           
  
                      
   
@Slf4j
@Service
public class HybridRecommendServiceImpl implements HybridRecommendService {

    private final SongMapper songMapper;
    private final ListenHistoryMapper listenHistoryMapper;
    private final SongLikeMapper songLikeMapper;
    private final UserFollowMapper userFollowMapper;
    private final PlaylistMapper playlistMapper;
    private final UserMapper userMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MusicIntelligenceCacheService musicIntelligenceCacheService;

    public HybridRecommendServiceImpl(SongMapper songMapper,
                                       ListenHistoryMapper listenHistoryMapper,
                                       SongLikeMapper songLikeMapper,
                                       UserFollowMapper userFollowMapper,
                                       PlaylistMapper playlistMapper,
                                       UserMapper userMapper,
                                       RedisTemplate<String, Object> redisTemplate,
                                       MusicIntelligenceCacheService musicIntelligenceCacheService) {
        this.songMapper = songMapper;
        this.listenHistoryMapper = listenHistoryMapper;
        this.songLikeMapper = songLikeMapper;
        this.userFollowMapper = userFollowMapper;
        this.playlistMapper = playlistMapper;
        this.userMapper = userMapper;
        this.redisTemplate = redisTemplate;
        this.musicIntelligenceCacheService = musicIntelligenceCacheService;
    }

             

           
    private static final String CACHE_PREFIX = "hybrid_recommend:";
    private static final String WEIGHTS_CACHE_KEY = "recommend:weights";

    @Value("${recommend.model.collaborative-path}")
    private String collaborativeModelPath;

    @Value("${recommend.model.audio-similarity-path}")
    private String audioSimilarityModelPath;

    @Value("${recommend.model.hybrid-path}")
    private String hybridModelPath;

    @Value("${recommend.model.require-manifest:true}")
    private boolean requireModelManifest;

    @Value("${recommend.collaborative.weight}")
    private double collaborativeWeight;

    @Value("${recommend.content.weight}")
    private double contentWeight;

    @Value("${recommend.popularity.weight}")
    private double popularityWeight;

    @Value("${recommend.social.weight}")
    private double socialWeight;

             
    private volatile Map<String, List<String>> collaborativeModel;
    private volatile Map<String, Map<String, Double>> audioSimilarityModel;
    private volatile Map<String, Object> hybridModel;
    private volatile List<String> globalHotSongs;
    private volatile Map<String, List<String>> genreIndex;
    private volatile String collaborativeModelVersion;
    private volatile String collaborativeCatalogVersion;
    private volatile String audioModelVersion;
    private volatile String audioCatalogVersion;
    private volatile String hybridModelVersion;
    private volatile String hybridCatalogVersion;
    private volatile Map<String, Object> collaborativeModelStatus = Collections.emptyMap();
    private volatile Map<String, Object> audioModelStatus = Collections.emptyMap();
    private volatile Map<String, Object> hybridModelStatus = Collections.emptyMap();
    private volatile String lastModelReloadAt;
    private volatile boolean lastModelReloadSuccessful;
    private volatile boolean lastCacheInvalidationSuccessful = true;
    private volatile String lastCacheInvalidationError;

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return 16;
        }
        return Math.min(limit, 100);
    }
    @Override
    public RecommendVO getHybridRecommend(Long userId, Integer limit) {
        int effectiveLimit = normalizeLimit(limit);
        String cacheKey = CACHE_PREFIX + musicIntelligenceCacheService.recommendVersionSegment()
                + activeModelFingerprint() + ":"
                + "hybrid:" + userId + ":" + effectiveLimit;
        RecommendVO cached = (RecommendVO) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }

        log.info("获取混合推荐: userId={}, limit={}", userId, limit);

                    
        List<RecommendVO.SongSimpleVO> collaborativeRecs = getCollaborativeRecommendations(userId, effectiveLimit);
        List<RecommendVO.SongSimpleVO> contentRecs = getContentBasedRecommendations(userId, effectiveLimit);
        List<RecommendVO.SongSimpleVO> popularityRecs = getPopularityRecommendations(userId, effectiveLimit);
        List<RecommendVO.SongSimpleVO> socialRecs = getSocialRecommendations(userId, effectiveLimit);
        String personalizedModelVersion = activePersonalizedModelVersion(userId);
        boolean modelBacked = personalizedModelVersion != null && !collaborativeRecs.isEmpty();

                
        List<RecommendVO.SongSimpleVO> hybridRecs = mergeRecommendations(
                collaborativeRecs, contentRecs, popularityRecs, socialRecs, effectiveLimit);

                    
        List<RecommendVO.SongSimpleVO> uniqueRecs = removeDuplicates(hybridRecs, effectiveLimit);
        fillFavoriteStatus(userId, uniqueRecs);

        RecommendVO result = new RecommendVO();
        result.setType("hybrid");
        result.setSource("hybrid");
        result.setSourceName("智能推荐");
        result.setReason(modelBacked
                ? "结合 Spark ALS 离线模型与你的站内行为推荐"
                : "结合你的站内行为、热门度和相似歌曲推荐");
        result.setModelVersion(modelBacked ? personalizedModelVersion : null);
        result.setModelBacked(modelBacked);
        result.setFallbackReason(modelBacked ? null : "当前用户暂无可用的离线模型画像，已使用在线策略回退");
        result.setSongs(uniqueRecs);

                
        redisTemplate.opsForValue().set(cacheKey, result, 1, TimeUnit.HOURS);

        return result;
    }

    @Override
    public List<RecommendedSongVO> getHybridRecommendWithReason(Long userId, Integer limit) {
        RecommendVO vo = getHybridRecommend(userId, limit);
        return convertToRecommendedSongVO(vo.getSongs(), "hybrid");
    }

    @Override
    public RecommendVO getColdStartRecommend(Long userId, Integer limit) {
        log.info("冷启动推荐: userId={}", userId);

        RecommendVO result = new RecommendVO();
        result.setType("coldstart");
        result.setSource("coldstart");
        result.setSourceName("新发现");
        result.setReason("为你精选热门好歌");

                
        List<RecommendVO.SongSimpleVO> hotSongs = getHotSongs((int) (limit * 0.6));
        List<RecommendVO.SongSimpleVO> newSongs = getNewSongs((int) (limit * 0.3));
        List<RecommendVO.SongSimpleVO> topSongs = getTopRatedSongs((int) (limit * 0.1));

        List<RecommendVO.SongSimpleVO> recommendations = new ArrayList<>();
        recommendations.addAll(hotSongs);
        recommendations.addAll(newSongs);
        recommendations.addAll(topSongs);

        List<RecommendVO.SongSimpleVO> uniqueRecs = removeDuplicates(recommendations, limit);
        fillFavoriteStatus(userId, uniqueRecs);

        result.setSongs(uniqueRecs);
        return result;
    }

    @Override
    public RecommendVO getDiscoveryRecommend(Long userId, Integer limit) {
        log.info("发现模式推荐: userId={}", userId);

        RecommendVO result = new RecommendVO();
        result.setType("discovery");
        result.setSource("discovery");
        result.setSourceName("发现音乐");
        result.setReason("探索未知的精彩");

        List<RecommendVO.SongSimpleVO> recommendations = new ArrayList<>();
        recommendations.addAll(getCrossGenreSongs(userId, (int) (limit * 0.4)));
        recommendations.addAll(getHiddenGems((int) (limit * 0.3)));
        recommendations.addAll(getRandomSongs((int) (limit * 0.3)));

        List<RecommendVO.SongSimpleVO> uniqueRecs = removeDuplicates(recommendations, limit);
        fillFavoriteStatus(userId, uniqueRecs);

        result.setSongs(uniqueRecs);
        return result;
    }

    @Override
    public RecommendVO getMoodBasedRecommend(Long userId, String mood, Integer limit) {
        log.info("情绪化推荐: userId={}, mood={}", userId, mood);

        RecommendVO result = new RecommendVO();
        result.setType("mood");
        result.setSource("mood");
        result.setSourceName(getMoodSourceName(mood));
        result.setReason(getMoodReason(mood));

        MoodConfig config = getMoodConfig(mood);
        List<RecommendVO.SongSimpleVO> recommendations = getMoodBasedSongs(config, limit);

        List<RecommendVO.SongSimpleVO> uniqueRecs = removeDuplicates(recommendations, limit);
        fillFavoriteStatus(userId, uniqueRecs);

        result.setSongs(uniqueRecs);
        return result;
    }

    @Override
    public void refreshUserRecommendProfile(Long userId) {
        log.info("刷新用户推荐画像: userId={}", userId);

        String cachePrefix = CACHE_PREFIX + musicIntelligenceCacheService.recommendVersionSegment();
        Set<String> keys = CacheHelper.keys(redisTemplate, cachePrefix + "*" + userId + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        log.info("用户推荐画像已刷新: userId={}", userId);
    }

    @Override
    public Map<String, Double> getRecommendWeights() {
        Map<String, Double> result = new HashMap<>();
        result.put("collaborative", collaborativeWeight);
        result.put("content", contentWeight);
        result.put("popularity", popularityWeight);
        result.put("social", socialWeight);
        return result;
    }

    @Override
    public void updateRecommendWeights(Map<String, Double> newWeights) {
        if (newWeights != null && !newWeights.isEmpty()) {
            if (newWeights.containsKey("collaborative")) {
                this.collaborativeWeight = newWeights.get("collaborative");
            }
            if (newWeights.containsKey("content")) {
                this.contentWeight = newWeights.get("content");
            }
            if (newWeights.containsKey("popularity")) {
                this.popularityWeight = newWeights.get("popularity");
            }
            if (newWeights.containsKey("social")) {
                this.socialWeight = newWeights.get("social");
            }

            redisTemplate.opsForValue().set(WEIGHTS_CACHE_KEY, newWeights, 24, TimeUnit.HOURS);
            log.info("推荐权重已更新: {}", newWeights);
        }
    }

    @PostConstruct
    public void loadModels() {
        reloadModelsInternal(null, false);
        loadWeights();
    }

    @Override
    public synchronized Map<String, Object> reloadModels(Long operatorId) {
        return reloadModelsInternal(operatorId, true);
    }

    @Override
    public Map<String, Object> getModelStatus() {
        Map<String, Object> models = new LinkedHashMap<>();
        models.put("collaborative", new LinkedHashMap<>(collaborativeModelStatus));
        models.put("audioSimilarity", new LinkedHashMap<>(audioModelStatus));
        models.put("hybrid", new LinkedHashMap<>(hybridModelStatus));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("models", models);
        result.put("activeFingerprint", activeModelFingerprint());
        result.put("requireManifest", requireModelManifest);
        result.put("lastReloadAt", lastModelReloadAt);
        result.put("lastReloadSuccessful", lastModelReloadSuccessful);
        result.put("cacheInvalidationSuccessful", lastCacheInvalidationSuccessful);
        if (lastCacheInvalidationError != null) {
            result.put("cacheInvalidationError", lastCacheInvalidationError);
        }
        result.put("recommendCacheVersion", musicIntelligenceCacheService.currentRecommendCacheVersion());
        return result;
    }

    private Map<String, Object> reloadModelsInternal(Long operatorId, boolean invalidateCache) {
        log.info("开始校验并加载推荐模型: operatorId={}", operatorId);
        String previousFingerprint = activeModelFingerprint();
        boolean collaborativeLoaded = loadCollaborativeModel();
        boolean audioLoaded = loadAudioSimilarityModel();
        boolean hybridLoaded = loadHybridModel();
        String currentFingerprint = activeModelFingerprint();

        lastModelReloadAt = LocalDateTime.now().toString();
        lastModelReloadSuccessful = collaborativeLoaded && audioLoaded && hybridLoaded;
        lastCacheInvalidationSuccessful = true;
        lastCacheInvalidationError = null;

        if (invalidateCache && !Objects.equals(previousFingerprint, currentFingerprint)) {
            try {
                musicIntelligenceCacheService.bumpRecommendCacheVersion(
                        "recommendation models reloaded: " + currentFingerprint, operatorId);
            } catch (RuntimeException exception) {
                lastCacheInvalidationSuccessful = false;
                lastCacheInvalidationError = exception.getClass().getSimpleName();
                log.warn("模型已切换，但推荐缓存版本更新失败；模型指纹仍会隔离旧缓存: error={}",
                        exception.getClass().getSimpleName());
            }
        }
        log.info("推荐模型加载结束: fingerprint={}, allSuccessful={}",
                currentFingerprint, lastModelReloadSuccessful);
        return getModelStatus();
    }

                                                     

    private boolean loadCollaborativeModel() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> model = RecommendationModelArtifactValidator.load(
                    mapper, collaborativeModelPath, "als_collaborative_filtering", requireModelManifest);
            @SuppressWarnings("unchecked")
            Map<String, List<String>> recs = (Map<String, List<String>>) model.get("recommendations");
            collaborativeModel = recs != null ? recs : new HashMap<>();
            collaborativeModelVersion = metadataValue(model, "model_version");
            collaborativeCatalogVersion = metadataValue(model, "catalog_version");
            collaborativeModelStatus = modelStatus("als_collaborative_filtering", collaborativeModelPath,
                    true, true, collaborativeModelVersion, collaborativeCatalogVersion,
                    collaborativeModel.size(), null);
            log.info("协同过滤模型校验并加载完成: users={}, modelVersion={}, catalogVersion={}",
                    collaborativeModel.size(), collaborativeModelVersion, collaborativeCatalogVersion);
            return true;
        } catch (Exception e) {
            log.warn("协同过滤模型加载失败，将使用MySQL数据: {}", e.getClass().getSimpleName());
            boolean hasLastKnownGood = collaborativeModel != null;
            if (collaborativeModel == null) {
                collaborativeModel = new HashMap<>();
            }
            collaborativeModelStatus = modelStatus("als_collaborative_filtering", collaborativeModelPath,
                    false, hasLastKnownGood, collaborativeModelVersion, collaborativeCatalogVersion,
                    collaborativeModel.size(), e);
            return false;
        }
    }

    private boolean loadAudioSimilarityModel() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> model = RecommendationModelArtifactValidator.load(
                    mapper, audioSimilarityModelPath, "audio_similarity", requireModelManifest);
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Double>> simMatrix = (Map<String, Map<String, Double>>) model.get("similarity_matrix");
            audioSimilarityModel = simMatrix != null ? simMatrix : new HashMap<>();
            audioModelVersion = metadataValue(model, "model_version");
            audioCatalogVersion = metadataValue(model, "catalog_version");
            audioModelStatus = modelStatus("audio_similarity", audioSimilarityModelPath,
                    true, true, audioModelVersion, audioCatalogVersion,
                    audioSimilarityModel.size(), null);
            log.info("音频相似度模型校验并加载完成: songs={}, modelVersion={}, catalogVersion={}",
                    audioSimilarityModel.size(), audioModelVersion, audioCatalogVersion);
            return true;
        } catch (Exception e) {
            log.warn("音频相似度模型加载失败，将使用内容特征: {}", e.getClass().getSimpleName());
            boolean hasLastKnownGood = audioSimilarityModel != null;
            if (audioSimilarityModel == null) {
                audioSimilarityModel = new HashMap<>();
            }
            audioModelStatus = modelStatus("audio_similarity", audioSimilarityModelPath,
                    false, hasLastKnownGood, audioModelVersion, audioCatalogVersion,
                    audioSimilarityModel.size(), e);
            return false;
        }
    }


    private boolean loadHybridModel() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> loadedModel = RecommendationModelArtifactValidator.load(
                    mapper, hybridModelPath, "hybrid_recommendation", requireModelManifest);
            @SuppressWarnings("unchecked")
            List<String> hotSongs = (List<String>) loadedModel.get("global_hot_songs");
            @SuppressWarnings("unchecked")
            Map<String, List<String>> genreIdx = (Map<String, List<String>>) loadedModel.get("genre_index");

            hybridModel = loadedModel;
            globalHotSongs = hotSongs != null ? hotSongs : new ArrayList<>();
            genreIndex = genreIdx != null ? genreIdx : new HashMap<>();
            hybridModelVersion = metadataValue(loadedModel, "model_version");
            hybridCatalogVersion = metadataValue(loadedModel, "catalog_version");
            hybridModelStatus = modelStatus("hybrid_recommendation", hybridModelPath,
                    true, true, hybridModelVersion, hybridCatalogVersion,
                    userRecommendationCount(loadedModel), null);
            log.info("混合推荐模型校验并加载完成: hotSongs={}, genres={}, modelVersion={}, catalogVersion={}",
                    globalHotSongs.size(), genreIndex.size(), hybridModelVersion, hybridCatalogVersion);
            return true;
        } catch (Exception e) {
            log.warn("混合推荐模型加载失败: {}", e.getClass().getSimpleName());
            boolean hasLastKnownGood = hybridModel != null;
            if (hybridModel == null) {
                hybridModel = new HashMap<>();
                globalHotSongs = new ArrayList<>();
                genreIndex = new HashMap<>();
            }
            hybridModelStatus = modelStatus("hybrid_recommendation", hybridModelPath,
                    false, hasLastKnownGood, hybridModelVersion, hybridCatalogVersion,
                    userRecommendationCount(hybridModel), e);
            return false;
        }
    }

    private void loadWeights() {
        @SuppressWarnings("unchecked")
        Map<String, Double> cachedWeights = (Map<String, Double>) redisTemplate.opsForValue().get(WEIGHTS_CACHE_KEY);

        if (cachedWeights != null) {
            this.collaborativeWeight = cachedWeights.getOrDefault("collaborative", this.collaborativeWeight);
            this.contentWeight = cachedWeights.getOrDefault("content", this.contentWeight);
            this.popularityWeight = cachedWeights.getOrDefault("popularity", this.popularityWeight);
            this.socialWeight = cachedWeights.getOrDefault("social", this.socialWeight);
            log.info("从缓存加载推荐权重: {}", cachedWeights);
        }
    }

    private Map<String, Object> modelStatus(String modelType,
                                             String path,
                                             boolean attemptSuccessful,
                                             boolean active,
                                             String modelVersion,
                                             String catalogVersion,
                                             int entryCount,
                                             Exception error) {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("modelType", modelType);
        status.put("artifactFile", artifactFileName(path));
        status.put("attemptSuccessful", attemptSuccessful);
        status.put("active", active);
        status.put("modelVersion", modelVersion);
        status.put("catalogVersion", catalogVersion);
        status.put("entryCount", entryCount);
        status.put("checkedAt", LocalDateTime.now().toString());
        if (error != null) {
            status.put("errorType", error.getClass().getSimpleName());
        }
        return Collections.unmodifiableMap(status);
    }

    private String artifactFileName(String path) {
        if (path == null || path.trim().isEmpty()) {
            return "";
        }
        try {
            return java.nio.file.Paths.get(path).getFileName().toString();
        } catch (RuntimeException exception) {
            return "invalid-path";
        }
    }

    @SuppressWarnings("unchecked")
    private String metadataValue(Map<String, Object> model, String key) {
        Object metadata = model.get("metadata");
        if (!(metadata instanceof Map)) {
            return null;
        }
        Object value = ((Map<String, Object>) metadata).get(key);
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private int userRecommendationCount(Map<String, Object> model) {
        if (model == null || !(model.get("user_recommendations") instanceof Map)) {
            return 0;
        }
        return ((Map<String, List<String>>) model.get("user_recommendations")).size();
    }

    private String activeModelFingerprint() {
        return "c-" + fingerprintPart(collaborativeModelVersion)
                + "_a-" + fingerprintPart(audioModelVersion)
                + "_h-" + fingerprintPart(hybridModelVersion);
    }

    private String fingerprintPart(String version) {
        if (version == null || version.trim().isEmpty()) {
            return "none";
        }
        return version.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    @SuppressWarnings("unchecked")
    private String activePersonalizedModelVersion(Long userId) {
        if (userId == null) {
            return null;
        }
        String userKey = String.valueOf(userId);
        if (collaborativeModel != null) {
            List<String> recommendations = collaborativeModel.get(userKey);
            if (recommendations != null && !recommendations.isEmpty()) {
                return collaborativeModelVersion;
            }
        }
        if (hybridModel != null && hybridModel.get("user_recommendations") instanceof Map) {
            Map<String, List<String>> recommendations =
                    (Map<String, List<String>>) hybridModel.get("user_recommendations");
            List<String> songs = recommendations.get(userKey);
            if (songs != null && !songs.isEmpty()) {
                return hybridModelVersion;
            }
        }
        return null;
    }

       
                              
       
    private List<RecommendVO.SongSimpleVO> getCollaborativeRecommendations(Long userId, Integer limit) {
        List<String> targetSongIds = new ArrayList<>();

                                        
        if (collaborativeModel != null && !collaborativeModel.isEmpty()) {
            String userIdKey = String.valueOf(userId);
            if (collaborativeModel.containsKey(userIdKey)) {
                List<String> recs = collaborativeModel.get(userIdKey);
                targetSongIds = recs.stream()
                        .limit(limit)
                        .collect(Collectors.toList());
            }
        }

        if (targetSongIds.isEmpty() && hybridModel != null && hybridModel.containsKey("user_recommendations")) {
            @SuppressWarnings("unchecked")
            Map<String, List<String>> userRecs = (Map<String, List<String>>) hybridModel.get("user_recommendations");
            List<String> recSongIds = userRecs.get(String.valueOf(userId));

            if (recSongIds != null && !recSongIds.isEmpty()) {
                targetSongIds = recSongIds.stream()
                        .limit(limit)
                        .collect(Collectors.toList());
            }
        }

                          
        if (targetSongIds.isEmpty() && !globalHotSongs.isEmpty()) {
            targetSongIds = globalHotSongs.stream()
                    .limit(limit)
                    .collect(Collectors.toList());
        }

                        
        if (!targetSongIds.isEmpty()) {
            return batchGetSongsByIds(targetSongIds, limit);
        }

        return new ArrayList<>();
    }

       
                 
       
    private List<RecommendVO.SongSimpleVO> batchGetSongsByIds(List<String> songIdStrs, Integer limit) {
        List<Long> songIds = new ArrayList<>();
        for (String idStr : songIdStrs) {
            try {
                songIds.add(Long.parseLong(idStr));
            } catch (NumberFormatException e) {
                log.warn("无效的歌曲ID: {}", idStr);
            }
        }

        if (songIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> rankedSongIds = new ArrayList<>(new LinkedHashSet<>(songIds));
        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Song::getId, rankedSongIds)
                .eq(Song::getStatus, 1)
                .eq(Song::getDeleted, 0);

        List<Song> songs = filterPublicUploaderSongs(songMapper.selectList(wrapper)).stream()
                .filter(this::hasPlayableAudio)
                .collect(Collectors.toList());
        Map<Long, Song> songMap = songs.stream().collect(Collectors.toMap(
                Song::getId, song -> song, (first, ignored) -> first));
        List<RecommendVO.SongSimpleVO> result = new ArrayList<>();
        for (Long songId : rankedSongIds) {
            Song song = songMap.get(songId);
            if (song != null) {
                result.add(convertToSimpleVO(song));
                if (result.size() >= limit) {
                    break;
                }
            }
        }
        return result;
    }
    private List<RecommendVO.SongSimpleVO> getContentBasedRecommendations(Long userId, Integer limit) {
        List<RecommendVO.SongSimpleVO> recommendations = new ArrayList<>();

        List<Long> recentSongIds = getUserRecentSongIds(userId, 10);
        if (recentSongIds.isEmpty()) {
            return recommendations;
        }

        Set<String> preferredGenres = getUserPreferredGenres(userId);

        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Song::getStatus, 1)
                .eq(Song::getDeleted, 0)
                .in(Song::getMainType, preferredGenres.isEmpty() ? Arrays.asList("Pop") : preferredGenres)
                .orderByDesc(Song::getPlayCount)
                .last("LIMIT " + limit);

        List<Song> songs = songMapper.selectList(wrapper);
        return convertToSimpleVO(songs);
    }

       
                         
       
    private List<RecommendVO.SongSimpleVO> getPopularityRecommendations(Long userId, Integer limit) {
        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Song::getStatus, 1)
                .eq(Song::getDeleted, 0)
                .orderByDesc(Song::getPlayCount, Song::getFavoriteCount)
                .last("LIMIT " + limit);

        List<Song> songs = songMapper.selectList(wrapper);
        return convertToSimpleVO(songs);
    }

       
                            
       
    private List<RecommendVO.SongSimpleVO> getSocialRecommendations(Long userId, Integer limit) {
        Set<Long> friendIds = getMutualFriends(userId);

        if (friendIds.isEmpty()) {
            return new ArrayList<>();
        }

                        
        Set<Long> allFriendLikedSongIds = new HashSet<>();
        for (Long friendId : friendIds) {
            List<Long> friendLikedSongs = getFriendLikedSongs(friendId, 5);
            allFriendLikedSongIds.addAll(friendLikedSongs);
        }

        if (allFriendLikedSongIds.isEmpty()) {
            return new ArrayList<>();
        }

                 
        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Song::getId, allFriendLikedSongIds)
                .eq(Song::getStatus, 1)
                .eq(Song::getDeleted, 0)
                .last("LIMIT " + limit);

        List<Song> songs = songMapper.selectList(wrapper);
        return convertToSimpleVO(songs);
    }

       
               
       
    private List<RecommendVO.SongSimpleVO> mergeRecommendations(
            List<RecommendVO.SongSimpleVO> collaborativeRecs,
            List<RecommendVO.SongSimpleVO> contentRecs,
            List<RecommendVO.SongSimpleVO> popularityRecs,
            List<RecommendVO.SongSimpleVO> socialRecs,
            Integer limit) {

        Map<Long, Double> scoreMap = new HashMap<>();

                 
        for (int i = 0; i < collaborativeRecs.size(); i++) {
            RecommendVO.SongSimpleVO song = collaborativeRecs.get(i);
            double score = (collaborativeRecs.size() - i) * collaborativeWeight;
            scoreMap.merge(song.getId(), score, Double::sum);
        }

                  
        for (int i = 0; i < contentRecs.size(); i++) {
            RecommendVO.SongSimpleVO song = contentRecs.get(i);
            double score = (contentRecs.size() - i) * contentWeight;
            scoreMap.merge(song.getId(), score, Double::sum);
        }

                
        for (int i = 0; i < popularityRecs.size(); i++) {
            RecommendVO.SongSimpleVO song = popularityRecs.get(i);
            double score = (popularityRecs.size() - i) * popularityWeight;
            scoreMap.merge(song.getId(), score, Double::sum);
        }

               
        for (int i = 0; i < socialRecs.size(); i++) {
            RecommendVO.SongSimpleVO song = socialRecs.get(i);
            double score = (socialRecs.size() - i) * socialWeight;
            scoreMap.merge(song.getId(), score, Double::sum);
        }

                
        List<Long> sortedSongIds = scoreMap.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

                   
        if (!sortedSongIds.isEmpty()) {
            LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
            wrapper.in(Song::getId, sortedSongIds)
                    .eq(Song::getStatus, 1)
                    .eq(Song::getDeleted, 0);

            List<Song> songs = filterPublicUploaderSongs(songMapper.selectList(wrapper)).stream()
                    .filter(this::hasPlayableAudio)
                    .collect(Collectors.toList());
            Map<Long, Song> songMap = songs.stream()
                    .collect(Collectors.toMap(Song::getId, s -> s));

            List<RecommendVO.SongSimpleVO> result = new ArrayList<>();
            for (Long songId : sortedSongIds) {
                Song song = songMap.get(songId);
                if (song != null) {
                    result.add(convertToSimpleVO(song));
                }
            }
            return result;
        }

        return new ArrayList<>();
    }

    private List<Long> getUserRecentSongIds(Long userId, int count) {
        LambdaQueryWrapper<ListenHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ListenHistory::getUserId, userId)
                .eq(ListenHistory::getDeleted, 0)
                .orderByDesc(ListenHistory::getListenTime)
                .last("LIMIT " + count);

        List<ListenHistory> histories = listenHistoryMapper.selectList(wrapper);
        return histories.stream()
                .map(ListenHistory::getSongId)
                .filter(ObjectUtils::isNotEmpty)
                .collect(Collectors.toList());
    }

    private Set<String> getUserPreferredGenres(Long userId) {
        List<Long> recentSongs = getUserRecentSongIds(userId, 100);

        if (recentSongs.isEmpty()) {
            return new HashSet<>(Arrays.asList("Pop", "Rock"));
        }

        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Song::getId, recentSongs)
                .select(Song::getMainType);

        List<Map<String, Object>> result = songMapper.selectMaps(wrapper);
        Map<String, Integer> genreCount = new HashMap<>();

        for (Map<String, Object> row : result) {
            String genre = (String) row.get("main_type");
            if (ObjectUtils.isNotEmpty(genre)) {
                genreCount.merge(genre, 1, Integer::sum);
            }
        }

        return genreCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private Set<Long> getMutualFriends(Long userId) {
        Set<Long> following = getFollowingUsers(userId);
        Set<Long> followers = getFollowers(userId);

        Set<Long> friends = new HashSet<>(following);
        friends.retainAll(followers);
        return friends;
    }

    private Set<Long> getFollowingUsers(Long userId) {
        LambdaQueryWrapper<UserFollow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFollow::getFollowerId, userId)
                .eq(UserFollow::getDeleted, 0);

        List<UserFollow> follows = userFollowMapper.selectList(wrapper);
        Set<Long> userIds = follows.stream()
                .map(UserFollow::getFolloweeId)
                .collect(Collectors.toSet());
        return filterEligibleUserIds(userIds);
    }

    private Set<Long> getFollowers(Long userId) {
        LambdaQueryWrapper<UserFollow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFollow::getFolloweeId, userId)
                .eq(UserFollow::getDeleted, 0);

        List<UserFollow> follows = userFollowMapper.selectList(wrapper);
        Set<Long> userIds = follows.stream()
                .map(UserFollow::getFollowerId)
                .collect(Collectors.toSet());
        return filterEligibleUserIds(userIds);
    }

    private List<Long> getFriendLikedSongs(Long friendId, int limit) {
        LambdaQueryWrapper<SongLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SongLike::getUserId, friendId)
                .eq(SongLike::getIsFavorite, 1)
                .eq(SongLike::getDeleted, 0)
                .last("LIMIT " + limit);

        List<SongLike> likes = songLikeMapper.selectList(wrapper);
        return likes.stream()
                .map(SongLike::getSongId)
                .collect(Collectors.toList());
    }

    private List<RecommendVO.SongSimpleVO> getHotSongs(int limit) {
        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Song::getStatus, 1)
                .eq(Song::getDeleted, 0)
                .eq(Song::getIsHot, 1)
                .orderByDesc(Song::getPlayCount, Song::getFavoriteCount)
                .last("LIMIT " + limit);

        List<Song> songs = songMapper.selectList(wrapper);
        return convertToSimpleVO(songs);
    }

    private List<RecommendVO.SongSimpleVO> getNewSongs(int limit) {
        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Song::getStatus, 1)
                .eq(Song::getDeleted, 0)
                .ge(Song::getCreateTime, LocalDateTime.now().minusDays(30))
                .orderByDesc(Song::getCreateTime)
                .last("LIMIT " + limit);

        List<Song> songs = songMapper.selectList(wrapper);
        return convertToSimpleVO(songs);
    }

    private List<RecommendVO.SongSimpleVO> getTopRatedSongs(int limit) {
        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Song::getStatus, 1)
                .eq(Song::getDeleted, 0)
                .orderByDesc(Song::getHotScore)
                .last("LIMIT " + limit);

        List<Song> songs = songMapper.selectList(wrapper);
        return convertToSimpleVO(songs);
    }

    private List<RecommendVO.SongSimpleVO> getCrossGenreSongs(Long userId, int limit) {
        Set<String> preferredGenres = getUserPreferredGenres(userId);

        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Song::getStatus, 1)
                .eq(Song::getDeleted, 0);

        if (!preferredGenres.isEmpty()) {
            wrapper.notIn(Song::getMainType, preferredGenres);
        }

        wrapper.orderByDesc(Song::getPlayCount)
                .last("LIMIT " + limit);

        List<Song> songs = songMapper.selectList(wrapper);
        return convertToSimpleVO(songs);
    }

    private List<RecommendVO.SongSimpleVO> getHiddenGems(int limit) {
        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Song::getStatus, 1)
                .eq(Song::getDeleted, 0)
                .gt(Song::getPlayCount, 100)
                .lt(Song::getPlayCount, 5000)
                .gt(Song::getFavoriteCount, 10)
                .orderByDesc(Song::getPlayCount)
                .last("LIMIT " + limit);

        List<Song> songs = songMapper.selectList(wrapper);
        return convertToSimpleVO(songs);
    }

       
                                  
       
    private List<RecommendVO.SongSimpleVO> getRandomSongs(int limit) {
                               
        String randomKey = "recommend:random:songs";
        List<Long> randomIds = (List<Long>) redisTemplate.opsForValue().get(randomKey);

        if (randomIds == null || randomIds.isEmpty()) {
                                 
            LambdaQueryWrapper<Song> countWrapper = new LambdaQueryWrapper<>();
            countWrapper.eq(Song::getStatus, 1).eq(Song::getDeleted, 0);
            Long total = songMapper.selectCount(countWrapper);

            if (total == null || total == 0) {
                return new ArrayList<>();
            }

            randomIds = new ArrayList<>();
            java.util.Random rand = new java.util.Random();
            for (int i = 0; i < Math.min(100, total.intValue()); i++) {
                randomIds.add((long) rand.nextInt(total.intValue()) + 1);
            }

                    
            redisTemplate.opsForValue().set(randomKey, randomIds, 1, TimeUnit.HOURS);
        }

                     
        List<Long> targetIds = randomIds.stream()
                .limit(limit)
                .collect(Collectors.toList());

               
        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Song::getId, targetIds)
                .eq(Song::getStatus, 1)
                .eq(Song::getDeleted, 0);

        List<Song> songs = songMapper.selectList(wrapper);
        Collections.shuffle(songs);
        return convertToSimpleVO(songs.stream().limit(limit).collect(Collectors.toList()));
    }

    private List<RecommendVO.SongSimpleVO> removeDuplicates(List<RecommendVO.SongSimpleVO> songs, Integer limit) {
        Set<Long> seen = new HashSet<>();
        List<RecommendVO.SongSimpleVO> result = new ArrayList<>();

        for (RecommendVO.SongSimpleVO song : songs) {
            if (song != null && song.getId() != null && !seen.contains(song.getId())) {
                seen.add(song.getId());
                result.add(song);
                if (result.size() >= limit) {
                    break;
                }
            }
        }

        return result;
    }

    private void fillFavoriteStatus(Long userId, List<RecommendVO.SongSimpleVO> songs) {
        if (userId == null || songs == null || songs.isEmpty()) {
            return;
        }

        List<Long> songIds = songs.stream()
                .map(RecommendVO.SongSimpleVO::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (songIds.isEmpty()) {
            return;
        }

        LambdaQueryWrapper<SongLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SongLike::getUserId, userId)
               .eq(SongLike::getIsFavorite, 1)
               .eq(SongLike::getDeleted, 0)
               .in(SongLike::getSongId, songIds)
               .select(SongLike::getSongId);

        List<SongLike> likes = songLikeMapper.selectList(wrapper);
        Set<Long> likedSongIds = likes.stream()
                .map(SongLike::getSongId)
                .collect(Collectors.toSet());

        songs.forEach(song -> song.setIsFavorite(likedSongIds.contains(song.getId())));
    }

    private RecommendVO.SongSimpleVO convertToSimpleVO(Song song) {
        if (song == null) {
            return null;
        }

        RecommendVO.SongSimpleVO vo = new RecommendVO.SongSimpleVO();
        vo.setId(song.getId());
        vo.setName(song.getName());
        vo.setArtistNames(song.getArtistNames());
        vo.setArtistId(song.getArtistId());
        vo.setAlbumName(song.getAlbumName());
        vo.setAlbumId(song.getAlbumId());
        vo.setDuration(song.getDuration());
        vo.setMainType(song.getMainType());
        vo.setCover(song.getCover());
        vo.setFavoriteCount(song.getFavoriteCount() != null ? song.getFavoriteCount().intValue() : null);
        vo.setPlayCount(song.getPlayCount());
        vo.setIsNew(song.getIsNew());
        vo.setIsHot(song.getIsHot());
        vo.setUrlStandard(song.getUrlStandard());
        vo.setUrlHigh(song.getUrlHigh());
        vo.setUrlLossless(song.getUrlLossless());
        vo.setSizeStandard(song.getSizeStandard());
        vo.setSizeHigh(song.getSizeHigh());
        vo.setSizeLossless(song.getSizeLossless());
        vo.setLanguage(song.getLanguage());
        vo.setVersionType(song.getVersionType());
        vo.setVersionName(song.getVersionName());

        return vo;
    }

    private List<RecommendVO.SongSimpleVO> convertToSimpleVO(List<Song> songs) {
        if (songs == null || songs.isEmpty()) {
            return Collections.emptyList();
        }

        return filterPublicUploaderSongs(songs).stream()
                .filter(this::hasPlayableAudio)
                .map(this::convertToSimpleVO)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private boolean hasPlayableAudio(Song song) {
        return song != null && (hasText(song.getUrlStandard())
                || hasText(song.getUrlHigh())
                || hasText(song.getUrlLossless()));
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private List<Song> filterPublicUploaderSongs(List<Song> songs) {
        if (songs == null || songs.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> uploaderIds = songs.stream()
                .map(Song::getUploaderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> allowedUploaderIds = uploaderIds.isEmpty()
                ? Collections.emptySet()
                : userMapper.selectBatchIds(uploaderIds).stream()
                .filter(UserAccountStatusUtil::canExposePublicContent)
                .map(User::getId)
                .collect(Collectors.toSet());
        return songs.stream()
                .filter(song -> song.getUploaderId() == null
                        || allowedUploaderIds.contains(song.getUploaderId()))
                .collect(Collectors.toList());
    }

    private Set<Long> filterEligibleUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptySet();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .filter(UserAccountStatusUtil::canAppearInRecommendations)
                .map(User::getId)
                .collect(Collectors.toSet());
    }

    private List<RecommendedSongVO> convertToRecommendedSongVO(List<RecommendVO.SongSimpleVO> songs, String source) {
        if (songs == null || songs.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, String> sourceNames = new HashMap<>();
        sourceNames.put("collaborative", "协同过滤推荐");
        sourceNames.put("content", "相似推荐");
        sourceNames.put("popularity", "热门推荐");
        sourceNames.put("social", "好友推荐");
        sourceNames.put("hybrid", "智能推荐");

        return songs.stream().map(song -> {
            RecommendedSongVO vo = new RecommendedSongVO();
            vo.setId(song.getId());
            vo.setName(song.getName());
            vo.setArtistNames(song.getArtistNames());
            vo.setAlbumName(song.getAlbumName());
            vo.setAlbumId(song.getAlbumId());
            vo.setDuration(song.getDuration());
            vo.setMainType(song.getMainType());
            vo.setCover(song.getCover());
            vo.setFavoriteCount(song.getFavoriteCount() != null ? song.getFavoriteCount().intValue() : null);
            vo.setPlayCount(song.getPlayCount());
            vo.setIsNew(song.getIsNew());
            vo.setIsHot(song.getIsHot());
            vo.setUrlStandard(song.getUrlStandard());
            vo.setUrlHigh(song.getUrlHigh());
            vo.setUrlLossless(song.getUrlLossless());
            vo.setSizeStandard(song.getSizeStandard());
            vo.setSizeHigh(song.getSizeHigh());
            vo.setSizeLossless(song.getSizeLossless());
            vo.setLanguage(song.getLanguage());
            vo.setVersionType(song.getVersionType());
            vo.setVersionName(song.getVersionName());
            vo.setIsFavorite(song.getIsFavorite());

            vo.setSource(source);
            vo.setSourceName(sourceNames.getOrDefault(source, "推荐"));
            vo.setConfidence(75 + (int) (Math.random() * 20));
            vo.setTags(Arrays.asList(song.getMainType()));

            return vo;
        }).collect(Collectors.toList());
    }

    private String getMoodSourceName(String mood) {
        switch (mood.toLowerCase()) {
            case "happy":
            case "energetic":
                return "快乐时光";
            case "sad":
                return "治愈时刻";
            case "calm":
                return "轻松时刻";
            case "focus":
                return "专注时刻";
            default:
                return "音乐陪伴";
        }
    }

    private String getMoodReason(String mood) {
        switch (mood.toLowerCase()) {
            case "happy":
                return "精选欢快歌曲，点亮你的心情";
            case "sad":
                return "温暖治愈，陪伴你度过低落";
            case "energetic":
                return "动感节拍，释放你的活力";
            case "calm":
                return "轻柔音乐，放松身心";
            case "focus":
                return "专注背景音，提升效率";
            default:
                return "为你推荐";
        }
    }

    private MoodConfig getMoodConfig(String mood) {
        switch (mood.toLowerCase()) {
            case "happy":
                return new MoodConfig(120.0, 0.7, "Pop", "Dance");
            case "sad":
                return new MoodConfig(80.0, 0.3, "Ballad", "Classical");
            case "energetic":
                return new MoodConfig(140.0, 0.8, "Rock", "Electronic");
            case "calm":
                return new MoodConfig(70.0, 0.2, "Ambient", "Folk");
            case "focus":
                return new MoodConfig(100.0, 0.4, "Classical", "Ambient");
            default:
                return new MoodConfig(100.0, 0.5, "Pop", "Pop");
        }
    }

    private List<RecommendVO.SongSimpleVO> getMoodBasedSongs(MoodConfig config, Integer limit) {
        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Song::getStatus, 1)
                .eq(Song::getDeleted, 0);

                 
        if (config.genre != null) {
            wrapper.and(w -> w.eq(Song::getMainType, config.genre)
                    .or()
                    .eq(Song::getSubTypes, config.genre));
        }

        wrapper.orderByDesc(Song::getPlayCount)
                .last("LIMIT " + limit);

        List<Song> songs = songMapper.selectList(wrapper);
        return convertToSimpleVO(songs);
    }

       
            
       
    private static class MoodConfig {
        Double minBpm;
        Double maxBpm;
        String genre;
        String subGenre;

        MoodConfig(double bpm, double energy, String genre, String subGenre) {
            this.minBpm = bpm - 20;
            this.maxBpm = bpm + 20;
            this.genre = genre;
            this.subGenre = subGenre;
        }
    }
}
