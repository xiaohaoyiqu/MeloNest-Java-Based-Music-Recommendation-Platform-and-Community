package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.haoran.music.common.config.UserGrowthConfig;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.User;
import com.haoran.music.entity.UserCheckin;
import com.haoran.music.mapper.*;
import com.haoran.music.service.UserActivityQuickService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

   
                      
                             
  
        
                           
                               
   
@Slf4j
@Service
public class UserActivityQuickServiceImpl implements UserActivityQuickService {

    private final UserCheckinMapper userCheckinMapper;
    private final UserActivityPointsMapper userActivityPointsMapper;
    private final UserMapper userMapper;
    private final UserFollowMapper userFollowMapper;
    private final CommentMapper commentMapper;
    private final SongLikeMapper songLikeMapper;
    private final ShareRecordMapper shareRecordMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final UserGrowthConfig userGrowthConfig;


    public UserActivityQuickServiceImpl(UserCheckinMapper userCheckinMapper,
                                        UserActivityPointsMapper userActivityPointsMapper,
                                        UserMapper userMapper,
                                        UserFollowMapper userFollowMapper,
                                        CommentMapper commentMapper,
                                        SongLikeMapper songLikeMapper,
                                        ShareRecordMapper shareRecordMapper,
                                        RedisTemplate<String, Object> redisTemplate,
                                        UserGrowthConfig userGrowthConfig) {
        this.userCheckinMapper = userCheckinMapper;
        this.userActivityPointsMapper = userActivityPointsMapper;
        this.userMapper = userMapper;
        this.userFollowMapper = userFollowMapper;
        this.commentMapper = commentMapper;
        this.songLikeMapper = songLikeMapper;
        this.shareRecordMapper = shareRecordMapper;
        this.redisTemplate = redisTemplate;
        this.userGrowthConfig = userGrowthConfig;
    }

    @Override
    public Integer getActivityScore(Long userId) {
        if (userId == null) {
            return 0;
        }
        if (!UserAccountStatusUtil.canInteract(userMapper.selectById(userId))) {
            return 0;
        }

        String cacheKey = userGrowthConfig.getActivity().getCacheKeyPrefix() + userId;
        Integer cachedScore = parseCachedScore(redisTemplate.opsForValue().get(cacheKey));
        if (cachedScore != null) {
            return cachedScore;
        }

        int baseScore = calculateBaseActivityScore(userId);
        int socialScore = calculateSocialActivityScore(userId);
        int score = calculateCombinedActivityScore(baseScore, socialScore);

        redisTemplate.opsForValue().set(cacheKey, score,
                userGrowthConfig.getActivity().getCacheExpireMinutes(), TimeUnit.MINUTES);

        return score;
    }
    @Override
    public Boolean isActiveUser(Long userId) {
        if (userId == null) {
            return false;
        }

                               
        int activityScore = getActivityScore(userId);
        return activityScore >= userGrowthConfig.getActivity().getActiveMinScore();
    }

