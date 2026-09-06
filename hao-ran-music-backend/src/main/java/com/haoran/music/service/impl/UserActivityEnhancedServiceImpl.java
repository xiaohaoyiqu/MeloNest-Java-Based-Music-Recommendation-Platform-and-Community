package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.haoran.music.common.constant.ActivityAntiSpamConstants;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.PaymentOrderStatusUtil;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.*;
import com.haoran.music.mapper.*;
import com.haoran.music.service.OnlineStatusService;
import com.haoran.music.service.MusicIntelligenceCacheService;
import com.haoran.music.service.UserActivityEnhancedService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;







@Slf4j
@Service
public class UserActivityEnhancedServiceImpl implements UserActivityEnhancedService {


    private final UserCheckinMapper userCheckinMapper;
    private final UserActivityPointsMapper userActivityPointsMapper;
    private final UserFollowMapper userFollowMapper;
    private final CommentMapper commentMapper;
    private final SongLikeMapper songLikeMapper;
    private final ShareRecordMapper shareRecordMapper;
    private final MusicPostMapper musicPostMapper;
    private final PlaylistMapper playlistMapper;
    private final CreatorWorkMapper creatorWorkMapper;
    private final VipOrderMapper vipOrderMapper;
    private final PaymentOrderMapper paymentOrderMapper;
    private final LyricRequestMapper lyricRequestMapper;
    private final SongResourceRequestMapper songResourceRequestMapper;
    private final UserMapper userMapper;
    private final UserCreditMapper userCreditMapper;
    private final ListenHistoryMapper listenHistoryMapper;
    private final OnlineStatusService onlineStatusService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MusicIntelligenceCacheService musicIntelligenceCacheService;

    private static final int CACHE_EXPIRE_MINUTES = 30;
    private static final int MAX_RANKING_LIMIT = 100;
    private static final int RANKING_CANDIDATE_LIMIT = 500;

    public UserActivityEnhancedServiceImpl(
            UserCheckinMapper userCheckinMapper,
            UserActivityPointsMapper userActivityPointsMapper,
            UserFollowMapper userFollowMapper,
            CommentMapper commentMapper,
            SongLikeMapper songLikeMapper,
            ShareRecordMapper shareRecordMapper,
            MusicPostMapper musicPostMapper,
            PlaylistMapper playlistMapper,
            CreatorWorkMapper creatorWorkMapper,
            VipOrderMapper vipOrderMapper,
            PaymentOrderMapper paymentOrderMapper,
            LyricRequestMapper lyricRequestMapper,
            SongResourceRequestMapper songResourceRequestMapper,
            UserMapper userMapper,
            UserCreditMapper userCreditMapper,
            ListenHistoryMapper listenHistoryMapper,
            OnlineStatusService onlineStatusService,
            RedisTemplate<String, Object> redisTemplate,
            MusicIntelligenceCacheService musicIntelligenceCacheService) {
        this.userCheckinMapper = userCheckinMapper;
        this.userActivityPointsMapper = userActivityPointsMapper;
        this.userFollowMapper = userFollowMapper;
        this.commentMapper = commentMapper;
        this.songLikeMapper = songLikeMapper;
        this.shareRecordMapper = shareRecordMapper;
        this.musicPostMapper = musicPostMapper;
        this.playlistMapper = playlistMapper;
        this.creatorWorkMapper = creatorWorkMapper;
        this.vipOrderMapper = vipOrderMapper;
        this.paymentOrderMapper = paymentOrderMapper;
        this.lyricRequestMapper = lyricRequestMapper;
        this.songResourceRequestMapper = songResourceRequestMapper;
        this.userMapper = userMapper;
        this.userCreditMapper = userCreditMapper;
        this.listenHistoryMapper = listenHistoryMapper;
        this.onlineStatusService = onlineStatusService;
        this.redisTemplate = redisTemplate;
        this.musicIntelligenceCacheService = musicIntelligenceCacheService;
    }



    @Override
    public Integer getEnhancedActivityScore(Long userId) {
        if (userId == null) {
            return 0;
        }
        return getEnhancedActivityScore(userMapper.selectById(userId));
    }

    private Integer getEnhancedActivityScore(User user) {
        if (!UserAccountStatusUtil.canInteract(user)) {
            return 0;
        }
        Long userId = user.getId();


        String cacheKey = ActivityAntiSpamConstants.ACTIVITY_CACHE_KEY_PREFIX + userId;
        Integer cachedScore = (Integer) redisTemplate.opsForValue().get(cacheKey);
        if (cachedScore != null) {
            return cachedScore;
        }


        int checkinScore = calculateCheckinActivityScore(userId);
        int socialScore = calculateSocialActivityScore(userId);
        int consumptionScore = calculateConsumptionActivityScore(userId);
        int creationScore = calculateCreationActivityScore(userId);
        int contentScore = calculateContentActivityScore(userId);


        double totalScore =
                checkinScore * ActivityAntiSpamConstants.CHECKIN_WEIGHT +
                socialScore * ActivityAntiSpamConstants.SOCIAL_WEIGHT +
                consumptionScore * ActivityAntiSpamConstants.CONSUMPTION_WEIGHT +
                creationScore * ActivityAntiSpamConstants.CREATION_WEIGHT;


        double contentBonus = contentScore * ActivityAntiSpamConstants.CONTENT_BONUS_WEIGHT;
        totalScore += contentBonus;


        double creditWeight = getCreditWeight(userId);
        totalScore = totalScore * creditWeight;

        int finalScore = (int) Math.min(Math.round(totalScore), 110);                


        redisTemplate.opsForValue().set(cacheKey, finalScore, CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);

        log.debug("用户活跃度计算: userId={}, checkin={}, social={}, consumption={}, creation={}, content={}, final={}",
                userId, checkinScore, socialScore, consumptionScore, creationScore, contentScore, finalScore);

        return finalScore;
    }

