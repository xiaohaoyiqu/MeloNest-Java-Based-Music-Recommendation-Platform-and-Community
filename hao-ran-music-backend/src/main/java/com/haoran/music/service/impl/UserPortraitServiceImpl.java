package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.*;
import com.haoran.music.mapper.*;
import com.haoran.music.service.UserPortraitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;






@Slf4j
@Service
public class UserPortraitServiceImpl implements UserPortraitService {

    private static final int MAX_PROFILE_LIKED_SONGS = 500;
    private static final int DEFAULT_SIMILAR_USER_LIMIT = 10;

    private final SongMapper songMapper;
    private final ListenHistoryMapper listenHistoryMapper;
    private final SongLikeMapper songLikeMapper;
    private final CommentMapper commentMapper;
    private final UserFollowMapper userFollowMapper;
    private final UserMapper userMapper;

    public UserPortraitServiceImpl(SongMapper songMapper,
                                   ListenHistoryMapper listenHistoryMapper,
                                   SongLikeMapper songLikeMapper,
                                   CommentMapper commentMapper,
                                   UserFollowMapper userFollowMapper,
                                   UserMapper userMapper) {
        this.songMapper = songMapper;
        this.listenHistoryMapper = listenHistoryMapper;
        this.songLikeMapper = songLikeMapper;
        this.commentMapper = commentMapper;
        this.userFollowMapper = userFollowMapper;
        this.userMapper = userMapper;
    }

    @Override
    public Map<String, Object> getUserPortrait(Long userId) {
        Map<String, Object> portrait = new HashMap<>();


        portrait.put("userId", userId);
        portrait.put("lastUpdateTime", LocalDateTime.now());


        portrait.put("musicPreference", getUserMusicPreference(userId));


        portrait.put("interestTags", getUserInterestTags(userId));


        portrait.put("activePeriods", getUserActivePeriods(userId));


        portrait.put("behaviorSummary", getUserBehaviorSummary(userId));

        return portrait;
    }

    @Override
    public Map<String, Object> getUserMusicPreference(Long userId) {
        Map<String, Object> preference = new HashMap<>();


        List<Song> likedSongs = getPublicLikedSongs(userId);

        if (likedSongs.isEmpty()) {
            preference.put("favoriteGenres", Arrays.asList("Pop"));        
            preference.put("favoriteLanguages", Arrays.asList("zh"));
            return preference;
        }


        Map<String, Integer> genreCount = new HashMap<>();

        Map<String, Integer> languageCount = new HashMap<>();

        for (Song song : likedSongs) {
            if (ObjectUtils.isNotEmpty(song.getMainType())) {
                genreCount.merge(song.getMainType(), 1, Integer::sum);
            }
            if (ObjectUtils.isNotEmpty(song.getLanguage())) {
                languageCount.merge(song.getLanguage(), 1, Integer::sum);
            }
        }


        List<String> favoriteGenres = genreCount.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());


        List<String> favoriteLanguages = languageCount.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        preference.put("favoriteGenres", favoriteGenres);
        preference.put("favoriteLanguages", favoriteLanguages);
        preference.put("genreDistribution", genreCount);
        preference.put("languageDistribution", languageCount);