    @Override
    public String getActivityLevel(Long userId) {
        if (userId == null) {
            return "unknown";
        }
        return resolveActivityLevel(getActivityScore(userId));
    }
    @Override
    public Map<String, Object> getActivityDetail(Long userId) {
        Map<String, Object> detail = new HashMap<>();
        User user = userId == null ? null : userMapper.selectById(userId);
        boolean canInteract = UserAccountStatusUtil.canInteract(user);

               
        int monthCheckinCount = getMonthCheckinCount(userId);
        int continuousDays = getContinuousDays(userId);
        int totalCheckinCount = getTotalCheckinCount(userId);
        boolean checkedToday = hasCheckedInToday(userId);

                
        int totalPoints = getUserTotalPoints(userId);

                   
        int baseActivityScore = canInteract ? calculateBaseActivityScore(userId) : 0;
        int socialScore = canInteract ? calculateSocialActivityScore(userId) : 0;
        int activityScore = canInteract ? calculateCombinedActivityScore(baseActivityScore, socialScore) : 0;
        String activityLevel = resolveActivityLevel(activityScore);
        boolean isActive = activityScore >= userGrowthConfig.getActivity().getActiveMinScore();
                 
        String username = user != null ? (user.getNickname() != null ? user.getNickname() : user.getUsername()) : "未知";

                   
        List<LocalDate> monthCheckinDates = getMonthCheckinDates(userId);

        detail.put("userId", userId);
        detail.put("username", username);
        detail.put("activityScore", activityScore);
        detail.put("baseActivityScore", baseActivityScore);
        detail.put("socialScore", socialScore);
        detail.put("scoreWeights", buildScoreWeights());
        detail.put("activityLevel", activityLevel);
        detail.put("isActive", isActive);
        detail.put("monthCheckinCount", monthCheckinCount);
        detail.put("continuousDays", continuousDays);
        detail.put("totalCheckinCount", totalCheckinCount);
        detail.put("checkedToday", checkedToday);
        detail.put("totalPoints", totalPoints);
        detail.put("monthCheckinDates", monthCheckinDates);
        detail.put("accountUnavailable", !canInteract);
        if (!canInteract) {
            detail.put("accountUnavailableMessage", UserAccountStatusUtil.currentUnavailableMessage(user));
        }
        detail.put("analysisTime", LocalDateTime.now());

        return detail;
    }

    @Override
    public Map<Long, Integer> batchGetActivityScore(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new HashMap<>();
        }

        List<Long> distinctUserIds = userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Integer> scores = new HashMap<>();
        if (distinctUserIds.isEmpty()) {
            return scores;
        }

        String prefix = userGrowthConfig.getActivity().getCacheKeyPrefix();
        List<String> cacheKeys = distinctUserIds.stream()
                .map(userId -> prefix + userId)
                .collect(Collectors.toList());
        List<Object> cachedValues = redisTemplate.opsForValue().multiGet(cacheKeys);

        List<Long> missingUserIds = new ArrayList<>();
        for (int i = 0; i < distinctUserIds.size(); i++) {
            Long userId = distinctUserIds.get(i);
            Object raw = cachedValues != null && i < cachedValues.size() ? cachedValues.get(i) : null;
            Integer cachedScore = parseCachedScore(raw);
            if (cachedScore != null) {
                scores.put(userId, cachedScore);
            } else {
                missingUserIds.add(userId);
            }
        }

