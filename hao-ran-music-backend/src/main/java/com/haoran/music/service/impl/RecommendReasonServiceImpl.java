package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.*;
import com.haoran.music.mapper.*;
import com.haoran.music.service.RecommendReasonService;
import com.haoran.music.vo.recommend.RecommendReasonVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

   
                      
                         
   
@Slf4j
@Service
public class RecommendReasonServiceImpl implements RecommendReasonService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SongMapper songMapper;

    @Autowired
    private ListenHistoryMapper listenHistoryMapper;

    @Autowired
    private SongLikeMapper songLikeMapper;

    @Autowired
    private UserFollowMapper userFollowMapper;

    @Autowired
    private UserTagPreferenceMapper userTagPreferenceMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

       
                   
       
    private static final Integer REASON_CACHE_HOURS = 3;

    @Override
    public RecommendReasonVO getRecommendReason(Long userId, Long itemId, String itemType) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(itemId) || ObjectUtils.isEmpty(itemType)) {
            return null;
        }

                  
        String cacheKey = "reason:" + itemType + ":" + userId + ":" + itemId;
        RecommendReasonVO cached = (RecommendReasonVO) redisTemplate.opsForValue().get(cacheKey);
        if (ObjectUtils.isNotEmpty(cached)) {
            return cached;
        }

        RecommendReasonVO reason = new RecommendReasonVO();
        reason.setItemId(itemId);
        reason.setItemType(itemType);

                      
        String reasonType = determineReasonType(userId, itemId, itemType);
        reason.setReasonType(reasonType);
        reason.setReasonTypeDescription(getReasonTypeDescription(reasonType));

                   
        String reasonText = generateReasonText(userId, itemId, itemType, reasonType);
        reason.setReasonText(reasonText);
        reason.setReasonTemplate(getReasonTemplate(reasonType));

                   
        reason.setConfidence(calculateConfidence(userId, itemId, itemType, reasonType));
        reason.setWeight(calculateWeight(userId, itemType));

                 
        reason.setExplainable(true);
        reason.setRecommendSource(getRecommendSource(reasonType));

                 
        List<RecommendReasonVO.RelatedEntity> relatedEntities = generateRelatedEntities(userId, itemId, itemType, reasonType);
        reason.setRelatedEntities(relatedEntities);

               
        redisTemplate.opsForValue().set(cacheKey, reason, REASON_CACHE_HOURS, TimeUnit.HOURS);

        log.debug("生成推荐理由: userId={}, itemId={}, type={}", userId, itemId, reasonType);

        return reason;
    }

    @Override
    public List<RecommendReasonVO> batchGetRecommendReasons(Long userId, Map<Long, String> items) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(items)) {
            return new ArrayList<>();
        }

        return items.entrySet().stream()
                .map(entry -> getRecommendReason(userId, entry.getKey(), entry.getValue()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public RecommendReasonVO generatePersonalizedReason(Long userId, Long itemId, String itemType, String recommendSource) {
        RecommendReasonVO reason = getRecommendReason(userId, itemId, itemType);
        if (ObjectUtils.isEmpty(reason)) {
            return null;
        }

                     
        reason.setRecommendSource(recommendSource);

        return reason;
    }

    @Override
    public Map<String, Integer> getReasonTypeStatistics() {
        Map<String, Integer> stats = new LinkedHashMap<>();

                   
        for (RecommendReasonVO.ReasonType type : RecommendReasonVO.ReasonType.values()) {
            stats.put(type.getCode(), 0);
        }

                                 
        String statsKey = "reason:stats:types";
        Map<String, Integer> cached = (Map<String, Integer>) redisTemplate.opsForValue().get(statsKey);
        if (ObjectUtils.isNotEmpty(cached)) {
            return cached;
        }

                      
        redisTemplate.opsForValue().set(statsKey, stats, 1, TimeUnit.HOURS);

        return stats;
    }

    @Override
    public Boolean updateReasonConfig(String reasonType, Boolean enabled, String template) {
        if (ObjectUtils.isEmpty(reasonType)) {
            return false;
        }

                     
        String configKey = "reason:config:" + reasonType;
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("enabled", ObjectUtils.isNotEmpty(enabled) ? enabled : true);
        config.put("template", ObjectUtils.isNotEmpty(template) ? template : getReasonTemplate(reasonType));
        config.put("updateTime", LocalDateTime.now());

        redisTemplate.opsForValue().set(configKey, config, 30, TimeUnit.DAYS);

        log.info("更新推荐理由配置: reasonType={}, enabled={}", reasonType, enabled);

        return true;
    }

    @Override
    public Map<String, Object> getReasonABTestData(String testId) {
        Map<String, Object> testData = new LinkedHashMap<>();

        testData.put("testId", testId);
        Map<String, Object> clickRateMap = new LinkedHashMap<>();
        clickRateMap.put("A", 15.5);
        clickRateMap.put("B", 18.2);
        Map<String, Object> conversionRateMap = new LinkedHashMap<>();
        conversionRateMap.put("A", 3.2);
        conversionRateMap.put("B", 3.8);
        Map<String, Object> metricsMap = new LinkedHashMap<>();
        metricsMap.put("clickRate", clickRateMap);
        metricsMap.put("conversionRate", conversionRateMap);
        testData.put("winner", "B");
        testData.put("confidence", 95.0);

        return testData;
    }

    @Override
    public Boolean recordReasonFeedback(Long userId, Long itemId, String itemType, Boolean helpful) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(itemId)) {
            return false;
        }

                     
        String feedbackKey = "reason:feedback:" + itemType + ":" + userId + ":" + itemId;
        Map<String, Object> feedback = new LinkedHashMap<>();
        feedback.put("userId", userId);
        feedback.put("itemId", itemId);
        feedback.put("itemType", itemType);
        feedback.put("helpful", ObjectUtils.isNotEmpty(helpful) ? helpful : false);
        feedback.put("feedbackTime", LocalDateTime.now());

        redisTemplate.opsForValue().set(feedbackKey, feedback, 7, TimeUnit.DAYS);

               
        String statsKey = "reason:feedback:stats:" + itemType;
        redisTemplate.opsForHash().increment(statsKey, "total", 1);
        if (ObjectUtils.isNotEmpty(helpful) && helpful) {
            redisTemplate.opsForHash().increment(statsKey, "helpful", 1);
        }
        redisTemplate.expire(statsKey, 30, TimeUnit.DAYS);

        log.debug("记录推荐理由反馈: userId={}, itemId={}, helpful={}", userId, itemId, helpful);

        return true;
    }

    @Override
    public Map<String, Object> getReasonEffectiveness(Integer days) {
        Map<String, Object> effectiveness = new LinkedHashMap<>();

        int analysisDays = ObjectUtils.isNotEmpty(days) ? days : 7;

                       
        Map<String, Object> feedbackStats = new LinkedHashMap<>();
        for (RecommendReasonVO.ReasonType type : RecommendReasonVO.ReasonType.values()) {
            String statsKey = "reason:feedback:stats:" + type.getCode();
            Object total = redisTemplate.opsForHash().get(statsKey, "total");
            Object helpful = redisTemplate.opsForHash().get(statsKey, "helpful");

            Map<String, Object> typeStats = new LinkedHashMap<>();
            typeStats.put("total", ObjectUtils.isNotEmpty(total) ? total : 0);
            typeStats.put("helpful", ObjectUtils.isNotEmpty(helpful) ? helpful : 0);

            int totalInt = ObjectUtils.isNotEmpty(total) ? Integer.parseInt(total.toString()) : 0;
            int helpfulInt = ObjectUtils.isNotEmpty(helpful) ? Integer.parseInt(helpful.toString()) : 0;
            double helpfulRate = totalInt > 0 ? (double) helpfulInt / totalInt * 100 : 0;
            typeStats.put("helpfulRate", String.format("%.2f%%", helpfulRate));

            feedbackStats.put(type.getCode(), typeStats);
        }

        effectiveness.put("period", analysisDays + "天");
        effectiveness.put("feedbackStats", feedbackStats);
        effectiveness.put("generateTime", LocalDateTime.now());

        return effectiveness;
    }

    @Override
    public Map<String, Object> optimizeReasonDisplay(Long userId) {
        Map<String, Object> config = new LinkedHashMap<>();

                 
        Map<String, Integer> userPreference = analyzeUserReasonPreference(userId);

                 
        config.put("userId", userId);
        config.put("preferredReasonTypes", userPreference);
        config.put("displayStrategy", determineDisplayStrategy(userPreference));
        config.put("maxReasonsToShow", 2);
        config.put("showConfidence", true);
        config.put("showRelatedEntities", true);

        return config;
    }

       
               
       
    private String determineReasonType(Long userId, Long itemId, String itemType) {
                                           

                        
        if (isFromSocialSource(userId, itemId)) {
            return RecommendReasonVO.ReasonType.SOCIAL_BASED.getCode();
        }

                        
        if (isFromHistory(userId, itemId)) {
            return RecommendReasonVO.ReasonType.HISTORY_BASED.getCode();
        }

                      
        if (isFromFavorite(userId, itemId)) {
            return RecommendReasonVO.ReasonType.FAVORITE_BASED.getCode();
        }

                        
        if (isFromTagPreference(userId, itemId)) {
            return RecommendReasonVO.ReasonType.TAG_BASED.getCode();
        }

                      
        return RecommendReasonVO.ReasonType.PORTRAIT_BASED.getCode();
    }

       
                 
       
    private boolean isFromSocialSource(Long userId, Long itemId) {
                        
        LambdaQueryWrapper<UserFollow> followWrapper = new LambdaQueryWrapper<>();
        followWrapper.eq(UserFollow::getFollowerId, userId)
                .eq(UserFollow::getDeleted, CommonConstants.NOT_DELETED);
        List<UserFollow> follows = userFollowMapper.selectList(followWrapper);

        if (ObjectUtils.isEmpty(follows)) {
            return false;
        }

        for (UserFollow follow : follows) {
            Long followeeId = follow.getFolloweeId();
            if (!UserAccountStatusUtil.canAppearInRecommendations(followeeId, userMapper::selectById)) {
                continue;
            }
            LambdaQueryWrapper<SongLike> likeWrapper = new LambdaQueryWrapper<>();
            likeWrapper.eq(SongLike::getUserId, followeeId)
                    .eq(SongLike::getSongId, itemId)
                    .eq(SongLike::getDeleted, CommonConstants.NOT_DELETED);
            Long count = songLikeMapper.selectCount(likeWrapper);
            if (ObjectUtils.isNotEmpty(count) && count > 0) {
                return true;
            }
        }

        return false;
    }

       
                 
       
    private boolean isFromHistory(Long userId, Long itemId) {
        LambdaQueryWrapper<ListenHistory> historyWrapper = new LambdaQueryWrapper<>();
        historyWrapper.eq(ListenHistory::getUserId, userId)
                .eq(ListenHistory::getSongId, itemId)
                .orderByDesc(ListenHistory::getListenTime)
                .last("LIMIT 1");
        ListenHistory history = listenHistoryMapper.selectOne(historyWrapper);

        return ObjectUtils.isNotEmpty(history);
    }

       
               
       
    private boolean isFromFavorite(Long userId, Long itemId) {
        LambdaQueryWrapper<SongLike> likeWrapper = new LambdaQueryWrapper<>();
        likeWrapper.eq(SongLike::getUserId, userId)
                .eq(SongLike::getSongId, itemId);
        Long count = songLikeMapper.selectCount(likeWrapper);

        return ObjectUtils.isNotEmpty(count) && count > 0;
    }

       
                 
       
    private boolean isFromTagPreference(Long userId, Long itemId) {
                  
        Song song = songMapper.selectById(itemId);
        if (ObjectUtils.isEmpty(song) || ObjectUtils.isEmpty(song.getMainGenre())) {
            return false;
        }

                      
        LambdaQueryWrapper<UserTagPreference> prefWrapper = new LambdaQueryWrapper<>();
        prefWrapper.eq(UserTagPreference::getUserId, userId)
                .eq(UserTagPreference::getTagName, song.getMainGenre());
        Long count = userTagPreferenceMapper.selectCount(prefWrapper);

        return ObjectUtils.isNotEmpty(count) && count > 0;
    }

       
               
       
    private String generateReasonText(Long userId, Long itemId, String itemType, String reasonType) {
        switch (reasonType) {
            case "social_based":
                         
                String friendName = getFriendNameWhoLiked(userId, itemId);
                if (ObjectUtils.isNotEmpty(friendName)) {
                    return "您的好友" + friendName + "也喜欢这首歌";
                }
                return "好友也喜欢这首歌";
            case "history_based":
                Song historySong = songMapper.selectById(itemId);
                return "因为您最近常听《" + (ObjectUtils.isNotEmpty(historySong) ? historySong.getName() : "") + "》";
            case "favorite_based":
                Song favoriteSong = songMapper.selectById(itemId);
                return "根据您收藏的《" + (ObjectUtils.isNotEmpty(favoriteSong) ? favoriteSong.getName() : "") + "》推荐";
            case "tag_based":
                return "根据您的风格偏好推荐";
            case "portrait_based":
                return "根据您的听歌习惯推荐";
            case "hot_based":
                return "本周热门推荐";
            default:
                return "为您推荐";
        }
    }

       
                   
       
    private String getFriendNameWhoLiked(Long userId, Long itemId) {
        LambdaQueryWrapper<UserFollow> followWrapper = new LambdaQueryWrapper<>();
        followWrapper.eq(UserFollow::getFollowerId, userId);
        List<UserFollow> follows = userFollowMapper.selectList(followWrapper);

        for (UserFollow follow : follows) {
            Long followeeId = follow.getFolloweeId();
            if (!UserAccountStatusUtil.canAppearInRecommendations(followeeId, userMapper::selectById)) {
                continue;
            }
            LambdaQueryWrapper<SongLike> likeWrapper = new LambdaQueryWrapper<>();
            likeWrapper.eq(SongLike::getUserId, followeeId)
                    .eq(SongLike::getSongId, itemId);
            Long count = songLikeMapper.selectCount(likeWrapper);
            if (ObjectUtils.isNotEmpty(count) && count > 0) {
                User friend = userMapper.selectById(followeeId);
                if (ObjectUtils.isNotEmpty(friend)) {
                    return friend.getUsername();
                }
            }
        }

        return null;
    }

       
               
       
    private String getReasonTypeDescription(String reasonType) {
        for (RecommendReasonVO.ReasonType type : RecommendReasonVO.ReasonType.values()) {
            if (type.getCode().equals(reasonType)) {
                return type.getDescription();
            }
        }
        return "未知类型";
    }

       
             
       
    private String getReasonTemplate(String reasonType) {
        for (RecommendReasonVO.ReasonType type : RecommendReasonVO.ReasonType.values()) {
            if (type.getCode().equals(reasonType)) {
                return type.getTemplate();
            }
        }
        return "为您推荐";
    }

       
             
       
    private String getRecommendSource(String reasonType) {
        return "推荐引擎_" + reasonType;
    }

       
            
       
    private Integer calculateConfidence(Long userId, Long itemId, String itemType, String reasonType) {
                      
        Integer confidence;
        switch (reasonType) {
            case "social_based":
                confidence = 85;
                break;
            case "history_based":
                confidence = 80;
                break;
            case "favorite_based":
                confidence = 90;
                break;
            case "tag_based":
                confidence = 75;
                break;
            case "portrait_based":
                confidence = 70;
                break;
            default:
                confidence = 60;
                break;
        }
        return confidence;
    }

       
             
       
    private Double calculateWeight(Long userId, String itemType) {
                         
        return 1.0;
    }

       
             
       
    private List<RecommendReasonVO.RelatedEntity> generateRelatedEntities(Long userId, Long itemId, String itemType, String reasonType) {
        List<RecommendReasonVO.RelatedEntity> entities = new ArrayList<>();

        if ("social_based".equals(reasonType)) {
                         
            String friendName = getFriendNameWhoLiked(userId, itemId);
            if (ObjectUtils.isNotEmpty(friendName)) {
                RecommendReasonVO.RelatedEntity entity = new RecommendReasonVO.RelatedEntity();
                entity.setEntityType("user");
                entity.setEntityName(friendName);
                entity.setRelationDescription("也喜欢这首歌");
                entities.add(entity);
            }
        } else if ("history_based".equals(reasonType) || "favorite_based".equals(reasonType)) {
                     
            Song song = songMapper.selectById(itemId);
            if (ObjectUtils.isNotEmpty(song)) {
                RecommendReasonVO.RelatedEntity entity = new RecommendReasonVO.RelatedEntity();
                entity.setEntityId(itemId);
                entity.setEntityType("song");
                entity.setEntityName(song.getName());
                entity.setRelationDescription("相关歌曲");
                entities.add(entity);
            }
        }

        return entities;
    }

       
                   
       
    private Map<String, Integer> analyzeUserReasonPreference(Long userId) {
        Map<String, Integer> preference = new LinkedHashMap<>();

                     
        for (RecommendReasonVO.ReasonType type : RecommendReasonVO.ReasonType.values()) {
            preference.put(type.getCode(), 50);
        }

                     
                         

        return preference;
    }

       
             
       
    private String determineDisplayStrategy(Map<String, Integer> preference) {
                      
        String topType = preference.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("portrait_based");

        return "优先展示" + topType + "类型的推荐理由";
    }
}
