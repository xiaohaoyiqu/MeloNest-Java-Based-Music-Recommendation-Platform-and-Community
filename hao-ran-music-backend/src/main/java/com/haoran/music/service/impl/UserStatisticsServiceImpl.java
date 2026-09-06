package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.config.UserGrowthConfig;
import com.haoran.music.common.enums.UserType;
import com.haoran.music.service.SimpleUserClassificationService;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.User;
import com.haoran.music.entity.UserStatistics;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.mapper.UserStatisticsMapper;
import com.haoran.music.service.UserStatisticsService;
import com.haoran.music.service.helper.UserRiskScorePolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;





@Slf4j
@Service
public class UserStatisticsServiceImpl extends ServiceImpl<UserStatisticsMapper, UserStatistics>
        implements UserStatisticsService {

    private static final String STATS_PREFIX = "user:stats:";
    private static final long REDIS_SCAN_COUNT = 1000L;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserGrowthConfig userGrowthConfig;

    @Autowired
    private SimpleUserClassificationService userClassificationService;

    @Autowired
    private UserRiskScorePolicy userRiskScorePolicy;


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordPlay(Long userId, Long songId, Integer duration, Boolean complete) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(songId)) {
            return;
        }
        if (!UserAccountStatusUtil.canContributePublicStats(userId, userMapper::selectById)) {
            log.debug("跳过非公共统计账号播放统计: userId={}, songId={}", userId, songId);
            return;
        }

        LocalDate today = LocalDate.now();
        String redisKey = STATS_PREFIX + today + ":" + userId;

        Long playCount = redisTemplate.opsForHash().increment(redisKey, "playCount", 1);
        Long playDuration = redisTemplate.opsForHash().increment(redisKey, "playDuration", ObjectUtils.isNotEmpty(duration) ? duration : 0);
        if (ObjectUtils.isNotEmpty(complete) && complete) {
            redisTemplate.opsForHash().increment(redisKey, "completePlayCount", 1);
        }

        String songSetKey = redisKey + ":songs";
        redisTemplate.opsForSet().add(songSetKey, songId);

        redisTemplate.expire(redisKey, userGrowthConfig.getStatistics().getCacheDays(), TimeUnit.DAYS);
        redisTemplate.expire(songSetKey, userGrowthConfig.getStatistics().getCacheDays(), TimeUnit.DAYS);

        int currentPlayCount = toInteger(playCount);
        int currentPlayDuration = toInteger(playDuration);
        if (userRiskScorePolicy.isBotThresholdReached(currentPlayCount, currentPlayDuration)) {
            markAsAbnormal(userId, today, userRiskScorePolicy.buildAbnormalReason(currentPlayCount, currentPlayDuration));
            userClassificationService.updateUserType(userId, UserType.BOT, "Playback behavior triggered bot threshold");
            log.warn("用户触发机器人阈值: userId={}, playCount={}, playDuration={}", userId, currentPlayCount, currentPlayDuration);
        }
    }







    @Override
    public void recordLogin(Long userId, String ip) {
        if (ObjectUtils.isEmpty(userId)) {
            return;
        }
        if (!UserAccountStatusUtil.canInteract(userMapper.selectById(userId))) {
            log.debug("跳过非正常账号登录统计: userId={}", userId);
            return;
        }

        LocalDate today = LocalDate.now();
        String redisKey = STATS_PREFIX + today + ":" + userId;
        redisTemplate.opsForHash().increment(redisKey, "loginCount", 1);
        if (ObjectUtils.isNotEmpty(ip)) {
            redisTemplate.opsForHash().put(redisKey, "ipAddress", ip);
        }
        redisTemplate.expire(redisKey, userGrowthConfig.getStatistics().getCacheDays(), TimeUnit.DAYS);

        User user = new User();
        user.setId(userId);
        LocalDateTime now = LocalDateTime.now();
        user.setLastLoginTime(now);
        user.setLastActiveTime(now);
        if (ObjectUtils.isNotEmpty(ip)) {
            user.setLastLoginIp(ip);
        }
        userMapper.updateById(user);

        log.debug("记录用户登录: userId={}, ip={}", userId, ip);
    }






    @Override
    public void recordSearch(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return;
        }
        if (!UserAccountStatusUtil.canContributePublicStats(userId, userMapper::selectById)) {
            log.debug("跳过非公共统计账号搜索统计: userId={}", userId);
            return;
        }

        LocalDate today = LocalDate.now();
        String redisKey = STATS_PREFIX + today + ":" + userId;
        redisTemplate.opsForHash().increment(redisKey, "searchCount", 1);
        redisTemplate.expire(redisKey, userGrowthConfig.getStatistics().getCacheDays(), TimeUnit.DAYS);

        log.debug("记录用户搜索: userId={}", userId);
    }








    @Override
    public void incrementInteraction(Long userId, String type, Integer increment) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(type)) {
            return;
        }
        if (!UserAccountStatusUtil.canContributePublicStats(userId, userMapper::selectById)) {
            log.debug("跳过非公共统计账号互动统计: userId={}, type={}", userId, type);
            return;
        }

        LocalDate today = LocalDate.now();
        String redisKey = STATS_PREFIX + today + ":" + userId;
        redisTemplate.opsForHash().increment(redisKey, type, ObjectUtils.isNotEmpty(increment) ? increment : 1);
        redisTemplate.expire(redisKey, userGrowthConfig.getStatistics().getCacheDays(), TimeUnit.DAYS);

        log.debug("记录用户互动: userId={}, type={}, increment={}", userId, type, increment);
    }

    @Override
    public UserStatistics getStatsByDate(Long userId, LocalDate statDate) {
        LambdaQueryWrapper<UserStatistics> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserStatistics::getUserId, userId)
                .eq(UserStatistics::getStatDate, statDate)
                .eq(UserStatistics::getDeleted, 0);
        return getOne(wrapper);
    }

    @Override
    public List<UserStatistics> getStatsByDateRange(Long userId, LocalDate startDate, LocalDate endDate) {
        return baseMapper.selectByDateRange(userId, startDate, endDate);
    }

    @Override
    public Map<String, Object> getUserTotalStats(Long userId, LocalDate startDate, LocalDate endDate) {
        return baseMapper.selectUserTotalStats(userId, startDate, endDate);
    }

    @Override
    public List<UserStatistics> getAllStatsByDate(LocalDate statDate) {
        return baseMapper.selectByStatDate(statDate);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAsAbnormal(Long userId, LocalDate statDate, String abnormalReason) {
        UserStatistics stats = getOrCreateStats(userId, statDate);
        applyRedisStats(stats, STATS_PREFIX + statDate + ":" + userId);
        stats.setIsAbnormal(1);
        stats.setRiskScore(userRiskScorePolicy.calculateRiskScore(stats));
        stats.setAbnormalReason(ObjectUtils.isNotEmpty(abnormalReason) ? abnormalReason : userRiskScorePolicy.buildAbnormalReason(stats));
        normalizeStats(stats);
        saveOrUpdate(stats);
    }








    @Override
    public Boolean isBotUser(Long userId, Integer days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days == null || days <= 0 ? 1 : days);

        List<Map<String, Object>> botUsers = baseMapper.selectBotUsers(
                startDate, endDate,
                userRiskScorePolicy.getBotMaxDailyPlayCount(),
                userRiskScorePolicy.getBotMaxDailyDurationSeconds());

        for (Map<String, Object> bot : botUsers) {
            Long botUserId = toLong(bot.get("user_id"));
            if (botUserId != null && botUserId.equals(userId)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public Integer getActiveDaysCount(Long userId, Integer days) {
        LocalDate startDate = LocalDate.now().minusDays(days == null || days <= 0 ? 1 : days);
        Integer count = baseMapper.selectActiveDaysCount(userId, startDate);
        return count == null ? 0 : count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void dailyBatchCreateOrUpdate(LocalDate statDate) {
        LocalDate targetDate = statDate == null ? LocalDate.now().minusDays(1) : statDate;
        log.info("开始批量创建/更新用户统计数据: {}", targetDate);

        Map<Long, UserStatistics> mergedStats = new HashMap<>();
        mergeListenHistoryStats(mergedStats, targetDate);
        mergeRedisStats(mergedStats, targetDate);

        int savedCount = 0;
        for (UserStatistics stats : mergedStats.values()) {
            normalizeStats(stats);
            stats.setRiskScore(userRiskScorePolicy.calculateRiskScore(stats));
            if (userRiskScorePolicy.shouldMarkAbnormal(stats)) {
                stats.setIsAbnormal(1);
                stats.setAbnormalReason(userRiskScorePolicy.buildAbnormalReason(stats));
            }
            saveOrUpdate(stats);
            savedCount++;
        }

        log.info("批量创建/更新用户统计数据完成: statDate={}, savedCount={}", targetDate, savedCount);
    }





    private void mergeListenHistoryStats(Map<Long, UserStatistics> mergedStats, LocalDate statDate) {
        LocalDateTime start = statDate.atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        List<Map<String, Object>> rows = baseMapper.selectDailyListenStats(start, end);
        for (Map<String, Object> row : rows) {
            Long userId = toLong(row.get("user_id"));
            if (userId == null) {
                continue;
            }
            UserStatistics stats = getMergedStats(mergedStats, userId, statDate);
            stats.setUsername(toStringValue(row.get("username")));
            stats.setPlayCount(Math.max(safeInt(stats.getPlayCount()), toInteger(row.get("play_count"))));
            stats.setPlayDuration(Math.max(safeInt(stats.getPlayDuration()), toInteger(row.get("play_duration"))));
            stats.setUniqueSongCount(Math.max(safeInt(stats.getUniqueSongCount()), toInteger(row.get("unique_song_count"))));
            stats.setCompletePlayCount(Math.max(safeInt(stats.getCompletePlayCount()), toInteger(row.get("complete_play_count"))));
        }
    }





    private void mergeRedisStats(Map<Long, UserStatistics> mergedStats, LocalDate statDate) {
        String pattern = STATS_PREFIX + statDate + ":*";
        Set<String> keys = scanKeys(pattern);
        if (keys == null || keys.isEmpty()) {
            return;
        }

        for (String key : keys) {
            if (key.endsWith(":songs")) {
                continue;
            }
            Long userId = parseUserIdFromStatsKey(key);
            if (userId == null) {
                continue;
            }
            UserStatistics stats = getMergedStats(mergedStats, userId, statDate);
            applyRedisStats(stats, key);
        }
    }

    private void applyRedisStats(UserStatistics stats, String redisKey) {
        stats.setPlayCount(Math.max(safeInt(stats.getPlayCount()), toInteger(redisTemplate.opsForHash().get(redisKey, "playCount"))));
        stats.setPlayDuration(Math.max(safeInt(stats.getPlayDuration()), toInteger(redisTemplate.opsForHash().get(redisKey, "playDuration"))));
        stats.setCompletePlayCount(Math.max(safeInt(stats.getCompletePlayCount()), toInteger(redisTemplate.opsForHash().get(redisKey, "completePlayCount"))));
        stats.setLoginCount(Math.max(safeInt(stats.getLoginCount()), toInteger(redisTemplate.opsForHash().get(redisKey, "loginCount"))));
        stats.setSearchCount(Math.max(safeInt(stats.getSearchCount()), toInteger(redisTemplate.opsForHash().get(redisKey, "searchCount"))));
        stats.setLikeCount(Math.max(safeInt(stats.getLikeCount()), toInteger(redisTemplate.opsForHash().get(redisKey, "like"))));
        stats.setFavoriteCount(Math.max(safeInt(stats.getFavoriteCount()), toInteger(redisTemplate.opsForHash().get(redisKey, "favorite"))));
        stats.setUnfavoriteCount(Math.max(safeInt(stats.getUnfavoriteCount()), toInteger(redisTemplate.opsForHash().get(redisKey, "unfavorite"))));
        stats.setCommentCount(Math.max(safeInt(stats.getCommentCount()), toInteger(redisTemplate.opsForHash().get(redisKey, "comment"))));
        stats.setShareCount(Math.max(safeInt(stats.getShareCount()), toInteger(redisTemplate.opsForHash().get(redisKey, "share"))));
        stats.setDownloadCount(Math.max(safeInt(stats.getDownloadCount()), toInteger(redisTemplate.opsForHash().get(redisKey, "download"))));
        stats.setCreatePlaylistCount(Math.max(safeInt(stats.getCreatePlaylistCount()), toInteger(redisTemplate.opsForHash().get(redisKey, "createPlaylist"))));
        stats.setFollowArtistCount(Math.max(safeInt(stats.getFollowArtistCount()), toInteger(redisTemplate.opsForHash().get(redisKey, "followArtist"))));
        Object ipAddress = redisTemplate.opsForHash().get(redisKey, "ipAddress");
        if (ObjectUtils.isNotEmpty(ipAddress)) {
            stats.setIpAddress(toStringValue(ipAddress));
        }

        Long uniqueSongCount = redisTemplate.opsForSet().size(redisKey + ":songs");
        if (uniqueSongCount != null) {
            stats.setUniqueSongCount(Math.max(safeInt(stats.getUniqueSongCount()), uniqueSongCount.intValue()));
        }
    }

    private UserStatistics getMergedStats(Map<Long, UserStatistics> mergedStats, Long userId, LocalDate statDate) {
        UserStatistics stats = mergedStats.get(userId);
        if (stats != null) {
            return stats;
        }
        stats = getOrCreateStats(userId, statDate);
        mergedStats.put(userId, stats);
        return stats;
    }

    private UserStatistics getOrCreateStats(Long userId, LocalDate statDate) {
        LambdaQueryWrapper<UserStatistics> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserStatistics::getUserId, userId)
                .eq(UserStatistics::getStatDate, statDate)
                .eq(UserStatistics::getDeleted, 0);
        UserStatistics stats = getOne(wrapper);
        if (stats == null) {
            stats = new UserStatistics();
            stats.setUserId(userId);
            stats.setStatDate(statDate);
            stats.setDeleted(0);
            User user = userMapper.selectById(userId);
            if (user != null) {
                stats.setUsername(ObjectUtils.isNotEmpty(user.getUsername()) ? user.getUsername() : user.getNickname());
            }
        }
        return stats;
    }

    private void normalizeStats(UserStatistics stats) {
        stats.setPlayCount(safeInt(stats.getPlayCount()));
        stats.setPlayDuration(safeInt(stats.getPlayDuration()));
        stats.setUniqueSongCount(safeInt(stats.getUniqueSongCount()));
        stats.setCompletePlayCount(safeInt(stats.getCompletePlayCount()));
        stats.setLikeCount(safeInt(stats.getLikeCount()));
        stats.setFavoriteCount(safeInt(stats.getFavoriteCount()));
        stats.setUnfavoriteCount(safeInt(stats.getUnfavoriteCount()));
        stats.setCommentCount(safeInt(stats.getCommentCount()));
        stats.setShareCount(safeInt(stats.getShareCount()));
        stats.setDownloadCount(safeInt(stats.getDownloadCount()));
        stats.setActiveDuration(safeInt(stats.getActiveDuration()));
        stats.setLoginCount(safeInt(stats.getLoginCount()));
        stats.setSearchCount(safeInt(stats.getSearchCount()));
        stats.setCreatePlaylistCount(safeInt(stats.getCreatePlaylistCount()));
        stats.setFollowArtistCount(safeInt(stats.getFollowArtistCount()));
        stats.setIsAbnormal(safeInt(stats.getIsAbnormal()));
        stats.setRiskScore(safeInt(stats.getRiskScore()));
        stats.setDeleted(safeInt(stats.getDeleted()));
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
            log.warn("鎵弿Redis缁熻澶辫触: pattern={}, error={}", pattern, e.getClass().getSimpleName());
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

    private String toStringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
