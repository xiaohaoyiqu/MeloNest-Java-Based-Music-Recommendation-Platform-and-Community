










package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.constant.MusicConstants;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.*;
import com.haoran.music.mapper.*;
import com.haoran.music.service.MessageKeywordService;
import com.haoran.music.service.UserFollowService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;




@Slf4j
@Service
public class MessageKeywordServiceImpl implements MessageKeywordService {


    private static final Pattern ARTIST_PATTERN = Pattern.compile("([\\u4e00-\\u9fa5a-zA-Z]{2,10})(?:唱|的歌|的歌曲|的新歌|的专辑)");
    private static final Pattern SONG_PATTERN = Pattern.compile("《([^《》]{2,20})》|([\\u4e00-\\u9fa5a-zA-Z]{2,10})(?:这首歌|那首歌|好好听|推荐)");
    private static final Pattern GENRE_PATTERN = Pattern.compile("(流行|摇滚|民谣|电子|古典|爵士|说唱|嘻哈|R&B|灵魂|金属|朋克|雷鬼|蓝调)");


    private static final String HASH_SALT = "haoran2026";

    private final SongMapper songMapper;
    private final UserFollowService userFollowService;
    private final UserFollowMapper userFollowMapper;
    private final ListenHistoryMapper listenHistoryMapper;
    private final PlaylistMapper playlistMapper;
    private final PlaylistSongMapper playlistSongMapper;
    private final UserMapper userMapper;
    private final ArtistMapper artistMapper;
    private final RedisTemplate<String, Object> redisTemplate;


    private static final String SOCIAL_RECOMMEND_KEY = "social_recommend:";
    private static final String USER_KEYWORD_KEY = "user_keywords:";
    private static final String USER_PREFERENCE_KEY = "social_recommend_pref:";

    public MessageKeywordServiceImpl(
            SongMapper songMapper,
            UserFollowService userFollowService,
            UserFollowMapper userFollowMapper,
            ListenHistoryMapper listenHistoryMapper,
            PlaylistMapper playlistMapper,
            PlaylistSongMapper playlistSongMapper,
            UserMapper userMapper,
            ArtistMapper artistMapper,
            RedisTemplate<String, Object> redisTemplate) {
        this.songMapper = songMapper;
        this.userFollowService = userFollowService;
        this.userFollowMapper = userFollowMapper;
        this.listenHistoryMapper = listenHistoryMapper;
        this.playlistMapper = playlistMapper;
        this.playlistSongMapper = playlistSongMapper;
        this.userMapper = userMapper;
        this.artistMapper = artistMapper;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public List<KeywordExtractResult> extractMusicKeywords(String message, Long userId) {

        if (!isSocialRecommendEnabled(userId)) {
            return Collections.emptyList();
        }

        List<KeywordExtractResult> results = new ArrayList<>();

        try {

            results.addAll(extractArtists(message));


            results.addAll(extractSongs(message));


            results.addAll(extractGenres(message));


            if (!results.isEmpty()) {
                saveKeywords(userId, results);
            }

            log.debug("用户 {} 提取到 {} 个音乐关键词", userId, results.size());
        } catch (Exception e) {
            log.warn("提取音乐关键词失败: userId={}, error={}", userId, e.getClass().getSimpleName());
        }

        return results;
    }

    @Override
    public Map<String, Integer> extractAndAggregateKeywords(List<String> messages, Long userId) {
        Map<String, Integer> keywordCount = new HashMap<>();

        for (String message : messages) {
            List<KeywordExtractResult> keywords = extractMusicKeywords(message, userId);
            for (KeywordExtractResult result : keywords) {
                keywordCount.merge(result.getHashedKeyword(), 1, Integer::sum);
            }
        }

        return keywordCount;
    }

    @Override
    public List<Long> recommendByKeywords(Long userId, List<String> keywordList, int limit) {

        Set<Long> recommendedSongIds = new HashSet<>();

        for (String hashedKeyword : keywordList) {

            String cacheKey = SOCIAL_RECOMMEND_KEY + "keyword:" + hashedKeyword;
            @SuppressWarnings("unchecked")
            List<Long> cachedSongIds = ObjectUtils.castList(redisTemplate.opsForValue().get(cacheKey), Long.class);

            if (cachedSongIds != null) {
                recommendedSongIds.addAll(cachedSongIds);
                continue;
            }


            List<Long> songIds = findSongsByKeyword(hashedKeyword);

            if (!songIds.isEmpty()) {

                redisTemplate.opsForValue().set(cacheKey, songIds, 1, TimeUnit.HOURS);
                recommendedSongIds.addAll(songIds);
            }
        }


        Set<Long> listenedSongIds = getUserListenedSongs(userId);
        recommendedSongIds.removeAll(listenedSongIds);
        List<Long> publicRecommendedSongIds = filterPublicSongIds(recommendedSongIds);


        List<Long> result = publicRecommendedSongIds.stream()
                .limit(limit)
                .collect(Collectors.toList());

        log.info("基于关键词推荐: userId={}, keywordCount={}, recommended={}",
                userId, keywordList.size(), result.size());

        return result;
    }

    @Override
    public boolean isSocialRecommendEnabled(Long userId) {
        SocialRecommendPreference preference = getUserPreference(userId);
        return preference != null && preference.getEnabled();
    }

    @Override
    public void setSocialRecommendEnabled(Long userId, boolean enabled) {
        SocialRecommendPreference preference = getUserPreference(userId);
        if (preference == null) {
            preference = new SocialRecommendPreference();
        }
        preference.setEnabled(enabled);


        String key = USER_PREFERENCE_KEY + userId;
        redisTemplate.opsForValue().set(key, preference, 30, TimeUnit.DAYS);

        log.info("用户 {} 社交推荐设置为: {}", userId, enabled);
    }

    @Override
    public void clearExpiredKeywords(int days) {

        log.info("关键词自动清除任务执行，保留天数: {}", days);
    }

    @Override
    public void saveUserPreference(Long userId, SocialRecommendPreference preference) {
        if (ObjectUtils.isEmpty(userId) || preference == null) {
            return;
        }
        String key = USER_PREFERENCE_KEY + userId;
        redisTemplate.opsForValue().set(key, preference, 30, TimeUnit.DAYS);
        log.info("保存用户社交推荐偏好: userId={}, enabled={}", userId, preference.getEnabled());
    }

    @Override
    public void clearUserKeywords(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return;
        }
        String key = USER_KEYWORD_KEY + userId;
        redisTemplate.delete(key);
        log.info("清除用户关键词数据: userId={}", userId);
    }