        return preference;
    }

    @Override
    public List<String> getUserInterestTags(Long userId) {
        List<String> tags = new ArrayList<>();


        Map<String, Object> preference = getUserMusicPreference(userId);
        @SuppressWarnings("unchecked")
        List<String> genres = getStringList(preference, "favoriteGenres");
        @SuppressWarnings("unchecked")
        List<String> languages = getStringList(preference, "favoriteLanguages");

        if (genres != null && !genres.isEmpty()) {
            tags.add(genres.get(0) + "音乐爱好者");
        }

        if (languages != null && !languages.isEmpty()) {
            String langTag = getLanguageTag(languages.get(0));
            if (langTag != null) {
                tags.add(langTag);
            }
        }


        Map<String, Long> summary = getUserBehaviorSummary(userId);
        Long playCount = summary.getOrDefault("playCount", 0L);
        Long likeCount = summary.getOrDefault("likeCount", 0L);
        Long commentCount = summary.getOrDefault("commentCount", 0L);

        if (playCount > 1000) {
            tags.add("重度音乐用户");
        } else if (playCount > 500) {
            tags.add("活跃音乐用户");
        }

        if (likeCount > 100) {
            tags.add("收藏达人");
        }

        if (commentCount > 50) {
            tags.add("评论达人");
        }

        return tags.isEmpty() ? Arrays.asList("新用户") : tags;
    }

    @Override
    public Map<String, Integer> getUserActivePeriods(Long userId) {
        Map<String, Integer> periods = new HashMap<>();
        periods.put("morning", 0);            
        periods.put("afternoon", 0);           
        periods.put("evening", 0);             
        periods.put("night", 0);             


        LambdaQueryWrapper<ListenHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ListenHistory::getUserId, userId)
                .eq(ListenHistory::getDeleted, CommonConstants.NOT_DELETED)
                .ge(ListenHistory::getCreateTime, LocalDateTime.now().minusDays(30))
                .last("LIMIT 1000");

        List<ListenHistory> histories = listenHistoryMapper.selectList(wrapper);

        for (ListenHistory history : histories) {
            LocalDateTime playTime = history.getCreateTime();
            if (playTime == null) continue;

            int hour = playTime.getHour();
            if (hour >= 6 && hour < 12) {
                periods.merge("morning", 1, Integer::sum);
            } else if (hour >= 12 && hour < 18) {
                periods.merge("afternoon", 1, Integer::sum);
            } else if (hour >= 18 && hour < 24) {
                periods.merge("evening", 1, Integer::sum);
            } else {
                periods.merge("night", 1, Integer::sum);
            }
        }

        return periods;
    }

    @Override
    public Integer calculatePreferenceScore(Long userId, String contentType, String contentValue) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(contentValue)) {
            return 50;          
        }

        Map<String, Object> preference = getUserMusicPreference(userId);

        if ("genre".equals(contentType)) {
            @SuppressWarnings("unchecked")
            List<String> genres = getStringList(preference, "favoriteGenres");
            if (genres != null) {
                int index = genres.indexOf(contentValue);
                return index >= 0 ? 100 - index * 20 : 50;
            }
        } else if ("language".equals(contentType)) {
            @SuppressWarnings("unchecked")
            List<String> languages = getStringList(preference, "favoriteLanguages");
            if (languages != null) {
                int index = languages.indexOf(contentValue);
                return index >= 0 ? 100 - index * 25 : 50;
            }
        }

        return 50;
    }

    @Override
    public Boolean refreshUserPortrait(Long userId) {
        try {


            getUserPortrait(userId);
            return true;
        } catch (Exception e) {
            log.error("刷新用户画像失败: userId={}", userId);
            return false;
        }
    }

    @Override
    public Integer batchRefreshPortrait(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }

        int successCount = 0;
        for (Long userId : userIds) {
            if (refreshUserPortrait(userId)) {
                successCount++;
            }
        }

        log.info("批量刷新用户画像完成: total={}, success={}", userIds.size(), successCount);
        return successCount;
    }

    @Override
    public Map<String, Long> getUserBehaviorSummary(Long userId) {
        Map<String, Long> summary = new HashMap<>();


        Long playCount = listenHistoryMapper.selectCount(
                new LambdaQueryWrapper<ListenHistory>()
                        .eq(ListenHistory::getUserId, userId)
                        .eq(ListenHistory::getDeleted, CommonConstants.NOT_DELETED)
        );
        summary.put("playCount", playCount);


        Long likeCount = songLikeMapper.selectCount(
                new LambdaQueryWrapper<SongLike>()
                        .eq(SongLike::getUserId, userId)
                        .eq(SongLike::getIsFavorite, 1)
                        .eq(SongLike::getDeleted, CommonConstants.NOT_DELETED)
        );
        summary.put("likeCount", likeCount);


        Long commentCount = commentMapper.selectCount(
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getUserId, userId)
                        .eq(Comment::getDeleted, CommonConstants.NOT_DELETED)
        );
        summary.put("commentCount", commentCount);


        Long followingCount = userFollowMapper.selectCount(
                new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFollowerId, userId)
                        .eq(UserFollow::getDeleted, CommonConstants.NOT_DELETED)
        );
        summary.put("followingCount", followingCount);


        Long followerCount = userFollowMapper.selectCount(
                new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFolloweeId, userId)
                        .eq(UserFollow::getDeleted, CommonConstants.NOT_DELETED)
        );
        summary.put("followerCount", followerCount);

        return summary;
    }

    @Override
    public List<Long> predictUserPreferences(Long userId, Integer limit) {

        Map<String, Object> preference = getUserMusicPreference(userId);
        @SuppressWarnings("unchecked")
        List<String> favoriteGenres = getStringList(preference, "favoriteGenres");


        String genre = favoriteGenres.isEmpty() ? "Pop" : favoriteGenres.get(0);

        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Song::getMainType, genre)
                .eq(Song::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Song::getDeleted, CommonConstants.NOT_DELETED)
                .orderByDesc(Song::getHotScore)
                .last("LIMIT " + (limit != null ? limit : 20));

        return songMapper.selectList(wrapper).stream()
                .map(Song::getId)
                .collect(Collectors.toList());
    }

    @Override
    public List<Long> getSimilarUsers(Long userId, Integer limit) {
        List<Long> likedSongIds = getPublicLikedSongs(userId).stream()
                .map(Song::getId)
                .filter(ObjectUtils::isNotEmpty)
                .collect(Collectors.toList());
        if (likedSongIds.isEmpty()) {
            return Collections.emptyList();
        }

        int safeLimit = limit == null || limit <= 0 ? DEFAULT_SIMILAR_USER_LIMIT : Math.min(limit, 100);
        return songLikeMapper.selectSimilarPublicUserIds(userId, likedSongIds, safeLimit);
    }

    @Override
    public Integer calculateUserSimilarity(Long userId1, Long userId2) {
        if (ObjectUtils.isEmpty(userId1) || ObjectUtils.isEmpty(userId2)) {
            return 0;
        }

        if (userId1.equals(userId2)) {
            return 100;
        }


        Set<Long> user1Liked = getPublicLikedSongs(userId1).stream()
                .map(Song::getId)
                .collect(Collectors.toSet());
        Set<Long> user2Liked = getPublicLikedSongs(userId2).stream()
                .map(Song::getId)
                .collect(Collectors.toSet());

        if (user1Liked.isEmpty() && user2Liked.isEmpty()) {
            return 50;         
        }


        Set<Long> intersection = new HashSet<>(user1Liked);
        intersection.retainAll(user2Liked);

        Set<Long> union = new HashSet<>(user1Liked);
        union.addAll(user2Liked);

        if (union.isEmpty()) {
            return 0;
        }

        double jaccard = (double) intersection.size() / union.size();
        return (int) (jaccard * 100);
    }









    private List<Song> getPublicLikedSongs(Long userId) {
        LambdaQueryWrapper<SongLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SongLike::getUserId, userId)
                .eq(SongLike::getIsFavorite, 1)
                .eq(SongLike::getDeleted, CommonConstants.NOT_DELETED)
                .orderByDesc(SongLike::getCreateTime)
                .last("LIMIT " + MAX_PROFILE_LIKED_SONGS);

        List<Long> songIds = songLikeMapper.selectList(wrapper).stream()
                .map(SongLike::getSongId)
                .filter(ObjectUtils::isNotEmpty)
                .collect(Collectors.toList());
        if (songIds.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, Song> songsById = songMapper.selectBatchIds(songIds).stream()
                .filter(song -> CommonConstants.STATUS_NORMAL.equals(song.getStatus()))
                .collect(Collectors.toMap(Song::getId, song -> song, (left, right) -> left));
        if (songsById.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> uploaderIds = songsById.values().stream()
                .map(Song::getUploaderId)
                .filter(ObjectUtils::isNotEmpty)
                .collect(Collectors.toSet());
        Map<Long, User> uploadersById = uploaderIds.isEmpty()
                ? Collections.emptyMap()
                : userMapper.selectBatchIds(uploaderIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user, (left, right) -> left));

        return songIds.stream()
                .map(songsById::get)
                .filter(Objects::nonNull)
                .filter(song -> song.getUploaderId() == null
                        || UserAccountStatusUtil.canExposePublicContent(uploadersById.get(song.getUploaderId())))
                .collect(Collectors.toList());
    }





    private String getLanguageTag(String language) {
        if (language == null) {
            return null;
        }
        switch (language) {
            case "zh":
                return "华语音乐";
            case "en":
                return "欧美音乐";
            case "ja":
                return "日语音乐";
            case "ko":
                return "韩语音乐";
            case "fr":
                return "法语音乐";
            default:
                return null;
        }
    }




    @SuppressWarnings("unchecked")
    private List<String> getStringList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List) {
            return (List<String>) value;
        }
        return Collections.emptyList();
    }
}
