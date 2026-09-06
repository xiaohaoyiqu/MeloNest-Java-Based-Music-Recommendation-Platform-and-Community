package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.haoran.music.common.config.UserGrowthConfig;
import com.haoran.music.common.context.UserContext;
import com.haoran.music.common.constant.RedisConstants;
import com.haoran.music.common.enums.UserType;
import com.haoran.music.common.util.AdminAccountOperationGuard;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.User;
import com.haoran.music.entity.UserStatistics;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.mapper.UserStatisticsMapper;
import com.haoran.music.service.MusicIntelligenceCacheService;
import com.haoran.music.service.SimpleUserClassificationService;
import com.haoran.music.service.UserSessionRevocationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;





@Slf4j
@Service
public class SimpleUserClassificationServiceImpl implements SimpleUserClassificationService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserStatisticsMapper userStatisticsMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private UserGrowthConfig userGrowthConfig;

    @Autowired
    private MusicIntelligenceCacheService musicIntelligenceCacheService;

    @Autowired(required = false)
    private UserSessionRevocationService userSessionRevocationService;




    private static final String USER_TYPE_CACHE_PREFIX = "user:type:";




    private static final String STATS_PREFIX = "user:stats:";
    private static final long REDIS_SCAN_COUNT = 1000L;

    @Override
    public UserType getUserType(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return UserType.NORMAL;
        }

        String cacheKey = USER_TYPE_CACHE_PREFIX + userId;
        Integer cachedType = toInteger(redisTemplate.opsForValue().get(cacheKey));
        if (cachedType != null) {
            return UserType.fromCode(cachedType);
        }

        User user = userMapper.selectAccountAccessStateById(userId);
        if (ObjectUtils.isEmpty(user)) {
            return UserType.NORMAL;
        }

        UserType userType = UserType.fromCode(user.getUserType());
        redisTemplate.opsForValue().set(cacheKey, userType.getCode(), 1, TimeUnit.HOURS);
        return userType;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserType(Long userId, UserType userType, String reason) {
        if (ObjectUtils.isEmpty(userId)) {
            return;
        }
        UserType targetType = userType == null ? UserType.NORMAL : userType;
        User user = userMapper.selectById(userId);
        if (ObjectUtils.isEmpty(user)) {
            return;
        }
        UserType previousType = UserType.fromCode(user.getUserType());
        if (previousType.shouldRestrict() && isAutomaticClassificationReason(reason)) {
            log.warn("跳过自动分类覆盖限制用户类型: userId={}, previousType={}, targetType={}, reason={}",
                    userId, previousType, targetType, reason);
            return;
        }
        if (shouldSkipPrivilegedTypeUpdate(user, targetType, reason)) {
            return;
        }

        LambdaUpdateWrapper<User> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(User::getId, userId)
                .set(User::getUserType, targetType.getCode())
                .set(User::getUserTypeUpdateTime, LocalDateTime.now());
        userMapper.update(null, updateWrapper);

        String cacheKey = USER_TYPE_CACHE_PREFIX + userId;
        redisTemplate.opsForValue().set(cacheKey, targetType.getCode(), 1, TimeUnit.HOURS);

        if (previousType.getCode() != targetType.getCode()) {
            bumpCandidateCacheVersion("user type changed:" + userId + ":" + targetType.getCode());
        }

        log.info("用户类型已更新: userId={}, userType={}, reason={}", userId, targetType, reason);

        if (targetType.shouldRestrict()) {
            forceLogout(userId);
        }
    }

    private void bumpCandidateCacheVersion(String reason) {
        try {
            musicIntelligenceCacheService.bumpCandidateCacheVersion(reason, UserContext.getCurrentUserId());
            musicIntelligenceCacheService.bumpRecommendCacheVersion(reason, UserContext.getCurrentUserId());
            musicIntelligenceCacheService.bumpRankingCacheVersion(reason, UserContext.getCurrentUserId());
        } catch (Exception e) {
            log.warn("用户分类变更后更新音乐智能缓存版本失败，不阻断用户分类: reason={}, error={}", reason, e.getClass().getSimpleName());
        }
    }

    @Override
    public void dailyUserClassification() {
        log.info("开始执行每日用户分类任务");

        LocalDate yesterday = LocalDate.now().minusDays(1);
        List<UserStatistics> allStats = userStatisticsMapper.selectByStatDate(yesterday);

        int normalCount = 0, activeCount = 0, inactiveCount = 0, botCount = 0;

        for (UserStatistics stat : allStats) {
            try {
                UserType userType = determineUserType(stat);
                updateUserType(stat.getUserId(), userType, "每日自动分类");

                switch (userType) {
                    case NORMAL:
                        normalCount++;
                        break;
                    case ACTIVE:
                        activeCount++;
                        break;
                    case INACTIVE:
                        inactiveCount++;
                        break;
                    case BOT:
                        botCount++;
                        break;
                    default:
                        break;
                }
            } catch (Exception e) {
                log.error("处理用户分类失败: userId={}, error={}", stat.getUserId(), e.getClass().getSimpleName());
            }
        }

        log.info("每日用户分类完成: 总数={}, 正常={}, 活跃={}, 不活跃={}, 机器人={}",
                allStats.size(), normalCount, activeCount, inactiveCount, botCount);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer checkBotUsers() {
        LocalDate today = LocalDate.now();
        int updatedCount = 0;

        List<Map<String, Object>> botUserList = userStatisticsMapper.selectBotUsers(
                today, today,
                userGrowthConfig.getStatistics().getBotMaxDailyPlayCount(),
                userGrowthConfig.getStatistics().getBotMaxDailyDurationSeconds());
        for (Map<String, Object> bot : botUserList) {
            Long botUserId = toLong(bot.get("user_id"));
            if (markBotUser(botUserId, "统计表触发机器人阈值")) {
                updatedCount++;
            }
        }

        updatedCount += checkRedisBotUsers(today);
        log.info("机器人用户检查完成: updatedCount={}", updatedCount);
        return updatedCount;
    }

    @Override
    public Boolean isBotUser(Long userId) {
        return isBotUser(userId, 1);
    }




    private Boolean isBotUser(Long userId, Integer days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days == null || days <= 0 ? 1 : days);

        List<Map<String, Object>> botUserList = userStatisticsMapper.selectBotUsers(
                startDate, endDate,
                userGrowthConfig.getStatistics().getBotMaxDailyPlayCount(),
                userGrowthConfig.getStatistics().getBotMaxDailyDurationSeconds());

        for (Map<String, Object> bot : botUserList) {
            Long botUserId = toLong(bot.get("user_id"));
            if (botUserId != null && botUserId.equals(userId)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public Boolean isRestricted(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return false;
        }
        User user = userMapper.selectAccountAccessStateById(userId);
        return !UserAccountStatusUtil.canAuthenticate(user);
    }

    @Override
    public Boolean isFrozen(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return false;
        }
        return UserAccountStatusUtil.isFrozen(userMapper.selectAccountAccessStateById(userId));
    }

    @Override
    public void forceLogout(Long userId) {
        if (userSessionRevocationService != null) {
            userSessionRevocationService.revokeWebSocketSessions(userId, "account_restricted");
        }


        String tokenKey = RedisConstants.TOKEN_PREFIX + userId;
        redisTemplate.delete(tokenKey);

        log.info("event=user_session_revoked userId={} reason=account_restricted", userId);
    }

    @Override
    public void recordUserPlay(Long userId, Integer duration) {
        if (ObjectUtils.isEmpty(userId)) {
            return;
        }

        LocalDate today = LocalDate.now();
        String redisKey = STATS_PREFIX + today + ":" + userId;

        Long playCount = redisTemplate.opsForHash().increment(redisKey, "playCount", 1);
        Long playDuration = redisTemplate.opsForHash().increment(redisKey, "playDuration", ObjectUtils.isNotEmpty(duration) ? duration : 0);
        redisTemplate.expire(redisKey, userGrowthConfig.getStatistics().getCacheDays(), TimeUnit.DAYS);

        int currentPlayCount = toInteger(playCount);
        int currentPlayDuration = toInteger(playDuration);

        if (isBotThresholdReached(currentPlayCount, currentPlayDuration)) {
            markAsAbnormal(userId, today, buildAbnormalReason(currentPlayCount, currentPlayDuration));
            updateUserType(userId, UserType.BOT, "播放行为触发机器人阈值");
            log.warn("用户触发机器人阈值: userId={}, playCount={}, playDuration={}", userId, currentPlayCount, currentPlayDuration);
        }
    }

    @Override
    public void recordUserLogin(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return;
        }

        LocalDate today = LocalDate.now();
        String redisKey = STATS_PREFIX + today + ":" + userId;
        redisTemplate.opsForHash().increment(redisKey, "loginCount", 1);
        redisTemplate.expire(redisKey, userGrowthConfig.getStatistics().getCacheDays(), TimeUnit.DAYS);

        updateLastActiveTime(userId);
    }

    @Override
    public void recordUserSearch(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return;
        }

        LocalDate today = LocalDate.now();
        String redisKey = STATS_PREFIX + today + ":" + userId;
        redisTemplate.opsForHash().increment(redisKey, "searchCount", 1);
        redisTemplate.expire(redisKey, userGrowthConfig.getStatistics().getCacheDays(), TimeUnit.DAYS);
    }

    @Override
    public void recordUserInteraction(Long userId, String type) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(type)) {
            return;
        }

        LocalDate today = LocalDate.now();
        String redisKey = STATS_PREFIX + today + ":" + userId;
        redisTemplate.opsForHash().increment(redisKey, type, 1);
        redisTemplate.expire(redisKey, userGrowthConfig.getStatistics().getCacheDays(), TimeUnit.DAYS);
    }




    private UserType determineUserType(UserStatistics stat) {
        if (ObjectUtils.isEmpty(stat)) {
            return UserType.NORMAL;
        }
        if (isBotThresholdReached(stat.getPlayCount(), stat.getPlayDuration())) {
            return UserType.BOT;
        }
        if (ObjectUtils.isNotEmpty(stat.getPlayDuration())
                && stat.getPlayDuration() >= userGrowthConfig.getStatistics().getClassificationActivePlayDurationSeconds()) {
            return UserType.ACTIVE;
        }
        if (ObjectUtils.isNotEmpty(stat.getUniqueSongCount())
                && stat.getUniqueSongCount() >= userGrowthConfig.getStatistics().getClassificationActiveUniqueSongCount()) {
            return UserType.ACTIVE;
        }

        return UserType.NORMAL;
    }





    private int checkRedisBotUsers(LocalDate statDate) {
        int updatedCount = 0;
        String pattern = STATS_PREFIX + statDate + ":*";
        Set<String> keys = scanKeys(pattern);
        if (keys == null || keys.isEmpty()) {
            return 0;
        }

        for (String key : keys) {
            if (key.endsWith(":songs")) {
                continue;
            }
            Long userId = parseUserIdFromStatsKey(key);
            if (userId == null) {
                continue;
            }
            int playCount = toInteger(redisTemplate.opsForHash().get(key, "playCount"));
            int playDuration = toInteger(redisTemplate.opsForHash().get(key, "playDuration"));
            if (isBotThresholdReached(playCount, playDuration)) {
                markAsAbnormal(userId, statDate, buildAbnormalReason(playCount, playDuration));
                if (markBotUser(userId, "Redis实时统计触发机器人阈值")) {
                    updatedCount++;
                }
            }
        }
        return updatedCount;
    }

    private boolean markBotUser(Long userId, String reason) {
        if (userId == null) {
            return false;
        }
        if (getUserType(userId) == UserType.BOT) {
            return false;
        }
        updateUserType(userId, UserType.BOT, reason);
        return true;
    }

    private boolean shouldSkipPrivilegedTypeUpdate(User target, UserType targetType, String reason) {
        if (!AdminAccountOperationGuard.isPrivilegedAccount(target)) {
            return false;
        }
        User operator = getCurrentOperatorOrNull();
        if (operator == null || target.getId().equals(operator.getId())) {
            log.warn("跳过后台账号类型自动更新: userId={}, targetType={}, reason={}",
                    target.getId(), targetType, reason);
            return true;
        }
        AdminAccountOperationGuard.requireCanOperateAccount(operator, target, "更新用户类型");
        return false;
    }

    private boolean isAutomaticClassificationReason(String reason) {
        return reason != null && (reason.startsWith("每日自动分类") || reason.contains("机器人阈值"));
    }

    private User getCurrentOperatorOrNull() {
        Long operatorId = UserContext.getCurrentUserId();
        if (operatorId == null) {
            return null;
        }
        return userMapper.selectById(operatorId);
    }




    private void markAsAbnormal(Long userId, LocalDate statDate, String abnormalReason) {
        UserStatistics existStat = userStatisticsMapper.selectOne(
                new LambdaQueryWrapper<UserStatistics>()
                        .eq(UserStatistics::getUserId, userId)
                        .eq(UserStatistics::getStatDate, statDate)
                        .eq(UserStatistics::getDeleted, 0)
        );

        if (ObjectUtils.isEmpty(existStat)) {
            existStat = new UserStatistics();
            existStat.setUserId(userId);
            existStat.setStatDate(statDate);
            existStat.setDeleted(0);
        }

        String redisKey = STATS_PREFIX + statDate + ":" + userId;
        existStat.setPlayCount(Math.max(safeInt(existStat.getPlayCount()), toInteger(redisTemplate.opsForHash().get(redisKey, "playCount"))));
        existStat.setPlayDuration(Math.max(safeInt(existStat.getPlayDuration()), toInteger(redisTemplate.opsForHash().get(redisKey, "playDuration"))));
        existStat.setIsAbnormal(1);
        existStat.setAbnormalReason(abnormalReason);
        existStat.setRiskScore(calculateRiskScore(existStat));

        if (existStat.getId() != null && existStat.getId() > 0) {
            userStatisticsMapper.updateById(existStat);
        } else {
            userStatisticsMapper.insert(existStat);
        }
    }




    private String buildAbnormalReason(Integer playCount, Integer playDuration) {
        StringBuilder reason = new StringBuilder("{");
        if (ObjectUtils.isNotEmpty(playCount) && playCount >= userGrowthConfig.getStatistics().getBotMaxDailyPlayCount()) {
            reason.append("\"highPlayCount\":\"播放次数超过").append(userGrowthConfig.getStatistics().getBotMaxDailyPlayCount()).append("\"");
        }
        if (ObjectUtils.isNotEmpty(playDuration) && playDuration >= userGrowthConfig.getStatistics().getBotMaxDailyDurationSeconds()) {
            if (reason.length() > 1) {
                reason.append(",");
            }
            reason.append("\"highDuration\":\"播放时长超过").append(userGrowthConfig.getStatistics().getBotMaxDailyDurationSeconds() / 3600).append("小时\"");
        }
        reason.append("}");
        return reason.toString();
    }




    private Integer calculateRiskScore(UserStatistics stat) {
        if (ObjectUtils.isEmpty(stat)) {
            return 0;
        }

        int score = 0;
        if (ObjectUtils.isNotEmpty(stat.getPlayCount())) {
            if (stat.getPlayCount() >= userGrowthConfig.getStatistics().getBotMaxDailyPlayCount()) {
                score += 50;
            } else if (stat.getPlayCount() >= userGrowthConfig.getStatistics().getRiskPlayCountWarnThreshold()) {
                score += 30;
            }
        }
        if (ObjectUtils.isNotEmpty(stat.getPlayDuration())) {
            if (stat.getPlayDuration() >= userGrowthConfig.getStatistics().getBotMaxDailyDurationSeconds()) {
                score += 50;
            } else if (stat.getPlayDuration() >= userGrowthConfig.getStatistics().getRiskDurationWarnSeconds()) {
                score += 30;
            }
        }

        return Math.min(score, 100);
    }




    private void updateLastActiveTime(Long userId) {
        LambdaUpdateWrapper<User> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(User::getId, userId)
                .set(User::getLastActiveTime, LocalDateTime.now());

        userMapper.update(null, updateWrapper);
        log.debug("更新用户最后活跃时间: userId={}", userId);
    }

    private boolean isBotThresholdReached(Integer playCount, Integer playDuration) {
        return safeInt(playCount) >= userGrowthConfig.getStatistics().getBotMaxDailyPlayCount()
                || safeInt(playDuration) >= userGrowthConfig.getStatistics().getBotMaxDailyDurationSeconds();
    }

    private Set<String> scanKeys(String pattern) {
        Set<String> keys = new HashSet<>();
        try (Cursor<String> cursor = redisTemplate.scan(ScanOptions.scanOptions()
                .match(pattern)
                .count(REDIS_SCAN_COUNT)
                .build())) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        } catch (Exception e) {
            log.warn("Scan Redis user statistics failed: pattern={}, error={}", pattern, e.getClass().getSimpleName());
        }
        return keys;
    }
    private Long parseUserIdFromStatsKey(String key) {
        if (ObjectUtils.isEmpty(key) || key.endsWith(":songs")) {
            return null;
        }
        int lastIndex = key.lastIndexOf(':');
        if (lastIndex < 0 || lastIndex + 1 >= key.length()) {
            return null;
        }
        try {
            return Long.parseLong(key.substring(lastIndex + 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