    @Override
    public Map<String, Object> getActivityDimensionDetail(Long userId) {
        Map<String, Object> detail = new HashMap<>();
        User user = userId == null ? null : userMapper.selectById(userId);
        boolean canInteract = UserAccountStatusUtil.canInteract(user);

        int checkinScore = canInteract ? calculateCheckinActivityScore(userId) : 0;
        int socialScore = canInteract ? calculateSocialActivityScore(userId) : 0;
        int consumptionScore = canInteract ? calculateConsumptionActivityScore(userId) : 0;
        int creationScore = canInteract ? calculateCreationActivityScore(userId) : 0;
        int contentScore = canInteract ? calculateContentActivityScore(userId) : 0;


        detail.put("userId", userId);
        Map<String, Object> checkinMap = new HashMap<>();
        checkinMap.put("score", checkinScore);
        checkinMap.put("weight", ActivityAntiSpamConstants.CHECKIN_WEIGHT);
        checkinMap.put("weightedScore", (int)(checkinScore * ActivityAntiSpamConstants.CHECKIN_WEIGHT));
        checkinMap.put("detail", getCheckinDetail(userId));
        detail.put("checkin", checkinMap);
        Map<String, Object> socialMap = new HashMap<>();
        socialMap.put("score", socialScore);
        socialMap.put("weight", ActivityAntiSpamConstants.SOCIAL_WEIGHT);
        socialMap.put("weightedScore", (int)(socialScore * ActivityAntiSpamConstants.SOCIAL_WEIGHT));
        socialMap.put("detail", getSocialDetail(userId));
        detail.put("social", socialMap);
        Map<String, Object> consumptionMap = new HashMap<>();
        consumptionMap.put("score", consumptionScore);
        consumptionMap.put("weight", ActivityAntiSpamConstants.CONSUMPTION_WEIGHT);
        consumptionMap.put("weightedScore", (int)(consumptionScore * ActivityAntiSpamConstants.CONSUMPTION_WEIGHT));
        consumptionMap.put("detail", getConsumptionDetail(userId));
        detail.put("consumption", consumptionMap);
        Map<String, Object> creationMap = new HashMap<>();
        creationMap.put("score", creationScore);
        creationMap.put("weight", ActivityAntiSpamConstants.CREATION_WEIGHT);
        creationMap.put("weightedScore", (int)(creationScore * ActivityAntiSpamConstants.CREATION_WEIGHT));
        creationMap.put("detail", getCreationDetail(userId));
        detail.put("creation", creationMap);
        Map<String, Object> contentMap = new HashMap<>();
        contentMap.put("score", contentScore);
        contentMap.put("isBonus", true);
        contentMap.put("bonusScore", (int)(contentScore * ActivityAntiSpamConstants.CONTENT_BONUS_WEIGHT));
        contentMap.put("detail", getContentDetail(userId));
        detail.put("content", contentMap);


        double creditWeight = getCreditWeight(userId);
        Map<String, Object> creditMap = new HashMap<>();
        creditMap.put("weight", creditWeight);
        creditMap.put("level", getCreditLevel(creditWeight));
        detail.put("credit", creditMap);
        detail.put("accountUnavailable", !canInteract);
        if (!canInteract) {
            detail.put("accountUnavailableMessage", UserAccountStatusUtil.currentUnavailableMessage(user));
        }


        int totalScore = canInteract ? getEnhancedActivityScore(userId) : 0;
        detail.put("totalScore", totalScore);
        detail.put("activityLevel", getActivityLevel(totalScore));

        return detail;
    }



    @Override
    public Integer getCheckinActivityScore(Long userId) {
        if (!canCalculateActivity(userId)) {
            return 0;
        }
        return calculateCheckinActivityScore(userId);
    }

    private int calculateCheckinActivityScore(Long userId) {
        int score = 0;


        int monthCheckinCount = getMonthCheckinCount(userId);
        score += Math.min(monthCheckinCount * ActivityAntiSpamConstants.CHECKIN_DAILY_SCORE, 50);


        int continuousDays = getContinuousDays(userId);
        score += Math.min(continuousDays * ActivityAntiSpamConstants.CHECKIN_CONTINUOUS_SCORE, 20);


        int totalPoints = getUserTotalPoints(userId);
        score += Math.min(totalPoints / 10, 20);


        int totalCheckinCount = getTotalCheckinCount(userId);
        score += Math.min(totalCheckinCount / 10, 10);

        return Math.min(score, 100);
    }

    @Override
    public Integer getSocialActivityScore(Long userId) {
        if (!canCalculateActivity(userId)) {
            return 0;
        }
        return calculateSocialActivityScore(userId);
    }

    private int calculateSocialActivityScore(Long userId) {
        int score = 0;

        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);


        long followingCount = getUserFollowingCount(userId);
        long followerCount = getUserFollowerCount(userId);
        score += Math.min((int)((followingCount + followerCount) / 2), 15);


        int monthCommentCount = getMonthCommentCount(userId);
        score += Math.min(monthCommentCount * ActivityAntiSpamConstants.SOCIAL_COMMENT_SCORE, 30);


        int monthLikeCount = getMonthLikeCount(userId);
        score += Math.min(monthLikeCount * ActivityAntiSpamConstants.SOCIAL_LIKE_SCORE, 15);


        int monthShareCount = getMonthShareCount(userId);
        score += Math.min(monthShareCount * ActivityAntiSpamConstants.SOCIAL_SHARE_SCORE, 15);


        int monthPostCount = getMonthPostCount(userId);
        score += Math.min(monthPostCount * ActivityAntiSpamConstants.SOCIAL_POST_SCORE, 25);