    public Map<String, Object> comprehensiveSocialRecommend(Long userId, int limit) {
        if (!isSocialRecommendEnabled(userId)) {
            return Collections.emptyMap();
        }

        Map<Long, RecommendScore> scoreMap = new HashMap<>();


        List<String> userKeywords = getUserKeywords(userId);
        if (!userKeywords.isEmpty()) {
            List<Long> keywordSongs = recommendByKeywords(userId, userKeywords, limit * 2);
            for (Long songId : keywordSongs) {
                addScore(scoreMap, songId, 15.0, "keyword", "聊天中提到");
            }
        }


        List<Long> friendIds = getFollowingIds(userId);
        for (Long friendId : friendIds) {
            List<Long> friendRecentSongs = getFriendRecentSongs(friendId, 20);
            for (Long songId : friendRecentSongs) {
                String friendName = getDisplayName(friendId);
                addScore(scoreMap, songId, 10.0, "friend_listen", "好友" + friendName + "听过");
            }
        }


        for (Long friendId : friendIds) {
            List<Long> friendFavorites = getFriendFavorites(friendId, 10);
            for (Long songId : friendFavorites) {
                String friendName = getDisplayName(friendId);
                addScore(scoreMap, songId, 10.0, "friend_favorite", "好友" + friendName + "收藏");
            }
        }


        List<Long> commonArtistIds = getCommonFollowedArtists(userId, friendIds);
        for (Long artistId : commonArtistIds) {
            List<Long> artistSongs = getSongsByArtist(artistId, 5);
            for (Long songId : artistSongs) {
                addScore(scoreMap, songId, 5.0, "common_artist", "共同关注的歌手");
            }
        }


        Set<Long> publicSongIds = new HashSet<>(filterPublicSongIds(scoreMap.keySet()));
        List<RecommendResult> results = scoreMap.entrySet().stream()
                .filter(entry -> publicSongIds.contains(entry.getKey()))
                .sorted((e1, e2) -> Double.compare(e2.getValue().getScore(), e1.getValue().getScore()))
                .limit(limit)
                .map(entry -> new RecommendResult(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("recommendations", results);
        response.put("total", results.size());

        log.info("综合社交推荐: userId={}, results={}", userId, results.size());

        return response;
    }







    public SocialRecommendPreference getUserPreference(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return null;
        }
        String key = USER_PREFERENCE_KEY + userId;
        SocialRecommendPreference preference = (SocialRecommendPreference) redisTemplate.opsForValue().get(key);
        if (preference == null) {

            preference = new SocialRecommendPreference();
            preference.setEnabled(false);
            preference.setShowSource(true);
            preference.setAllowShared(false);
            preference.setKeywordRetentionDays(7);
        }
        return preference;
    }






    private List<KeywordExtractResult> extractArtists(String message) {
        List<KeywordExtractResult> results = new ArrayList<>();
        java.util.regex.Matcher matcher = ARTIST_PATTERN.matcher(message);

        while (matcher.find()) {
            String artist = matcher.group(1);
            String hashed = hashKeyword(artist);
            results.add(new KeywordExtractResult("artist", hashed, 0.8));
        }

        return results;
    }




    private List<KeywordExtractResult> extractSongs(String message) {
        List<KeywordExtractResult> results = new ArrayList<>();


        java.util.regex.Matcher matcher = SONG_PATTERN.matcher(message);
        while (matcher.find()) {
            String song = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            String hashed = hashKeyword(song);
            results.add(new KeywordExtractResult("song", hashed, 0.7));
        }

        return results;
    }




    private List<KeywordExtractResult> extractGenres(String message) {
        List<KeywordExtractResult> results = new ArrayList<>();
        java.util.regex.Matcher matcher = GENRE_PATTERN.matcher(message);

        while (matcher.find()) {
            String genre = matcher.group(1);
            String hashed = hashKeyword(genre);
            results.add(new KeywordExtractResult("genre", hashed, 0.6));
        }

        return results;
    }




    private String hashKeyword(String keyword) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(HASH_SALT.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(keyword.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {

            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(keyword.getBytes(StandardCharsets.UTF_8));
                return Base64.getEncoder().encodeToString(hash);
            } catch (Exception ex) {
                return keyword;             
            }
        }
    }




    private void saveKeywords(Long userId, List<KeywordExtractResult> keywords) {
        String key = USER_KEYWORD_KEY + userId;


        @SuppressWarnings("unchecked")
        Map<String, Long> existingKeywords = (Map<String, Long>) redisTemplate.opsForValue().get(key);
        if (existingKeywords == null) {
            existingKeywords = new HashMap<>();
        }


        long now = System.currentTimeMillis();
        for (KeywordExtractResult result : keywords) {
            existingKeywords.put(result.getHashedKeyword(), now);
        }


        SocialRecommendPreference preference = getUserPreference(userId);
        int retentionDays = preference != null ? preference.getKeywordRetentionDays() : 7;
        redisTemplate.opsForValue().set(key, existingKeywords, retentionDays, TimeUnit.DAYS);
    }




    private List<String> getUserKeywords(Long userId) {
        String key = USER_KEYWORD_KEY + userId;
        @SuppressWarnings("unchecked")
        Map<String, Long> keywords = (Map<String, Long>) redisTemplate.opsForValue().get(key);
        return keywords != null ? new ArrayList<>(keywords.keySet()) : Collections.emptyList();
    }







    private List<Long> findSongsByKeyword(String keyword) {
        if (ObjectUtils.isEmpty(keyword)) {
            return Collections.emptyList();
        }

        List<Long> result = new ArrayList<>();


        LambdaQueryWrapper<Song> songWrapper = new LambdaQueryWrapper<>();
        songWrapper.eq(Song::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Song::getDeleted, CommonConstants.NOT_DELETED)
                .like(Song::getName, keyword)
                .orderByDesc(Song::getHotScore)
                .last("LIMIT 20");
        result.addAll(filterPublicSongs(songMapper.selectList(songWrapper)).stream()
                .map(Song::getId)
                .collect(Collectors.toList()));


        if (result.size() < 20) {
            LambdaQueryWrapper<Artist> artistWrapper = new LambdaQueryWrapper<>();
            artistWrapper.eq(Artist::getDeleted, CommonConstants.NOT_DELETED)
                    .like(Artist::getName, keyword)
                    .orderByDesc(Artist::getHotScore)
                    .last("LIMIT 10");
            List<Artist> artists = artistMapper.selectList(artistWrapper);


            for (Artist artist : artists) {
                LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(Song::getStatus, CommonConstants.STATUS_NORMAL)
                        .eq(Song::getDeleted, CommonConstants.NOT_DELETED)
                        .apply("FIND_IN_SET({0}, artist_ids) > 0", artist.getId())
                        .orderByDesc(Song::getHotScore)
                        .last("LIMIT 5");
                result.addAll(filterPublicSongs(songMapper.selectList(wrapper)).stream()
                        .map(Song::getId)
                        .collect(Collectors.toList()));
            }
        }


        return result.stream()
                .distinct()
                .limit(50)
                .collect(Collectors.toList());
    }




    private Set<Long> getUserListenedSongs(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return Collections.emptySet();
        }

        LambdaQueryWrapper<ListenHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ListenHistory::getUserId, userId)
                .eq(ListenHistory::getDeleted, CommonConstants.NOT_DELETED)
                .select(ListenHistory::getSongId)
                .last("LIMIT 1000");