        for (Long userId : missingUserIds) {
            scores.put(userId, getActivityScore(userId));
        }
        return scores;
    }
    @Override
    public Map<String, Object> getCheckinStatistics(Long userId) {
        Map<String, Object> stats = new HashMap<>();
        User user = userId == null ? null : userMapper.selectById(userId);
        boolean canInteract = UserAccountStatusUtil.canInteract(user);

                 
        int monthCheckinCount = getMonthCheckinCount(userId);
        int continuousDays = getContinuousDays(userId);
        boolean checkedToday = hasCheckedInToday(userId);
        int totalCheckinCount = getTotalCheckinCount(userId);

                
        int daysInMonth = LocalDate.now().lengthOfMonth();
        int daysPassed = LocalDate.now().getDayOfMonth();
        double checkinRate = daysPassed > 0 ? (double) monthCheckinCount / daysPassed : 0;

                 
        int weekCheckinCount = getWeekCheckinCount(userId);

        stats.put("monthCheckinCount", monthCheckinCount);
        stats.put("continuousDays", continuousDays);
        stats.put("checkedToday", checkedToday);
        stats.put("totalCheckinCount", totalCheckinCount);
        stats.put("checkinRate", checkinRate);
        stats.put("weekCheckinCount", weekCheckinCount);
        stats.put("activityScore", canInteract ? calculateBaseActivityScore(userId) : 0);
        stats.put("accountUnavailable", !canInteract);
        if (!canInteract) {
            stats.put("accountUnavailableMessage", UserAccountStatusUtil.currentUnavailableMessage(user));
        }
        stats.put("daysInMonth", daysInMonth);
        stats.put("daysPassed", daysPassed);

        return stats;
    }

    @Override
    public Map<String, Object> getSocialStatistics(Long userId) {
        Map<String, Object> stats = new HashMap<>();
        User user = userId == null ? null : userMapper.selectById(userId);
        boolean canInteract = UserAccountStatusUtil.canInteract(user);

                  
        long followingCount = getUserFollowingCount(userId);
        long followerCount = getUserFollowerCount(userId);

                        
        int monthCommentCount = getMonthCommentCount(userId);
        int totalCommentCount = getTotalCommentCount(userId);

                  
        int monthLikeCount = getMonthLikeCount(userId);
        int totalLikeCount = getTotalLikeCount(userId);

                        
        int monthShareCount = getMonthShareCount(userId);
        int totalShareCount = getTotalShareCount(userId);

                            
        int socialScore = calculateSocialScore(followingCount, followerCount,
                monthCommentCount, monthLikeCount, monthShareCount);

        stats.put("userId", userId);
        stats.put("followingCount", followingCount);           
        stats.put("followerCount", followerCount);             
        stats.put("monthCommentCount", monthCommentCount);         
        stats.put("totalCommentCount", totalCommentCount);          
        stats.put("monthLikeCount", monthLikeCount);             
        stats.put("totalLikeCount", totalLikeCount);            
        stats.put("monthShareCount", monthShareCount);           
        stats.put("totalShareCount", totalShareCount);          
        stats.put("socialScore", canInteract ? socialScore : 0);           
        stats.put("accountUnavailable", !canInteract);
        if (!canInteract) {
            stats.put("accountUnavailableMessage", UserAccountStatusUtil.currentUnavailableMessage(user));
        }
        stats.put("analysisTime", LocalDateTime.now());

        return stats;
    }

    @Override
    public Boolean refreshActivityCache(Long userId) {
        if (userId == null) {
            return false;
        }

        try {
                   
            String cacheKey = userGrowthConfig.getActivity().getCacheKeyPrefix() + userId;
            redisTemplate.delete(cacheKey);

                      
            int score = getActivityScore(userId);

            log.info("刷新用户活跃度缓存: userId={}, score={}", userId, score);
            return true;
        } catch (Exception e) {
            log.error("刷新用户活跃度缓存失败: userId={}", userId);
            return false;
        }
    }

    private int calculateBaseActivityScore(Long userId) {
        UserGrowthConfig.Activity activityConfig = userGrowthConfig.getActivity();
        int score = 0;

        int monthCheckinCount = getMonthCheckinCount(userId);
        score += Math.min(
                monthCheckinCount * positive(activityConfig.getMonthCheckinPerDayScore(), 2),
                positive(activityConfig.getMonthCheckinMaxScore(), 50));

        int continuousDays = getContinuousDays(userId);
        score += Math.min(
                continuousDays * positive(activityConfig.getContinuousPerDayScore(), 2),
                positive(activityConfig.getContinuousMaxScore(), 20));

        int totalPoints = getUserTotalPoints(userId);
        score += Math.min(
                totalPoints / positive(activityConfig.getPointsPerScore(), 10),
                positive(activityConfig.getPointsMaxScore(), 20));

        int totalCheckinCount = getTotalCheckinCount(userId);
        score += Math.min(
                totalCheckinCount / positive(activityConfig.getTotalCheckinPerScore(), 10),
                positive(activityConfig.getTotalCheckinMaxScore(), 10));

        return Math.min(score, positive(activityConfig.getMaxScore(), 100));
    }

    private int calculateSocialActivityScore(Long userId) {
        long followingCount = getUserFollowingCount(userId);
        long followerCount = getUserFollowerCount(userId);
        int monthCommentCount = getMonthCommentCount(userId);
        int monthLikeCount = getMonthLikeCount(userId);
        int monthShareCount = getMonthShareCount(userId);
        return calculateSocialScore(followingCount, followerCount, monthCommentCount, monthLikeCount, monthShareCount);
    }

    private int calculateCombinedActivityScore(int baseScore, int socialScore) {
        UserGrowthConfig.Activity activityConfig = userGrowthConfig.getActivity();
        int baseWeight = positive(activityConfig.getBaseScoreWeight(), 80);
        int socialWeight = positive(activityConfig.getSocialScoreWeight(), 20);
        int totalWeight = Math.max(1, baseWeight + socialWeight);
        int maxScore = positive(activityConfig.getMaxScore(), 100);
        int combined = (int) Math.round((baseScore * baseWeight + socialScore * socialWeight) / (double) totalWeight);
        return Math.max(0, Math.min(combined, maxScore));
    }

    private Map<String, Integer> buildScoreWeights() {
        UserGrowthConfig.Activity activityConfig = userGrowthConfig.getActivity();
        Map<String, Integer> weights = new HashMap<>();
        weights.put("base", positive(activityConfig.getBaseScoreWeight(), 80));
        weights.put("social", positive(activityConfig.getSocialScoreWeight(), 20));
        return weights;
    }

    private String resolveActivityLevel(int activityScore) {
        UserGrowthConfig.Activity activityConfig = userGrowthConfig.getActivity();
        if (activityScore >= activityConfig.getSuperActiveMinScore()) {
            return "super_active";
        }
        if (activityScore >= activityConfig.getActiveMinScore()) {
            return "active";
        }
        if (activityScore >= activityConfig.getNormalMinScore()) {
            return "normal";
        }
        return "inactive";
    }

    private Integer parseCachedScore(Object cachedValue) {
        if (cachedValue == null) {
            return null;
        }
        if (cachedValue instanceof Number) {
            return ((Number) cachedValue).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(cachedValue));
        } catch (Exception e) {
            return null;
        }
    }
                                                       

       
               
       
    private int getMonthCheckinCount(Long userId) {
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);

        LambdaQueryWrapper<UserCheckin> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCheckin::getUserId, userId)
                .ge(UserCheckin::getCheckinDate, monthStart);

        return Math.toIntExact(userCheckinMapper.selectCount(wrapper));
    }

       
               
       
    private int getWeekCheckinCount(Long userId) {
        LocalDate weekStart = LocalDate.now().minusDays(LocalDate.now().getDayOfWeek().getValue() - 1);

        LambdaQueryWrapper<UserCheckin> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCheckin::getUserId, userId)
                .ge(UserCheckin::getCheckinDate, weekStart);

        return Math.toIntExact(userCheckinMapper.selectCount(wrapper));
    }

       
               
       
    private int getContinuousDays(Long userId) {
        LocalDate today = LocalDate.now();

        LambdaQueryWrapper<UserCheckin> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCheckin::getUserId, userId)
                .orderByDesc(UserCheckin::getCheckinDate)
                .last("LIMIT 1");

        UserCheckin lastCheckin = userCheckinMapper.selectOne(wrapper);
        if (lastCheckin == null) {
            return 0;
        }

                                 
        LocalDate lastDate = lastCheckin.getCheckinDate();
        long daysBetween = ChronoUnit.DAYS.between(lastDate, today);
        if (daysBetween > 1) {
            return 0;
        }

        return lastCheckin.getContinuousDays() != null ? lastCheckin.getContinuousDays() : 0;
    }

       
              
       
    private int getTotalCheckinCount(Long userId) {
        LambdaQueryWrapper<UserCheckin> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCheckin::getUserId, userId);

        return Math.toIntExact(userCheckinMapper.selectCount(wrapper));
    }

       
              
       
    private boolean hasCheckedInToday(Long userId) {
        LocalDate today = LocalDate.now();

        LambdaQueryWrapper<UserCheckin> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCheckin::getUserId, userId)
                .eq(UserCheckin::getCheckinDate, today);

        return userCheckinMapper.selectCount(wrapper) > 0;
    }

       
                                               
       
    private int getUserTotalPoints(Long userId) {
        Integer points = userActivityPointsMapper.getUserTotalPoints(userId);
        return points == null ? 0 : points;
    }

       
                 
       
    private List<LocalDate> getMonthCheckinDates(Long userId) {
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);

        LambdaQueryWrapper<UserCheckin> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCheckin::getUserId, userId)
                .between(UserCheckin::getCheckinDate, monthStart, monthEnd)
                .orderByAsc(UserCheckin::getCheckinDate);

        return userCheckinMapper.selectList(wrapper).stream()
                .map(UserCheckin::getCheckinDate)
                .collect(Collectors.toList());
    }

       
              
       
    private long getUserFollowingCount(Long userId) {
        LambdaQueryWrapper<com.haoran.music.entity.UserFollow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(com.haoran.music.entity.UserFollow::getFollowerId, userId);
        return userFollowMapper.selectCount(wrapper);
    }

       
              
       
    private long getUserFollowerCount(Long userId) {
        LambdaQueryWrapper<com.haoran.music.entity.UserFollow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(com.haoran.music.entity.UserFollow::getFolloweeId, userId);
        return userFollowMapper.selectCount(wrapper);
    }

       
              
       
    private int getMonthCommentCount(Long userId) {
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);

        LambdaQueryWrapper<com.haoran.music.entity.Comment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(com.haoran.music.entity.Comment::getUserId, userId)
                .ge(com.haoran.music.entity.Comment::getCreateTime, monthStart);

        return Math.toIntExact(commentMapper.selectCount(wrapper));
    }

       
             
       
    private int getTotalCommentCount(Long userId) {
        LambdaQueryWrapper<com.haoran.music.entity.Comment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(com.haoran.music.entity.Comment::getUserId, userId);

        return Math.toIntExact(commentMapper.selectCount(wrapper));
    }

       
              
       
    private int getMonthLikeCount(Long userId) {
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);

        LambdaQueryWrapper<com.haoran.music.entity.SongLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(com.haoran.music.entity.SongLike::getUserId, userId)
                .eq(com.haoran.music.entity.SongLike::getIsLike, 1)
                .ge(com.haoran.music.entity.SongLike::getCreateTime, monthStart);

        return Math.toIntExact(songLikeMapper.selectCount(wrapper));
    }

       
             
       
    private int getTotalLikeCount(Long userId) {
        LambdaQueryWrapper<com.haoran.music.entity.SongLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(com.haoran.music.entity.SongLike::getUserId, userId)
                .eq(com.haoran.music.entity.SongLike::getIsLike, 1);

        return Math.toIntExact(songLikeMapper.selectCount(wrapper));
    }

       
              
       
    private int getMonthShareCount(Long userId) {
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);

        LambdaQueryWrapper<com.haoran.music.entity.ShareRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(com.haoran.music.entity.ShareRecord::getUserId, userId)
                .ge(com.haoran.music.entity.ShareRecord::getCreateTime, monthStart);

        return Math.toIntExact(shareRecordMapper.selectCount(wrapper));
    }

       
             
       
    private int getTotalShareCount(Long userId) {
        LambdaQueryWrapper<com.haoran.music.entity.ShareRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(com.haoran.music.entity.ShareRecord::getUserId, userId);

        return Math.toIntExact(shareRecordMapper.selectCount(wrapper));
    }

       
                       
       
    private int calculateSocialScore(long followingCount, long followerCount,
                                     int monthCommentCount, int monthLikeCount, int monthShareCount) {
        int score = 0;
        UserGrowthConfig.Activity activityConfig = userGrowthConfig.getActivity();

        int followScore = (int) Math.min((followingCount + followerCount) / 2,
                positive(activityConfig.getSocialFollowMaxScore(), 20));
        score += followScore;

        int commentScore = Math.min(
                monthCommentCount * positive(activityConfig.getSocialCommentPerScore(), 2),
                positive(activityConfig.getSocialCommentMaxScore(), 40));
        score += commentScore;

        int likeScore = Math.min(
                monthLikeCount * positive(activityConfig.getSocialLikePerScore(), 1),
                positive(activityConfig.getSocialLikeMaxScore(), 30));
        score += likeScore;

        int shareScore = Math.min(
                monthShareCount * positive(activityConfig.getSocialSharePerScore(), 2),
                positive(activityConfig.getSocialShareMaxScore(), 10));
        score += shareScore;

        return Math.min(score, positive(activityConfig.getSocialScoreMax(), 100));
    }

       
                                                                                           
      
  
    private int positive(Integer value, int fallback) {
        return ObjectUtils.isEmpty(value) || value <= 0 ? fallback : value;
    }
}