        return Math.min(score, 100);
    }

    @Override
    public Integer getConsumptionActivityScore(Long userId) {
        if (!canCalculateActivity(userId)) {
            return 0;
        }
        return calculateConsumptionActivityScore(userId);
    }

    private int calculateConsumptionActivityScore(Long userId) {
        int score = 0;

        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);


        int monthVipCount = getMonthVipPurchaseCount(userId, monthStart);
        score += monthVipCount * ActivityAntiSpamConstants.CONSUMPTION_VIP_SCORE;

        Map<String, Object> consumptionSummary = getMonthConsumptionSummary(userId, monthStart);


        int monthPaidCount = toInt(consumptionSummary.get("purchase_count"));
        score += monthPaidCount * ActivityAntiSpamConstants.CONSUMPTION_PAID_SCORE;


        int monthRewardCount = getMonthRewardCount(userId, monthStart);
        score += monthRewardCount * ActivityAntiSpamConstants.CONSUMPTION_REWARD_SCORE;


        BigDecimal monthAmount = toBigDecimal(consumptionSummary.get("total_amount"));
        score += monthAmount.intValue();

        return Math.min(score, 100);
    }

    @Override
    public Integer getCreationActivityScore(Long userId) {
        if (!canCalculateActivity(userId)) {
            return 0;
        }
        return calculateCreationActivityScore(userId);
    }

    private int calculateCreationActivityScore(Long userId) {
        int score = 0;

        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);


        int monthWorkCount = getMonthWorkSubmitCount(userId, monthStart);
        score += monthWorkCount * ActivityAntiSpamConstants.CREATION_WORK_SCORE;


        int monthLyricCount = getMonthLyricRequestCount(userId, monthStart);
        score += monthLyricCount * ActivityAntiSpamConstants.CREATION_LYRIC_SCORE;


        int monthRequestCount = getMonthResourceRequestCount(userId, monthStart);
        score += monthRequestCount * ActivityAntiSpamConstants.CREATION_REQUEST_SCORE;


        int monthApprovedCount = getMonthApprovedCount(userId, monthStart);
        score += monthApprovedCount * ActivityAntiSpamConstants.CREATION_APPROVED_SCORE;

        return Math.min(score, 100);
    }

    @Override
    public Integer getContentActivityScore(Long userId) {
        if (!canCalculateActivity(userId)) {
            return 0;
        }
        return calculateContentActivityScore(userId);
    }

    private int calculateContentActivityScore(Long userId) {
        int score = 0;

        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);


        int totalListenMinutes = getMonthListenMinutes(userId, monthStart);
        score += Math.min(totalListenMinutes / ActivityAntiSpamConstants.CONTENT_LISTEN_MINUTE_SCORE, 40);


        int completePlayCount = getMonthCompletePlayCount(userId, monthStart);
        score += Math.min(completePlayCount * ActivityAntiSpamConstants.CONTENT_COMPLETE_SCORE / 10, 30);


        int monthPlaylistCount = getMonthPlaylistCreateCount(userId, monthStart);
        score += monthPlaylistCount * ActivityAntiSpamConstants.CONTENT_PLAYLIST_SCORE;


        int monthMvWatchCount = getMonthMvWatchCount(userId, monthStart);
        score += Math.min(monthMvWatchCount * ActivityAntiSpamConstants.CONTENT_MV_SCORE, 30);

        return Math.min(score, 100);
    }



    @Override
    public Map<String, Object> checkBehaviorAbnormal(Long userId, String behaviorType) {
        Map<String, Object> result = new HashMap<>();
        boolean isAbnormal = false;
        String reason = "";
        double reduceRate = 1.0;


        Map<String, Object> rateLimit = checkRateLimit(userId, behaviorType);
        if (!(boolean) rateLimit.get("allowed")) {
            isAbnormal = true;
            reason = "频率过快，超过限制";
            reduceRate = 0.1;
        }


        if (!isAbnormal) {
            Map<String, Object> behaviorAnalysis = getUserBehaviorAnalysis(userId);
            Map<String, Integer> distribution = (Map<String, Integer>) behaviorAnalysis.get("behaviorDistribution");
            int totalCount = distribution.values().stream().mapToInt(Integer::intValue).sum();
            if (totalCount > 20) {
                int maxCount = distribution.values().stream().mapToInt(Integer::intValue).max().orElse(0);
                double singleRatio = (double) maxCount / totalCount;
                if (singleRatio > ActivityAntiSpamConstants.SUSPICIOUS_SINGLE_BEHAVIOR_RATIO) {
                    isAbnormal = true;
                    reason = "行为模式单一，主要进行单一操作";
                    reduceRate = 0.5;
                }
            }
        }


        User user = userMapper.selectById(userId);
        if (user != null && user.getCreateTime() != null) {
            long daysSinceRegister = ChronoUnit.DAYS.between(user.getCreateTime(), LocalDateTime.now());
            if (daysSinceRegister <= ActivityAntiSpamConstants.NEW_USER_PROTECT_DAYS) {
                reason += " [新用户保护期内]";
                reduceRate = Math.min(reduceRate, ActivityAntiSpamConstants.NEW_USER_WEIGHT_RATE);
            }
        }


        int currentHour = LocalDateTime.now().getHour();
        if (currentHour >= ActivityAntiSpamConstants.ABNORMAL_HOUR_START &&
            currentHour < ActivityAntiSpamConstants.ABNORMAL_HOUR_END) {
            reason += " [异常时段活跃]";
            reduceRate = Math.min(reduceRate, ActivityAntiSpamConstants.ABNORMAL_HOUR_WEIGHT);
        }


        double creditWeight = getCreditWeight(userId);
        reduceRate = reduceRate * creditWeight;

        result.put("isAbnormal", isAbnormal);
        result.put("reason", reason);
        result.put("reduceRate", reduceRate);
        result.put("checkTime", LocalDateTime.now());

        return result;
    }

    @Override
    public Double getCreditWeight(Long userId) {

        LambdaQueryWrapper<UserCredit> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCredit::getUserId, userId);
        UserCredit userCredit = userCreditMapper.selectOne(wrapper);

        int creditScore = (userCredit != null && userCredit.getCreditScore() != null)
                ? userCredit.getCreditScore()
                : 100;

        if (creditScore >= ActivityAntiSpamConstants.EXCELLENT_CREDIT_MIN_SCORE) {
            return ActivityAntiSpamConstants.EXCELLENT_CREDIT_WEIGHT;
        } else if (creditScore >= ActivityAntiSpamConstants.NORMAL_CREDIT_MIN_SCORE) {
            return ActivityAntiSpamConstants.NORMAL_CREDIT_WEIGHT;
        } else if (creditScore >= ActivityAntiSpamConstants.OBSERVE_CREDIT_MIN_SCORE) {
            return ActivityAntiSpamConstants.OBSERVE_CREDIT_WEIGHT;
        } else {
            return ActivityAntiSpamConstants.LOW_CREDIT_WEIGHT;
        }
    }

    @Override
    public Double calculateBehaviorScore(Long userId, String behaviorType, Double baseScore) {

        Map<String, Object> abnormalCheck = checkBehaviorAbnormal(userId, behaviorType);
        double reduceRate = (double) abnormalCheck.get("reduceRate");


        String recordKey = ActivityAntiSpamConstants.BEHAVIOR_RECORD_CACHE_KEY_PREFIX +
                          userId + ":" + behaviorType + ":" + LocalDate.now();
        Integer todayCount = (Integer) redisTemplate.opsForValue().get(recordKey);
        if (todayCount == null) {
            todayCount = 0;
        }

        double decayRate = 1.0;
        if (todayCount >= ActivityAntiSpamConstants.DECAY_START_COUNT) {
            decayRate = Math.max(
                    ActivityAntiSpamConstants.DECAY_MIN_RATE,
                    1.0 - (todayCount - ActivityAntiSpamConstants.DECAY_START_COUNT + 1) * ActivityAntiSpamConstants.DECAY_STEP
            );
        }


        double finalScore = baseScore * reduceRate * decayRate;


        redisTemplate.opsForValue().set(recordKey, todayCount + 1,
                ActivityAntiSpamConstants.BEHAVIOR_RECORD_CACHE_EXPIRE_DAYS, TimeUnit.DAYS);

        log.debug("行为得分计算: userId={}, type={}, baseScore={}, reduceRate={}, decayRate={}, finalScore={}",
                userId, behaviorType, baseScore, reduceRate, decayRate, finalScore);

        return finalScore;
    }

    @Override
    public Map<String, Object> checkRateLimit(Long userId, String behaviorType) {
        Map<String, Object> result = new HashMap<>();


        String recordKey = ActivityAntiSpamConstants.BEHAVIOR_RECORD_CACHE_KEY_PREFIX +
                          userId + ":" + behaviorType + ":" + LocalDate.now();
        Integer todayCount = (Integer) redisTemplate.opsForValue().get(recordKey);
        if (todayCount == null) {
            todayCount = 0;
        }


        int dailyLimit = getDailyLimit(behaviorType);


        String minuteKey = ActivityAntiSpamConstants.RATE_LIMIT_CACHE_KEY_PREFIX +
                          userId + ":" + behaviorType + ":" + System.currentTimeMillis() / 60000;
        Integer minuteCount = (Integer) redisTemplate.opsForValue().get(minuteKey);
        if (minuteCount == null) {
            minuteCount = 0;
        }
        int minuteLimit = getMinuteLimit(behaviorType);

        boolean allowed = todayCount < dailyLimit && minuteCount < minuteLimit;

        result.put("allowed", allowed);
        result.put("todayCount", todayCount);
        result.put("dailyLimit", dailyLimit);
        result.put("minuteCount", minuteCount);
        result.put("minuteLimit", minuteLimit);
        result.put("remaining", Math.max(0, dailyLimit - todayCount));
        result.put("resetTime", LocalDate.now().plusDays(1).atStartOfDay());

        if (allowed) {

            redisTemplate.opsForValue().set(minuteKey, minuteCount + 1, 1, TimeUnit.MINUTES);
        }

        return result;
    }



    @Override
    public Map<String, Object> getUserBehaviorAnalysis(Long userId) {
        Map<String, Object> analysis = new HashMap<>();

        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);


        Map<String, Integer> behaviorDistribution = new HashMap<>();
        behaviorDistribution.put("like", getMonthLikeCount(userId));
        behaviorDistribution.put("comment", getMonthCommentCount(userId));
        behaviorDistribution.put("share", getMonthShareCount(userId));
        behaviorDistribution.put("post", getMonthPostCount(userId));
        behaviorDistribution.put("follow", (int) getUserFollowingCount(userId));


        Map<Integer, Integer> hourDistribution = getActiveHourDistribution(userId, monthStart);


        boolean hasAbnormalPattern = checkAbnormalPattern(behaviorDistribution, hourDistribution);


        List<String> suggestions = generateSuggestions(behaviorDistribution, hourDistribution);
        int activityScore = getEnhancedActivityScore(userId);
        double creditWeight = getCreditWeight(userId);
        Map<String, Object> status = buildBehaviorStatus(userId, activityScore, creditWeight,
                hasAbnormalPattern, behaviorDistribution, hourDistribution);

        analysis.put("userId", userId);
        analysis.put("behaviorDistribution", behaviorDistribution);
        analysis.put("hourDistribution", hourDistribution);
        analysis.put("hasAbnormalPattern", hasAbnormalPattern);
        analysis.put("activityScore", activityScore);
        analysis.put("activityLevel", getActivityLevel(activityScore));
        analysis.put("creditWeight", creditWeight);
        analysis.put("creditLevel", getCreditLevel(creditWeight));
        analysis.put("onlineStatus", status.get("onlineStatus"));
        analysis.put("activityStatus", status.get("activityStatus"));
        analysis.put("riskStatus", status.get("riskStatus"));
        analysis.put("status", status);
        analysis.put("suggestions", suggestions);
        analysis.put("analysisTime", LocalDateTime.now());
        return analysis;
    }

    @Override
    public Boolean markSuspiciousUser(Long userId, String reason) {
        try {
            String key = ActivityAntiSpamConstants.SUSPICIOUS_USER_CACHE_KEY_PREFIX + userId;
            Map<String, Object> suspiciousMark = new HashMap<>();
        suspiciousMark.put("marked", true);
        suspiciousMark.put("reason", reason);
        suspiciousMark.put("markTime", LocalDateTime.now());
            redisTemplate.opsForValue().set(key, suspiciousMark, 7, TimeUnit.DAYS);

            log.warn("标记可疑用户: userId={}, reason={}", userId, reason);
            return true;
        } catch (Exception e) {
            log.error("标记可疑用户失败: userId={}", userId);
            return false;
        }
    }

    @Override
    public Boolean unmarkSuspiciousUser(Long userId) {
        try {
            String key = ActivityAntiSpamConstants.SUSPICIOUS_USER_CACHE_KEY_PREFIX + userId;
            redisTemplate.delete(key);

            log.info("取消可疑用户标记: userId={}", userId);
            return true;
        } catch (Exception e) {
            log.error("取消可疑用户标记失败: userId={}", userId);
            return false;
        }
    }



    @Override
    public List<Map<String, Object>> getActivityRanking(String dimension, Integer limit) {
        int safeLimit = limit == null || limit <= 0 ? 50 : Math.min(limit, MAX_RANKING_LIMIT);
        String rankingDimension = ObjectUtils.isEmpty(dimension) ? "total" : dimension;
        String cacheKey = "activity:ranking:" + musicIntelligenceCacheService.rankingVersionSegment()
                + "v2:" + rankingDimension;


        List<Object> cachedRanking = redisTemplate.opsForList().range(cacheKey, 0, safeLimit - 1);
        if (cachedRanking != null && !cachedRanking.isEmpty()) {
            List<Map<String, Object>> cachedItems = cachedRanking.stream()
                    .filter(obj -> obj instanceof Map)
                    .map(obj -> (Map<String, Object>) obj)
                    .collect(Collectors.toList());
            Map<Long, User> usersById = loadRankedUsers(cachedItems);
            List<Map<String, Object>> filteredRanking = cachedItems.stream()
                    .filter(item -> {
                        Long cachedUserId = parseRankedUserId(item);
                        return cachedUserId != null
                                && UserAccountStatusUtil.canContributePublicStats(usersById.get(cachedUserId));
                    })
                    .limit(safeLimit)
                    .collect(Collectors.toList());
            assignRanks(filteredRanking);
            return filteredRanking;
        }

        List<User> users = userMapper.selectList(buildPublicRankingUserQuery());

        List<Map<String, Object>> ranking = users.stream()
                .map(user -> buildActivityRankingItem(user, getEnhancedActivityScore(user)))
                .sorted((left, right) -> Integer.compare(
                        ((Number) right.get("score")).intValue(),
                        ((Number) left.get("score")).intValue()))
                .limit(safeLimit)
                .collect(Collectors.toList());

        assignRanks(ranking);

        if (!ranking.isEmpty()) {
            redisTemplate.delete(cacheKey);
            redisTemplate.opsForList().rightPushAll(cacheKey, ranking.toArray());
            redisTemplate.expire(cacheKey, 30, TimeUnit.MINUTES);
        }

        return ranking;
    }

    @Override
    public Integer getUserRanking(Long userId, String dimension) {
        if (ObjectUtils.isEmpty(userId)) {
            return -1;
        }
        if (!UserAccountStatusUtil.canContributePublicStats(userId, userMapper::selectById)) {
            return -1;
        }

        String rankingDimension = ObjectUtils.isEmpty(dimension) ? "total" : dimension;
        String cacheKey = "activity:rank:" + musicIntelligenceCacheService.rankingVersionSegment()
                + "v2:" + rankingDimension + ":" + userId;

        Integer cachedRank = (Integer) redisTemplate.opsForValue().get(cacheKey);
        if (cachedRank != null) {
            return cachedRank;
        }

        int activityScore = getEnhancedActivityScore(userId);
        List<User> users = userMapper.selectList(buildPublicRankingUserQuery());

        int rank = 1;
        for (User user : users) {
            if (user.getId() == null || user.getId().equals(userId)) {
                continue;
            }
            if (getEnhancedActivityScore(user) > activityScore) {
                rank++;
            }
        }

        redisTemplate.opsForValue().set(cacheKey, rank, 15, TimeUnit.MINUTES);
        return rank;
    }

    private void assignRanks(List<Map<String, Object>> ranking) {
        for (int i = 0; i < ranking.size(); i++) {
            ranking.get(i).put("rank", i + 1);
        }
    }





    private Map<String, Object> buildActivityRankingItem(User user, Integer score) {
        Map<String, Object> item = new HashMap<>();
        int activityScore = score == null ? 0 : score;
        item.put("userId", user.getId());
        item.put("score", activityScore);
        item.put("level", getActivityLevel(activityScore));
        item.put("nickname", ObjectUtils.isNotEmpty(user.getNickname()) ? user.getNickname() : user.getUsername());
        item.put("avatar", ObjectUtils.isNotEmpty(user.getAvatar()) ? user.getAvatar() : "");
        return item;
    }






    private LambdaQueryWrapper<User> buildPublicRankingUserQuery() {
        return UserAccountStatusUtil.publicStatsUserQuery()
                .last("LIMIT " + RANKING_CANDIDATE_LIMIT);
    }







    private Map<Long, User> loadRankedUsers(List<Map<String, Object>> items) {
        Set<Long> userIds = items.stream()
                .map(this::parseRankedUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user, (left, right) -> left));
    }







    private Long parseRankedUserId(Map<String, Object> item) {
        Object rawUserId = item.get("userId");
        if (rawUserId == null) {
            return null;
        }
        try {
            return rawUserId instanceof Number
                    ? ((Number) rawUserId).longValue()
                    : Long.valueOf(String.valueOf(rawUserId));
        } catch (NumberFormatException e) {
            return null;
        }
    }


    @Override
    public Map<String, Object> getActivityRewards(Long userId) {
        Map<String, Object> rewards = new HashMap<>();

        int activityScore = getEnhancedActivityScore(userId);
        String activityLevel = getActivityLevel(activityScore);


        List<Map<String, String>> availableRewards = new ArrayList<>();

        switch (activityLevel) {
            case "super_active":
                Map<String, String> r1 = new HashMap<>();
                r1.put("type", "积分");
                r1.put("amount", "50");
                r1.put("name", "超级活跃奖励");
                availableRewards.add(r1);
                Map<String, String> r2 = new HashMap<>();
                r2.put("type", "装饰");
                r2.put("id", "super_active_badge");
                r2.put("name", "超级活跃徽章");
                availableRewards.add(r2);
                break;
            case "active":
                Map<String, String> r3 = new HashMap<>();
                r3.put("type", "积分");
                r3.put("amount", "25");
                r3.put("name", "活跃奖励");
                availableRewards.add(r3);
                break;
            case "normal":
                Map<String, String> r4 = new HashMap<>();
                r4.put("type", "积分");
                r4.put("amount", "10");
                r4.put("name", "普通活跃奖励");
                availableRewards.add(r4);
                break;
            default:
                break;
        }

        rewards.put("userId", userId);
        rewards.put("activityScore", activityScore);
        rewards.put("activityLevel", activityLevel);
        rewards.put("availableRewards", availableRewards);

        return rewards;
    }

    @Override
    public Map<String, Object> getActivityLevelProgress(Long userId) {
        Map<String, Object> progress = new HashMap<>();

        int activityScore = getEnhancedActivityScore(userId);
        String currentLevel = getActivityLevel(activityScore);

        String nextLevel = null;
        int nextScore = 0;
        int progressPercent = 0;

        switch (currentLevel) {
            case "inactive":
                nextLevel = "normal";
                nextScore = ActivityAntiSpamConstants.NORMAL_MIN_SCORE;
                progressPercent = (int)((activityScore / (double)nextScore) * 100);
                break;
            case "normal":
                nextLevel = "active";
                nextScore = ActivityAntiSpamConstants.ACTIVE_MIN_SCORE;
                progressPercent = (int)(((activityScore - ActivityAntiSpamConstants.NORMAL_MIN_SCORE) /
                        (double)(nextScore - ActivityAntiSpamConstants.NORMAL_MIN_SCORE)) * 100);
                break;
            case "active":
                nextLevel = "super_active";
                nextScore = ActivityAntiSpamConstants.SUPER_ACTIVE_MIN_SCORE;
                progressPercent = (int)(((activityScore - ActivityAntiSpamConstants.ACTIVE_MIN_SCORE) /
                        (double)(nextScore - ActivityAntiSpamConstants.ACTIVE_MIN_SCORE)) * 100);
                break;
            case "super_active":
                nextLevel = null;
                nextScore = activityScore;
                progressPercent = 100;
                break;
        }

        progress.put("userId", userId);
        progress.put("currentLevel", currentLevel);
        progress.put("currentScore", activityScore);
        progress.put("nextLevel", nextLevel);
        progress.put("nextScore", nextScore);
        progress.put("progressPercent", progressPercent);

        return progress;
    }



    private boolean canCalculateActivity(Long userId) {
        return UserAccountStatusUtil.canInteract(userId, userMapper::selectById);
    }

    private String getActivityLevel(int score) {
        if (score >= ActivityAntiSpamConstants.SUPER_ACTIVE_MIN_SCORE) {
            return "super_active";
        } else if (score >= ActivityAntiSpamConstants.ACTIVE_MIN_SCORE) {
            return "active";
        } else if (score >= ActivityAntiSpamConstants.NORMAL_MIN_SCORE) {
            return "normal";
        } else {
            return "inactive";
        }
    }

    private String getCreditLevel(double weight) {
        if (weight >= ActivityAntiSpamConstants.EXCELLENT_CREDIT_WEIGHT) {
            return "excellent";
        } else if (weight >= ActivityAntiSpamConstants.NORMAL_CREDIT_WEIGHT) {
            return "normal";
        } else if (weight >= ActivityAntiSpamConstants.OBSERVE_CREDIT_WEIGHT) {
            return "observe";
        } else {
            return "low";
        }
    }

    private int getDailyLimit(String behaviorType) {
        switch (behaviorType) {
            case ActivityAntiSpamConstants.BEHAVIOR_TYPE_LIKE:
                return ActivityAntiSpamConstants.DAILY_LIKE_LIMIT;
            case ActivityAntiSpamConstants.BEHAVIOR_TYPE_COMMENT:
                return ActivityAntiSpamConstants.DAILY_COMMENT_LIMIT;
            case ActivityAntiSpamConstants.BEHAVIOR_TYPE_SHARE:
                return ActivityAntiSpamConstants.DAILY_SHARE_LIMIT;
            case ActivityAntiSpamConstants.BEHAVIOR_TYPE_POST:
                return ActivityAntiSpamConstants.DAILY_POST_LIMIT;
            case ActivityAntiSpamConstants.BEHAVIOR_TYPE_FOLLOW:
                return ActivityAntiSpamConstants.DAILY_FOLLOW_LIMIT;
            default:
                return 100;
        }
    }

    private int getMinuteLimit(String behaviorType) {
        switch (behaviorType) {
            case ActivityAntiSpamConstants.BEHAVIOR_TYPE_LIKE:
                return ActivityAntiSpamConstants.LIKE_RATE_LIMIT_PER_MINUTE;
            case ActivityAntiSpamConstants.BEHAVIOR_TYPE_COMMENT:
                return ActivityAntiSpamConstants.COMMENT_RATE_LIMIT_PER_MINUTE;
            case ActivityAntiSpamConstants.BEHAVIOR_TYPE_SHARE:
                return ActivityAntiSpamConstants.SHARE_RATE_LIMIT_PER_MINUTE;
            case ActivityAntiSpamConstants.BEHAVIOR_TYPE_POST:
                return ActivityAntiSpamConstants.POST_RATE_LIMIT_PER_MINUTE;
            case ActivityAntiSpamConstants.BEHAVIOR_TYPE_FOLLOW:
                return ActivityAntiSpamConstants.FOLLOW_RATE_LIMIT_PER_MINUTE;
            default:
                return 10;
        }
    }


    private int getMonthCheckinCount(Long userId) {
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        LambdaQueryWrapper<UserCheckin> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCheckin::getUserId, userId)
                .ge(UserCheckin::getCheckinDate, monthStart);
        return Math.toIntExact(userCheckinMapper.selectCount(wrapper));
    }

    private int getContinuousDays(Long userId) {
        LocalDate today = LocalDate.now();
        LambdaQueryWrapper<UserCheckin> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCheckin::getUserId, userId)
                .orderByDesc(UserCheckin::getCheckinDate)
                .last("LIMIT 1");
        UserCheckin lastCheckin = userCheckinMapper.selectOne(wrapper);
        if (lastCheckin == null) return 0;

        LocalDate lastDate = lastCheckin.getCheckinDate();
        long daysBetween = ChronoUnit.DAYS.between(lastDate, today);
        if (daysBetween > 1) return 0;

        return lastCheckin.getContinuousDays() != null ? lastCheckin.getContinuousDays() : 0;
    }

    private int getTotalCheckinCount(Long userId) {
        LambdaQueryWrapper<UserCheckin> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCheckin::getUserId, userId);
        return Math.toIntExact(userCheckinMapper.selectCount(wrapper));
    }

    private int getUserTotalPoints(Long userId) {
        Integer points = userActivityPointsMapper.getUserTotalPoints(userId);
        return points == null ? 0 : points;
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
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Comment::getUserId, userId)
                .ge(Comment::getCreateTime, monthStart);
        return Math.toIntExact(commentMapper.selectCount(wrapper));
    }

    private int getMonthLikeCount(Long userId) {
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        LambdaQueryWrapper<SongLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SongLike::getUserId, userId)
                .eq(SongLike::getIsLike, 1)
                .ge(SongLike::getCreateTime, monthStart);
        return Math.toIntExact(songLikeMapper.selectCount(wrapper));
    }

    private int getMonthShareCount(Long userId) {
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        LambdaQueryWrapper<ShareRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ShareRecord::getUserId, userId)
                .ge(ShareRecord::getCreateTime, monthStart);
        return Math.toIntExact(shareRecordMapper.selectCount(wrapper));
    }

    private int getMonthPostCount(Long userId) {
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        LambdaQueryWrapper<MusicPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MusicPost::getUserId, userId)
                .eq(MusicPost::getIsDeleted, false)
                .ge(MusicPost::getCreateTime, monthStart);
        return Math.toIntExact(musicPostMapper.selectCount(wrapper));
    }


    private int getMonthVipPurchaseCount(Long userId, LocalDateTime monthStart) {
        LambdaQueryWrapper<VipOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VipOrder::getUserId, userId)
                .eq(VipOrder::getStatus, "active")
                .ge(VipOrder::getCreateTime, monthStart);
        return Math.toIntExact(vipOrderMapper.selectCount(wrapper));
    }

    private int getMonthRewardCount(Long userId, LocalDateTime monthStart) {

        return 0;
    }

    private Map<String, Object> getMonthConsumptionSummary(Long userId, LocalDateTime monthStart) {
        QueryWrapper<PaymentOrder> wrapper = new QueryWrapper<>();
        wrapper.select(
                    "SUM(CASE WHEN business_type = 'purchase' THEN 1 ELSE 0 END) AS purchase_count",
                    "COALESCE(SUM(amount), 0) AS total_amount")
                .eq("user_id", userId)
                .ge("create_time", monthStart)
                .lt("create_time", monthStart.plusMonths(1));
        PaymentOrderStatusUtil.applyPaidOrderFilter(wrapper);

        List<Map<String, Object>> rows = paymentOrderMapper.selectMaps(wrapper);
        return rows.isEmpty() ? Collections.emptyMap() : rows.get(0);
    }

    private int toInt(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }
        return BigDecimal.ZERO;
    }


    private int getMonthWorkSubmitCount(Long userId, LocalDateTime monthStart) {
        LambdaQueryWrapper<CreatorWork> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CreatorWork::getUserId, userId)
                .ge(CreatorWork::getCreateTime, monthStart);
        return Math.toIntExact(creatorWorkMapper.selectCount(wrapper));
    }

    private int getMonthLyricRequestCount(Long userId, LocalDateTime monthStart) {
        LambdaQueryWrapper<LyricRequest> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LyricRequest::getUserId, userId)
                .ge(LyricRequest::getCreateTime, monthStart);
        return Math.toIntExact(lyricRequestMapper.selectCount(wrapper));
    }

    private int getMonthResourceRequestCount(Long userId, LocalDateTime monthStart) {
        LambdaQueryWrapper<SongResourceRequest> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SongResourceRequest::getUserId, userId)
                .ge(SongResourceRequest::getCreateTime, monthStart);
        return Math.toIntExact(songResourceRequestMapper.selectCount(wrapper));
    }

    private int getMonthApprovedCount(Long userId, LocalDateTime monthStart) {
        LambdaQueryWrapper<CreatorWork> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CreatorWork::getUserId, userId)
                .eq(CreatorWork::getStatus, 1)       
                .ge(CreatorWork::getReviewTime, monthStart);
        return Math.toIntExact(creatorWorkMapper.selectCount(wrapper));
    }


    private int getMonthListenMinutes(Long userId, LocalDateTime monthStart) {
        LambdaQueryWrapper<ListenHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ListenHistory::getUserId, userId)
                .ge(ListenHistory::getCreateTime, monthStart);
        List<ListenHistory> histories = listenHistoryMapper.selectList(wrapper);
        return histories.stream()
                .filter(h -> h.getDuration() != null)
                .mapToInt(ListenHistory::getDuration)
                .sum() / 60;         
    }

    private int getMonthCompletePlayCount(Long userId, LocalDateTime monthStart) {
        LambdaQueryWrapper<ListenHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ListenHistory::getUserId, userId)
                .eq(ListenHistory::getIsCompleted, 1)
                .ge(ListenHistory::getCreateTime, monthStart);
        return Math.toIntExact(listenHistoryMapper.selectCount(wrapper));
    }

    private int getMonthPlaylistCreateCount(Long userId, LocalDateTime monthStart) {
        LambdaQueryWrapper<Playlist> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Playlist::getUserId, userId)
                .eq(Playlist::getDeleted, 0)
                .ge(Playlist::getCreateTime, monthStart);
        return Math.toIntExact(playlistMapper.selectCount(wrapper));
    }

    private int getMonthMvWatchCount(Long userId, LocalDateTime monthStart) {

        return 0;
    }


    private Map<String, Object> getCheckinDetail(Long userId) {
        Map<String, Object> detail = new HashMap<>();
        detail.put("monthCheckinCount", getMonthCheckinCount(userId));
        detail.put("continuousDays", getContinuousDays(userId));
        detail.put("totalCheckinCount", getTotalCheckinCount(userId));
        detail.put("totalPoints", getUserTotalPoints(userId));
        return detail;
    }

    private Map<String, Object> getSocialDetail(Long userId) {
        Map<String, Object> detail = new HashMap<>();
        detail.put("followingCount", getUserFollowingCount(userId));
        detail.put("followerCount", getUserFollowerCount(userId));
        detail.put("monthCommentCount", getMonthCommentCount(userId));
        detail.put("monthLikeCount", getMonthLikeCount(userId));
        detail.put("monthShareCount", getMonthShareCount(userId));
        detail.put("monthPostCount", getMonthPostCount(userId));
        return detail;
    }

    private Map<String, Object> getConsumptionDetail(Long userId) {
        Map<String, Object> detail = new HashMap<>();
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        Map<String, Object> consumptionSummary = getMonthConsumptionSummary(userId, monthStart);
        detail.put("monthVipCount", getMonthVipPurchaseCount(userId, monthStart));
        detail.put("monthPaidCount", toInt(consumptionSummary.get("purchase_count")));
        detail.put("monthRewardCount", getMonthRewardCount(userId, monthStart));
        detail.put("monthAmount", toBigDecimal(consumptionSummary.get("total_amount")));
        return detail;
    }

    private Map<String, Object> getCreationDetail(Long userId) {
        Map<String, Object> detail = new HashMap<>();
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        detail.put("monthWorkCount", getMonthWorkSubmitCount(userId, monthStart));
        detail.put("monthLyricCount", getMonthLyricRequestCount(userId, monthStart));
        detail.put("monthRequestCount", getMonthResourceRequestCount(userId, monthStart));
        detail.put("monthApprovedCount", getMonthApprovedCount(userId, monthStart));
        return detail;
    }

    private Map<String, Object> getContentDetail(Long userId) {
        Map<String, Object> detail = new HashMap<>();
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        detail.put("monthListenMinutes", getMonthListenMinutes(userId, monthStart));
        detail.put("completePlayCount", getMonthCompletePlayCount(userId, monthStart));
        detail.put("monthPlaylistCount", getMonthPlaylistCreateCount(userId, monthStart));
        detail.put("monthMvWatchCount", getMonthMvWatchCount(userId, monthStart));
        return detail;
    }


    private Map<Integer, Integer> getActiveHourDistribution(Long userId, LocalDateTime monthStart) {

        Map<Integer, Integer> distribution = new HashMap<>();
        for (int i = 0; i < 24; i++) {
            distribution.put(i, 0);
        }


        LambdaQueryWrapper<ListenHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ListenHistory::getUserId, userId)
               .ge(ListenHistory::getCreateTime, monthStart);

        List<ListenHistory> histories = listenHistoryMapper.selectList(wrapper);

        for (ListenHistory history : histories) {
            if (history.getCreateTime() != null) {
                int hour = history.getCreateTime().getHour();
                distribution.merge(hour, 1, Integer::sum);
            }
        }

        return distribution;
    }
    private boolean checkAbnormalPattern(Map<String, Integer> behaviorDistribution,
                                         Map<Integer, Integer> hourDistribution) {

        int totalBehavior = behaviorDistribution.values().stream().mapToInt(Integer::intValue).sum();
        if (totalBehavior > 20) {
            int maxBehavior = behaviorDistribution.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            double singleRatio = (double) maxBehavior / totalBehavior;
            if (singleRatio > ActivityAntiSpamConstants.SUSPICIOUS_SINGLE_BEHAVIOR_RATIO) {
                return true;
            }
        }


        int activeHours = 0;
        for (Map.Entry<Integer, Integer> entry : hourDistribution.entrySet()) {
            if (entry.getValue() > 0) {
                activeHours++;
            }
        }
        if (activeHours >= ActivityAntiSpamConstants.CONTINUOUS_ACTIVE_HOURS_LIMIT) {
            return true;
        }

        return false;
    }

    private List<String> generateSuggestions(Map<String, Integer> behaviorDistribution,
                                            Map<Integer, Integer> hourDistribution) {
        List<String> suggestions = new ArrayList<>();


        int totalBehavior = behaviorDistribution.values().stream().mapToInt(Integer::intValue).sum();
        if (totalBehavior == 0) {
            suggestions.add("您还没有任何社交行为，试着去评论、点赞或分享吧！");
        } else {
            int maxBehavior = behaviorDistribution.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getValue)
                    .orElse(0);
            if (maxBehavior > totalBehavior * 0.7) {
                suggestions.add("您的行为模式较为单一，尝试更多样化的互动可以获得更好的体验！");
            }
        }


        int activeHours = (int) hourDistribution.values().stream().filter(v -> v > 0).count();
        if (activeHours < 5) {
            suggestions.add("您的活跃时段较为集中，分散在不同时段活跃会获得更多奖励！");
        }

        return suggestions;
    }



    private Map<String, Object> buildBehaviorStatus(Long userId,
                                                    int activityScore,
                                                    double creditWeight,
                                                    boolean hasAbnormalPattern,
                                                    Map<String, Integer> behaviorDistribution,
                                                    Map<Integer, Integer> hourDistribution) {
        Map<String, Object> status = new HashMap<>(onlineStatusService.getUserOnlineStatus(userId));
        status.put("activityLevel", getActivityLevel(activityScore));
        status.put("creditLevel", getCreditLevel(creditWeight));
        status.put("riskStatus", resolveBehaviorRiskStatus(userId, creditWeight, hasAbnormalPattern,
                behaviorDistribution, hourDistribution));
        status.put("activeHourCount", hourDistribution.values().stream().filter(v -> v != null && v > 0).count());
        status.put("behaviorTotal", behaviorDistribution.values().stream().filter(Objects::nonNull)
                .mapToInt(Integer::intValue).sum());
        return status;
    }

    private String resolveBehaviorRiskStatus(Long userId,
                                             double creditWeight,
                                             boolean hasAbnormalPattern,
                                             Map<String, Integer> behaviorDistribution,
                                             Map<Integer, Integer> hourDistribution) {
        if (userId != null && Boolean.TRUE.equals(redisTemplate.hasKey(ActivityAntiSpamConstants.SUSPICIOUS_USER_CACHE_KEY_PREFIX + userId))) {
            return "marked_suspicious";
        }
        if (hasAbnormalPattern) {
            return "abnormal";
        }
        if (creditWeight < ActivityAntiSpamConstants.OBSERVE_CREDIT_WEIGHT) {
            return "low_credit";
        }
        if (creditWeight < ActivityAntiSpamConstants.NORMAL_CREDIT_WEIGHT) {
            return "observe";
        }

        int totalBehavior = behaviorDistribution.values().stream()
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        long activeHours = hourDistribution.values().stream()
                .filter(v -> v != null && v > 0)
                .count();
        if (totalBehavior == 0 && activeHours == 0) {
            return "no_data";
        }
        return "normal";
    }

    private Map<String, Object> getEnhancedActivityInfo(Long userId) {
        Map<String, Object> result = new HashMap<>();
        int activityScore = getEnhancedActivityScore(userId);
        result.put("activityScore", activityScore);
        result.put("activityLevel", getActivityLevel(activityScore));
        return result;
    }
}
