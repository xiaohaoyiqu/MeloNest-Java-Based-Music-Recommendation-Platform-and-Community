package com.haoran.music.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.util.CacheHelper;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.RedisUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.*;
import com.haoran.music.mapper.*;
import com.haoran.music.service.UserProfileService;
import com.haoran.music.vo.user.UserProfileVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;





@Slf4j
@Service
public class UserProfileServiceImpl extends ServiceImpl<UserProfileMapper, UserProfile> implements UserProfileService {

    @Autowired
    private UserProfileMapper userProfileMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ListenHistoryMapper listenHistoryMapper;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private SongMapper songMapper;




    private static final String PROFILE_CACHE_PREFIX = "user:profile:";
    private static final String PREFERENCE_TAGS_PREFIX = "user:tags:";




    private static final int PROFILE_CACHE_TTL = 3600;




    private static final int PREFERENCE_TAGS_CACHE_TTL = 1800;
    private static final int PROFILE_REFRESH_BATCH_SIZE = 200;

    @Override
    public UserProfileVO getUserProfile(Long userId) {
        String cacheKey = PROFILE_CACHE_PREFIX + userId;

        return CacheHelper.getOrLoad(
                redisUtils,
                cacheKey,
                () -> {
                    UserProfile profile = userProfileMapper.selectOne(
                            new LambdaQueryWrapper<UserProfile>()
                                    .eq(UserProfile::getUserId, userId)
                    );

                    if (ObjectUtils.isEmpty(profile)) {
                        profile = createNewProfile(userId, false);
                    }

                    return convertToVO(profile);
                },
                PROFILE_CACHE_TTL,
                TimeUnit.SECONDS,
                UserProfileVO.class
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserPreferences(Long userId, List<String> preferredGenres,
                                      List<String> preferredLanguages, List<String> preferredMoods) {
        validatePreferenceValues(preferredGenres, "音乐流派");
        validatePreferenceValues(preferredLanguages, "语言");
        validatePreferenceValues(preferredMoods, "情绪");

        UserProfile profile = userProfileMapper.selectOne(
                new LambdaQueryWrapper<UserProfile>()
                        .eq(UserProfile::getUserId, userId)
        );

        if (ObjectUtils.isEmpty(profile)) {
            profile = createNewProfile(userId, true);
        }

        if (ObjectUtils.isNotEmpty(preferredGenres)) {
            profile.setPreferredGenres(JSON.toJSONString(preferredGenres));
        }
        if (ObjectUtils.isNotEmpty(preferredLanguages)) {
            profile.setPreferredLanguages(JSON.toJSONString(preferredLanguages));
        }
        if (ObjectUtils.isNotEmpty(preferredMoods)) {
            profile.setPreferredMoods(JSON.toJSONString(preferredMoods));
        }

        profile.setUpdateTime(LocalDateTime.now());

        if (ObjectUtils.isNotEmpty(profile.getId())) {
            userProfileMapper.updateById(profile);
        } else {
            userProfileMapper.insert(profile);
        }


        CacheHelper.delete(redisUtils, PROFILE_CACHE_PREFIX + userId);
        CacheHelper.delete(redisUtils, PREFERENCE_TAGS_PREFIX + userId);

        log.info("更新用户偏好: userId={}, genreCount={}, languageCount={}, moodCount={}",
                userId, sizeOf(preferredGenres), sizeOf(preferredLanguages), sizeOf(preferredMoods));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refreshUserProfile(Long userId) {
        log.info("开始刷新用户画像: userId={}", userId);

        UserProfile profile = userProfileMapper.selectOne(
                new LambdaQueryWrapper<UserProfile>()
                        .eq(UserProfile::getUserId, userId)
        );

        if (ObjectUtils.isEmpty(profile)) {
            profile = createNewProfile(userId, true);
        }


        refreshMusicPreferences(profile);


        refreshBehaviorFeatures(profile);


        refreshLifecycleData(profile);


        calculateUserSegment(profile);


        Integer churnProb = predictChurnProbability(userId);
        profile.setChurnProbability(churnProb);

        profile.setUpdateTime(LocalDateTime.now());

        if (ObjectUtils.isNotEmpty(profile.getId())) {
            userProfileMapper.updateById(profile);
        } else {
            userProfileMapper.insert(profile);
        }


        CacheHelper.delete(redisUtils, PROFILE_CACHE_PREFIX + userId);
        CacheHelper.delete(redisUtils, PREFERENCE_TAGS_PREFIX + userId);

        log.info("用户画像刷新完成: userId={}, segment={}, churnProb={}",
                userId, profile.getUserSegment(), churnProb);
    }

    @Override
    public void batchRefreshUserProfiles() {
        log.info("开始批量刷新用户画像");

        LocalDateTime threshold = LocalDateTime.now().minusDays(30);
        int successCount = 0;
        int failCount = 0;
        long lastUserId = 0L;

        while (true) {
            List<Long> userIds = userMapper.selectRecentlyActiveUserIdsAfter(
                    threshold, lastUserId, PROFILE_REFRESH_BATCH_SIZE);
            if (ObjectUtils.isEmpty(userIds)) {
                break;
            }

            for (Long userId : userIds) {
                try {
                    refreshUserProfile(userId);
                    successCount++;
                } catch (Exception e) {
                    log.error("刷新用户画像失败: userId={}", userId);
                    failCount++;
                }
            }
            lastUserId = userIds.get(userIds.size() - 1);
        }

        log.info("批量刷新用户画像完成: 成功={}, 失败={}", successCount, failCount);
    }

    @Override
    public Map<String, Object> getUserPreferenceTags(Long userId) {
        String cacheKey = PREFERENCE_TAGS_PREFIX + userId;

        return CacheHelper.getOrLoad(
                redisUtils,
                cacheKey,
                () -> {
                    UserProfileVO profile = getUserProfile(userId);
                    Map<String, Object> result = new HashMap<>();


                    result.put("preferredGenres", profile.getPreferredGenres());
                    result.put("preferredLanguages", profile.getPreferredLanguages());
                    result.put("preferredArtists", profile.getPreferredArtists());


                    result.put("peakActiveHour", profile.getPeakActiveHour());
                    result.put("avgDailyDuration", profile.getAvgDailyDurationHours());


                    result.put("userSegment", profile.getUserSegment());
                    result.put("lifecycleStage", profile.getLifecycleStage());

                    return result;
                },
                PREFERENCE_TAGS_CACHE_TTL,
                TimeUnit.SECONDS,
                Map.class
        );
    }

    @Override
    public void recordUserBehavior(Long userId, String action, Long targetId, String metadata) {
        if (!UserAccountStatusUtil.canInteract(userMapper.selectById(userId))) {
            log.debug("跳过非正常账号画像行为: userId={}, action={}", userId, action);
            return;
        }
        Set<String> supportedActions = new HashSet<>(Arrays.asList(
                "play", "like", "favorite", "comment", "share", "search", "follow", "download", "skip"));
        String normalizedAction = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        if (!supportedActions.contains(normalizedAction)) {
            throw new IllegalArgumentException("不支持的用户行为类型");
        }
        if (targetId == null || targetId <= 0) {
            throw new IllegalArgumentException("行为目标ID不合法");
        }

        LocalDate today = LocalDate.now();
        String behaviorKey = "user:behavior:" + today + ":" + userId;


        redisUtils.hIncrBy(behaviorKey, normalizedAction + "_count", 1);


        String targetKey = behaviorKey + ":" + normalizedAction;
        redisUtils.sAdd(targetKey, String.valueOf(targetId));


        redisUtils.expire(behaviorKey, 7, TimeUnit.DAYS);
        redisUtils.expire(targetKey, 7, TimeUnit.DAYS);

        log.debug("记录用户行为: userId={}, action={}, targetId={}", userId, action, targetId);
    }

    @Override
    public Map<String, Object> getUserSegmentStats() {
        Map<String, Long> segmentCount = new HashMap<>();
        segmentCount.put("NEW", 0L);
        segmentCount.put("ACTIVE", 0L);
        segmentCount.put("RETENTION", 0L);
        segmentCount.put("CHURN", 0L);
        segmentCount.put("RESURRECTED", 0L);

        long totalUsers = 0L;
        long publicUsers = 0L;
        for (Map<String, Object> row : userProfileMapper.selectSegmentStats()) {
            String segment = row.get("segment") == null ? "UNKNOWN" : String.valueOf(row.get("segment"));
            long totalCount = numberValue(row.get("total_count"));
            long publicCount = numberValue(row.get("public_count"));
            segmentCount.put(segment, segmentCount.getOrDefault(segment, 0L) + totalCount);
            totalUsers += totalCount;
            publicUsers += publicCount;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalUsers", totalUsers);
        result.put("publicUsers", publicUsers);
        result.put("restrictedUsers", Math.max(0L, totalUsers - publicUsers));
        result.put("segmentStats", segmentCount);

        return result;
    }

    private long numberValue(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    @Override
    public Integer predictChurnProbability(Long userId) {
        UserProfile profile = userProfileMapper.selectOne(
                new LambdaQueryWrapper<UserProfile>()
                        .eq(UserProfile::getUserId, userId)
        );

        if (ObjectUtils.isEmpty(profile)) {
            return 0;
        }

        int churnScore = 0;


        if (ObjectUtils.isNotEmpty(profile.getLastActiveDate())) {
            long daysSinceLastActive = LocalDate.now().toEpochDay() - profile.getLastActiveDate().toEpochDay();
            if (daysSinceLastActive > 30) {
                churnScore += 40;
            } else if (daysSinceLastActive > 14) {
                churnScore += 20;
            } else if (daysSinceLastActive > 7) {
                churnScore += 10;
            }
        }


        if (ObjectUtils.isNotEmpty(profile.getTotalActiveDays())) {

            LocalDate weekAgo = LocalDate.now().minusDays(7);
            long recentActiveDays = listenHistoryMapper.selectList(
                    new LambdaQueryWrapper<ListenHistory>()
                            .eq(ListenHistory::getUserId, userId)
                            .ge(ListenHistory::getCreateTime, weekAgo.atStartOfDay(java.time.ZoneId.systemDefault()))
            ).stream()
                    .map(h -> h.getCreateTime().toLocalDate())
                    .distinct()
                    .count();

            if (recentActiveDays == 0) {
                churnScore += 30;
            } else if (recentActiveDays <= 2) {
                churnScore += 15;
            }
        }


        if (ObjectUtils.isNotEmpty(profile.getAvgDailyDuration())) {
            if (profile.getAvgDailyDuration() < 300) {         
                churnScore += 30;
            } else if (profile.getAvgDailyDuration() < 900) {          
                churnScore += 15;
            }
        }

        return Math.min(churnScore, 100);
    }






    private UserProfile createNewProfile(Long userId, boolean persist) {
        User user = userMapper.selectById(userId);
        if (ObjectUtils.isEmpty(user)) {
            return null;
        }

        UserProfile profile = new UserProfile();
        profile.setUserId(userId);
        profile.setRegisterDate(user.getCreateTime().toLocalDate());
        profile.setFirstActiveDate(LocalDate.now());
        profile.setLastActiveDate(LocalDate.now());
        profile.setTotalActiveDays(1);
        profile.setTotalPlayDuration(0);
        profile.setTotalPlayCount(0);
        profile.setUserSegment("NEW");
        profile.setLifecycleStage("AWARENESS");
        profile.setLtvScore(10);
        profile.setChurnProbability(0);
        profile.setCreateTime(LocalDateTime.now());
        profile.setUpdateTime(LocalDateTime.now());

        if (persist) {
            userProfileMapper.insert(profile);
        }
        return profile;
    }

    private void validatePreferenceValues(List<String> values, String fieldName) {
        if (values == null) {
            return;
        }
        if (values.size() > 20) {
            throw new IllegalArgumentException(fieldName + "最多20项");
        }
        Set<String> uniqueValues = new HashSet<>();
        for (String value : values) {
            String normalized = value == null ? "" : value.trim();
            if (normalized.isEmpty() || normalized.length() > 50 || normalized.matches(".*[\\p{Cntrl}].*")) {
                throw new IllegalArgumentException(fieldName + "包含不合法内容");
            }
            if (!uniqueValues.add(normalized.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException(fieldName + "不能重复");
            }
        }
    }

    private int sizeOf(List<?> values) {
        return values == null ? 0 : values.size();
    }




    private void refreshMusicPreferences(UserProfile profile) {
        Long userId = profile.getUserId();

        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
        List<ListenHistory> recentHistory = listenHistoryMapper.selectList(
                new LambdaQueryWrapper<ListenHistory>()
                        .eq(ListenHistory::getUserId, userId)
                        .ge(ListenHistory::getCreateTime, thirtyDaysAgo.atStartOfDay(java.time.ZoneId.systemDefault()))
        );

        if (ObjectUtils.isEmpty(recentHistory)) {
            return;
        }

        Set<Long> songIds = recentHistory.stream()
                .map(ListenHistory::getSongId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (songIds.isEmpty()) {
            return;
        }

        Map<Long, Song> songMap = songMapper.selectBatchIds(songIds).stream()
                .collect(Collectors.toMap(Song::getId, song -> song, (left, right) -> left));

        Map<String, Integer> genreCount = new HashMap<>();
        Map<String, Integer> languageCount = new HashMap<>();
        Map<String, Integer> artistCount = new HashMap<>();
        for (ListenHistory history : recentHistory) {
            Song song = songMap.get(history.getSongId());
            if (ObjectUtils.isEmpty(song)) {
                continue;
            }
            String genre = ObjectUtils.isEmpty(song.getMainGenre()) ? song.getMainType() : song.getMainGenre();
            if (ObjectUtils.isNotEmpty(genre)) {
                genreCount.put(genre, genreCount.getOrDefault(genre, 0) + 1);
            }
            if (ObjectUtils.isNotEmpty(song.getLanguage())) {
                languageCount.put(song.getLanguage(), languageCount.getOrDefault(song.getLanguage(), 0) + 1);
            }
            if (ObjectUtils.isNotEmpty(song.getArtistNames())) {
                artistCount.put(song.getArtistNames(), artistCount.getOrDefault(song.getArtistNames(), 0) + 1);
            }
        }

        List<String> topGenres = topKeys(genreCount, 5);
        List<String> topLanguages = topKeys(languageCount, 3);
        List<String> topArtists = topKeys(artistCount, 5);

        if (ObjectUtils.isNotEmpty(topGenres)) {
            profile.setPreferredGenres(JSON.toJSONString(topGenres));
        }
        if (ObjectUtils.isNotEmpty(topLanguages)) {
            profile.setPreferredLanguages(JSON.toJSONString(topLanguages));
        }
        if (ObjectUtils.isNotEmpty(topArtists)) {
            profile.setPreferredArtists(JSON.toJSONString(topArtists));
        }
    }



    private void refreshBehaviorFeatures(UserProfile profile) {
        Long userId = profile.getUserId();


        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
        List<ListenHistory> recentHistory = listenHistoryMapper.selectList(
                new LambdaQueryWrapper<ListenHistory>()
                        .eq(ListenHistory::getUserId, userId)
                        .ge(ListenHistory::getCreateTime, thirtyDaysAgo.atStartOfDay(java.time.ZoneId.systemDefault()))
        );

        if (ObjectUtils.isNotEmpty(recentHistory)) {
            int totalDuration = recentHistory.stream()
                    .mapToInt(h -> ObjectUtils.isNotEmpty(h.getDuration()) ? h.getDuration() : 0)
                    .sum();
            profile.setAvgDailyDuration(totalDuration / 30);


            Map<Integer, Integer> hourCount = new HashMap<>();
            for (ListenHistory history : recentHistory) {
                int hour = history.getCreateTime().getHour();
                hourCount.put(hour, hourCount.getOrDefault(hour, 0) + 1);
            }

            Integer peakHour = hourCount.entrySet().stream()
                    .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(20);          
            profile.setPeakActiveHour(peakHour);
        }
    }




    private void refreshLifecycleData(UserProfile profile) {
        Long userId = profile.getUserId();
        User user = userMapper.selectById(userId);

        List<ListenHistory> allHistory = listenHistoryMapper.selectList(
                new LambdaQueryWrapper<ListenHistory>()
                        .eq(ListenHistory::getUserId, userId)
        );

        profile.setTotalPlayCount(allHistory.size());
        int totalDuration = allHistory.stream()
                .mapToInt(h -> ObjectUtils.isNotEmpty(h.getDuration()) ? h.getDuration() : 0)
                .sum();
        profile.setTotalPlayDuration(totalDuration);

        List<LocalDate> activeDates = allHistory.stream()
                .map(ListenHistory::getCreateTime)
                .filter(Objects::nonNull)
                .map(LocalDateTime::toLocalDate)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        if (!activeDates.isEmpty()) {
            profile.setFirstActiveDate(activeDates.get(0));
            profile.setLastActiveDate(activeDates.get(activeDates.size() - 1));
            profile.setTotalActiveDays(activeDates.size());
            return;
        }

        LocalDate fallbackActiveDate = resolveUserLastActiveDate(user);
        if (profile.getFirstActiveDate() == null) {
            profile.setFirstActiveDate(fallbackActiveDate);
        }
        profile.setLastActiveDate(fallbackActiveDate);
        profile.setTotalActiveDays(fallbackActiveDate == null ? 0 : 1);
    }
    private LocalDate resolveUserLastActiveDate(User user) {
        if (user == null) {
            return null;
        }
        if (user.getLastActiveTime() != null) {
            return user.getLastActiveTime().toLocalDate();
        }
        if (user.getLastOnlineTime() != null) {
            return user.getLastOnlineTime().toLocalDate();
        }
        if (user.getLastLoginTime() != null) {
            return user.getLastLoginTime().toLocalDate();
        }
        return user.getCreateTime() == null ? null : user.getCreateTime().toLocalDate();
    }

    private List<String> topKeys(Map<String, Integer> source, int limit) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        return source.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }



    private void calculateUserSegment(UserProfile profile) {
        LocalDate now = LocalDate.now();
        long daysSinceRegister = 0;
        if (ObjectUtils.isNotEmpty(profile.getRegisterDate())) {
            daysSinceRegister = now.toEpochDay() - profile.getRegisterDate().toEpochDay();
        }

        long daysSinceLastActive = 0;
        if (ObjectUtils.isNotEmpty(profile.getLastActiveDate())) {
            daysSinceLastActive = now.toEpochDay() - profile.getLastActiveDate().toEpochDay();
        }


        if (daysSinceRegister <= 7) {
            profile.setUserSegment("NEW");
            profile.setLifecycleStage("AWARENESS");
        }

        else if (daysSinceLastActive > 30) {
            profile.setUserSegment("CHURN");
            profile.setLifecycleStage("CHURN");
            profile.setChurnDate(now);
        }

        else if (daysSinceLastActive <= 7 && profile.getTotalActiveDays() > 30) {
            profile.setUserSegment("RETENTION");
            profile.setLifecycleStage("RETENTION");
        }

        else {
            profile.setUserSegment("ACTIVE");
            profile.setLifecycleStage("CONSIDER");
        }


        int ltvScore = 10;
        if (profile.getTotalActiveDays() != null) {
            ltvScore += Math.min(profile.getTotalActiveDays(), 50);
        }
        if (profile.getTotalPlayDuration() != null) {
            ltvScore += Math.min(profile.getTotalPlayDuration() / 3600, 30);
        }
        profile.setLtvScore(Math.min(ltvScore, 100));
    }




    private UserProfileVO convertToVO(UserProfile profile) {
        UserProfileVO vo = new UserProfileVO();
        BeanUtils.copyProperties(profile, vo);


        vo.setPreferredGenres(parseJsonList(profile.getPreferredGenres()));
        vo.setPreferredLanguages(parseJsonList(profile.getPreferredLanguages()));
        vo.setPreferredArtists(parseJsonList(profile.getPreferredArtists()));


        if (profile.getAvgDailyDuration() != null) {
            vo.setAvgDailyDurationHours(profile.getAvgDailyDuration() / 3600.0);
        }
        if (profile.getTotalPlayDuration() != null) {
            vo.setTotalPlayDurationHours(profile.getTotalPlayDuration() / 3600.0);
        }

        return vo;
    }




    private List<String> parseJsonList(String jsonStr) {
        if (ObjectUtils.isEmpty(jsonStr)) {
            return new ArrayList<>();
        }
        try {
            return JSON.parseArray(jsonStr, String.class);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }






    private Song getSongById(Long songId) {
        if (ObjectUtils.isEmpty(songId)) {
            return null;
        }
        return songMapper.selectById(songId);
    }
}