        return new HashSet<>(listenHistoryMapper.selectList(wrapper).stream()
                .map(ListenHistory::getSongId)
                .collect(Collectors.toList()));
    }




    private List<Long> getFollowingIds(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return Collections.emptyList();
        }
        List<Long> followingIds = userFollowService.getFollowingIds(userId);
        if (followingIds == null || followingIds.isEmpty()) {
            return Collections.emptyList();
        }

        return userMapper.selectBatchIds(followingIds).stream()
                .filter(UserAccountStatusUtil::canAppearInRecommendations)
                .map(User::getId)
                .collect(Collectors.toList());
    }








    private List<Long> getFriendRecentSongs(Long friendId, int limit) {
        if (ObjectUtils.isEmpty(friendId)) {
            return Collections.emptyList();
        }


        SocialRecommendPreference preference = getUserPreference(friendId);
        if (preference == null || !preference.getAllowShared()) {

            log.debug("用户 {} 不允许分享听歌记录", friendId);
            return Collections.emptyList();
        }

        LambdaQueryWrapper<ListenHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ListenHistory::getUserId, friendId)
                .eq(ListenHistory::getDeleted, CommonConstants.NOT_DELETED)
                .orderByDesc(ListenHistory::getListenTime)
                .last("LIMIT " + limit);

        return filterPublicSongIds(listenHistoryMapper.selectList(wrapper).stream()
                .map(ListenHistory::getSongId)
                .collect(Collectors.toList()));
    }




    private List<Long> getFriendFavorites(Long friendId, int limit) {
        if (ObjectUtils.isEmpty(friendId)) {
            return Collections.emptyList();
        }


        LambdaQueryWrapper<Playlist> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Playlist::getUserId, friendId)
                .eq(Playlist::getType, MusicConstants.PlaylistType.FAVORITE)
                .eq(Playlist::getDeleted, CommonConstants.NOT_DELETED)
                .select(Playlist::getId);

        Playlist favoritePlaylist = playlistMapper.selectOne(wrapper);
        if (favoritePlaylist == null) {
            return Collections.emptyList();
        }


        LambdaQueryWrapper<PlaylistSong> songWrapper = new LambdaQueryWrapper<>();
        songWrapper.eq(PlaylistSong::getPlaylistId, favoritePlaylist.getId())
                .eq(PlaylistSong::getDeleted, CommonConstants.NOT_DELETED)
                .orderByDesc(PlaylistSong::getAddTime)
                .last("LIMIT " + limit);

        return filterPublicSongIds(playlistSongMapper.selectList(songWrapper).stream()
                .map(PlaylistSong::getSongId)
                .collect(Collectors.toList()));
    }




    private List<Long> getSongsByArtist(Long artistId, int limit) {
        if (ObjectUtils.isEmpty(artistId)) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Song::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Song::getDeleted, CommonConstants.NOT_DELETED)
                .apply("FIND_IN_SET({0}, artist_ids) > 0", artistId)
                .orderByDesc(Song::getHotScore)
                .last("LIMIT " + limit);

        return filterPublicSongs(songMapper.selectList(wrapper)).stream()
                .map(Song::getId)
                .collect(Collectors.toList());
    }

    private List<Long> filterPublicSongIds(Collection<Long> songIds) {
        if (songIds == null || songIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> orderedIds = songIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (orderedIds.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Song> songMap = filterPublicSongs(songMapper.selectBatchIds(orderedIds)).stream()
                .collect(Collectors.toMap(Song::getId, song -> song, (left, right) -> left));
        return orderedIds.stream()
                .filter(songMap::containsKey)
                .collect(Collectors.toList());
    }

    private List<Song> filterPublicSongs(List<Song> songs) {
        if (songs == null || songs.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> uploaderIds = songs.stream()
                .map(Song::getUploaderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> allowedUploaderIds = UserAccountStatusUtil.filterPublicContentUserIds(
                uploaderIds, ids -> userMapper.selectBatchIds(ids));
        return songs.stream()
                .filter(song -> song != null
                        && CommonConstants.STATUS_NORMAL.equals(song.getStatus())
                        && CommonConstants.NOT_DELETED.equals(song.getDeleted())
                        && (song.getUploaderId() == null || allowedUploaderIds.contains(song.getUploaderId())))
                .collect(Collectors.toList());
    }




    private void addScore(Map<Long, RecommendScore> scoreMap, Long songId,
                         Double score, String source, String description) {
        RecommendScore rs = scoreMap.computeIfAbsent(songId, k -> new RecommendScore(k));
        rs.addScore(score, source, description);
    }




    private static class RecommendScore {
        @Getter
        private final Long songId;
        private Double totalScore = 0.0;
        @Getter
        private final List<ScoreSource> sources = new ArrayList<>();

        public RecommendScore(Long songId) {
            this.songId = songId;
        }

        public void addScore(Double score, String source, String description) {
            this.totalScore += score;
            this.sources.add(new ScoreSource(source, description, score));
        }

        public Double getScore() { return totalScore; }
    }




    @Getter
    public static class ScoreSource {
        private final String source;
        private final String description;
        private final Double score;

        public ScoreSource(String source, String description, Double score) {
            this.source = source;
            this.description = description;
            this.score = score;
        }

    }




    @Getter
    public static class RecommendResult {
        private final Long songId;
        private final Double score;
        private final List<ScoreSource> sources;

        public RecommendResult(Long songId, RecommendScore rs) {
            this.songId = songId;
            this.score = rs.getScore();
            this.sources = rs.getSources();
        }

    }







    private String getDisplayName(Long userId) {
        if (userId == null) {
            return "未知用户";
        }
        User user = userMapper.selectById(userId);
        return user != null && user.getNickname() != null ? user.getNickname() : "未知用户";
    }








    private List<Long> getCommonFollowedArtists(Long userId, List<Long> friendIds) {
        if (userId == null || friendIds == null || friendIds.isEmpty()) {
            return new ArrayList<>();
        }


        Set<Long> userArtists = getFollowedArtistIds(userId);


        List<Long> commonArtists = new ArrayList<>();
        for (Long friendId : friendIds) {
            Set<Long> friendArtists = getFollowedArtistIds(friendId);
            for (Long artistId : friendArtists) {
                if (userArtists.contains(artistId) && !commonArtists.contains(artistId)) {
                    commonArtists.add(artistId);
                }
            }
            if (commonArtists.size() >= 10) {
                break;
            }
        }

        return commonArtists;
    }







    private Set<Long> getFollowedArtistIds(Long userId) {
        Set<Long> artistIds = new HashSet<>();
        LambdaQueryWrapper<UserFollow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFollow::getFollowerId, userId)
                .select(UserFollow::getFolloweeId);
        List<UserFollow> follows = userFollowMapper.selectList(wrapper);

        if (ObjectUtils.isEmpty(follows)) {
            return artistIds;
        }


        Set<Long> followeeIds = follows.stream()
                .map(UserFollow::getFolloweeId)
                .collect(Collectors.toSet());


        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.in(User::getId, followeeIds)
                .eq(User::getIsCreator, 1);
        List<User> artists = userMapper.selectList(userWrapper);

        for (User artist : artists) {
            artistIds.add(artist.getId());
        }

        return artistIds;
    }
}
