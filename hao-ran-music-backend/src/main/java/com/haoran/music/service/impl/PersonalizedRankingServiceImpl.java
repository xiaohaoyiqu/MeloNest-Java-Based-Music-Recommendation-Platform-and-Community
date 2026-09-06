package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.constant.UserAccountPolicyConstants;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.*;
import com.haoran.music.mapper.*;
import com.haoran.music.service.PersonalizedRankingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

   
             
  
        
                      
                       
                        
              
              
                     
  
                      
   
@Slf4j
@Service
public class PersonalizedRankingServiceImpl implements PersonalizedRankingService {

    private final SongMapper songMapper;
    private final CreatorMapper creatorMapper;
    private final SongLikeMapper songLikeMapper;
    private final PlaylistSongMapper playlistSongMapper;
    private final PlaylistMapper playlistMapper;
    private final ListenHistoryMapper listenHistoryMapper;
    private final UserFollowMapper userFollowMapper;
    private final UserMapper userMapper;

             
    private static final double PREFERENCE_SCORE_WEIGHT = 0.5;
    private static final double PLAY_COUNT_WEIGHT = 0.2;
    private static final double HOT_SCORE_WEIGHT = 0.2;
    private static final double NEW_SONG_BOOST = 0.1;

    public PersonalizedRankingServiceImpl(SongMapper songMapper,
                                         CreatorMapper creatorMapper,
                                         SongLikeMapper songLikeMapper,
                                         PlaylistSongMapper playlistSongMapper,
                                         PlaylistMapper playlistMapper,
                                         ListenHistoryMapper listenHistoryMapper,
                                         UserFollowMapper userFollowMapper,
                                         UserMapper userMapper) {
        this.songMapper = songMapper;
        this.creatorMapper = creatorMapper;
        this.songLikeMapper = songLikeMapper;
        this.playlistSongMapper = playlistSongMapper;
        this.playlistMapper = playlistMapper;
        this.listenHistoryMapper = listenHistoryMapper;
        this.userFollowMapper = userFollowMapper;
        this.userMapper = userMapper;
    }

    @Override
    public Map<String, Object> getPersonalizedHotSongs(Long userId, Integer limit) {
        int actualLimit = limit != null && limit > 0 ? limit : 50;

        Map<String, Object> result = new HashMap<>();

                                 
        Map<String, Object> preference = getUserMusicPreferenceWithHistory(userId);
        List<String> favoriteGenres = getStringList(preference, "favoriteGenres", Arrays.asList("Pop"));

                              
        Set<Long> userLikedSongIds = getUserLikedSongIds(userId);

                        
        Map<Long, Double> songScoreMap = new HashMap<>();

        for (String genre : favoriteGenres) {
            LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Song::getMainType, genre)
                    .eq(Song::getStatus, CommonConstants.STATUS_NORMAL)
                    .eq(Song::getDeleted, CommonConstants.NOT_DELETED)
                    .last("LIMIT 200");

            List<Song> genreSongs = songMapper.selectList(wrapper);

            for (Song song : genreSongs) {
                if (userLikedSongIds.contains(song.getId())) {
                    continue;
                }

                double score = calculateSongScore(song, userId, genre);
                songScoreMap.put(song.getId(), Math.max(songScoreMap.getOrDefault(song.getId(), 0.0), score));
            }
        }

                           
        Set<Long> friendIds = getMutualFriends(userId);
        for (Long friendId : friendIds) {
            List<Long> friendLikedSongs = getFriendLikedSongs(friendId);
            for (Long sid : friendLikedSongs) {
                if (!userLikedSongIds.contains(sid)) {
                    songScoreMap.merge(sid, 10.0, Double::sum);
                }
            }
        }

                      
        List<Long> rankedSongIds = songScoreMap.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                                             
                .limit(Math.min((long) actualLimit * 3L, Integer.MAX_VALUE))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        Map<Long, Song> songMap = new HashMap<>();
        if (!rankedSongIds.isEmpty()) {
            LambdaQueryWrapper<Song> songWrapper = new LambdaQueryWrapper<>();
            songWrapper.in(Song::getId, rankedSongIds)
                    .eq(Song::getStatus, CommonConstants.STATUS_NORMAL)
                    .eq(Song::getDeleted, CommonConstants.NOT_DELETED);
            List<Song> songs = filterPublicUploaderSongs(songMapper.selectList(songWrapper));
            songMap.putAll(songs.stream().collect(Collectors.toMap(Song::getId, s -> s)));
        }
                  
        List<Map<String, Object>> rankedSongs = rankedSongIds.stream()
                .map(id -> {
                    Song song = songMap.get(id);
                    if (song == null) {
                        return null;
                    }
                    Map<String, Object> songVO = new HashMap<>();
                    songVO.put("id", song.getId());
                    songVO.put("name", song.getName());
                    songVO.put("artistNames", song.getArtistNames());
                    songVO.put("artistId", song.getArtistId());
                    songVO.put("albumName", song.getAlbumName());
                    songVO.put("cover", song.getCover());
                    songVO.put("duration", song.getDuration());
                    songVO.put("playCount", song.getPlayCount());
                    songVO.put("score", songScoreMap.get(id));
                    return songVO;
                })
                .filter(Objects::nonNull)
                .limit(actualLimit)
                .collect(Collectors.toList());

        result.put("songs", rankedSongs);
        result.put("total", songMap.size());
        result.put("preferenceGenres", favoriteGenres);

        return result;
    }

    @Override
    public Map<String, Object> getPersonalizedCreators(Long userId, Integer limit) {
        int actualLimit = limit != null && limit > 0 ? limit : 20;

        Map<String, Object> result = new HashMap<>();

                        
        Set<Long> followingCreatorUserIds = getFollowingCreatorUserIds(userId);

                        
        Set<Long> friendIds = getMutualFriends(userId);
        Set<Long> friendCreatorUserIds = new HashSet<>();
        for (Long friendId : friendIds) {
            friendCreatorUserIds.addAll(getFollowingCreatorUserIds(friendId));
        }

                   
        Map<Long, Double> creatorUserScoreMap = new HashMap<>();

        for (Long creatorUserId : followingCreatorUserIds) {
            creatorUserScoreMap.put(creatorUserId, 50.0);
        }

        for (Long creatorUserId : friendCreatorUserIds) {
            creatorUserScoreMap.merge(creatorUserId, 30.0, Double::sum);
        }

                          
        List<Long> creatorUserIds = new ArrayList<>(creatorUserScoreMap.keySet());
        Map<Long, Creator> creatorMap = new HashMap<>();
        Map<Long, User> userMap = new HashMap<>();

        if (!creatorUserIds.isEmpty()) {
            LambdaQueryWrapper<Creator> creatorWrapper = new LambdaQueryWrapper<>();
            creatorWrapper.in(Creator::getUserId, creatorUserIds)
                    .eq(Creator::getStatus, "active")
                    .eq(Creator::getDeleted, CommonConstants.NOT_DELETED);
            List<Creator> creators = creatorMapper.selectList(creatorWrapper);
            creatorMap.putAll(creators.stream().collect(Collectors.toMap(Creator::getUserId, c -> c)));

            LambdaQueryWrapper<User> userWrapper = UserAccountStatusUtil.publicContentUserQuery()
                    .in(User::getId, creatorUserIds);
            List<User> users = userMapper.selectList(userWrapper);
            userMap.putAll(users.stream().collect(Collectors.toMap(User::getId, u -> u)));
            creatorUserScoreMap.keySet().removeIf(uid ->
                    !UserAccountStatusUtil.canExposePublicContent(userMap.get(uid)));
        }

                           
        for (Map.Entry<Long, Double> entry : creatorUserScoreMap.entrySet()) {
            Creator creator = creatorMap.get(entry.getKey());
            if (creator != null) {
                Long fansCount = creator.getFansCount();
                if (fansCount != null && fansCount > 0) {
                    double fansBonus = Math.min(fansCount / 100.0, 20.0);
                    entry.setValue(entry.getValue() + fansBonus);
                }

                if ("signed".equals(creator.getCreatorType()) || "external_signed".equals(creator.getCreatorType())) {
                    entry.setValue(entry.getValue() + 10.0);
                }
            }

            User user = userMap.get(entry.getKey());
            if (user != null && user.getCreditScore() != null
                    && user.getCreditScore() >= UserAccountPolicyConstants.CREDIT_SCORE_GOOD_MIN) {
                entry.setValue(entry.getValue() + 5.0);
            }
        }

                      
        List<Long> rankedCreatorUserIds = creatorUserScoreMap.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(actualLimit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        List<Map<String, Object>> rankedCreators = rankedCreatorUserIds.stream()
                .map(uid -> {
                    User user = userMap.get(uid);
                    if (user == null) {
                        return null;
                    }

                    Creator creator = creatorMap.get(uid);

                    Map<String, Object> creatorVO = new HashMap<>();
                    creatorVO.put("userId", user.getId());
                    creatorVO.put("creatorName", user.getNickname() != null ? user.getNickname() : user.getUsername());
                    creatorVO.put("avatar", user.getAvatar());
                    creatorVO.put("creatorId", creator != null ? creator.getId() : null);
                    creatorVO.put("fansCount", creator != null ? creator.getFansCount() : 0);
                    creatorVO.put("description", user.getIntroduction());
                    creatorVO.put("creatorType", creator != null ? creator.getCreatorType() : "independent");
                    creatorVO.put("score", creatorUserScoreMap.get(uid));
                    creatorVO.put("isFollowing", followingCreatorUserIds.contains(uid));
                    creatorVO.put("isOfficial", user.getIsOfficial() != null && user.getIsOfficial() == 1);
                    return creatorVO;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        result.put("creators", rankedCreators);
        result.put("total", creatorUserScoreMap.size());

        return result;
    }

    @Override
    public Map<String, Object> getPersonalizedPlaylists(Long userId, Integer limit) {
        int actualLimit = limit != null && limit > 0 ? limit : 20;

        Map<String, Object> result = new HashMap<>();

                      
        Map<String, Object> preference = getUserMusicPreferenceWithHistory(userId);
        List<String> favoriteGenres = getStringList(preference, "favoriteGenres", Arrays.asList("Pop"));

                           
        Set<Long> userLikedSongIds = getUserLikedSongIds(userId);
        Set<Long> userPlaylistSongIds = getUserPlaylistSongIds(userId);

                                
        LambdaQueryWrapper<Playlist> wrapper = new LambdaQueryWrapper<>();
        wrapper.ne(Playlist::getUserId, userId)
                .eq(Playlist::getIsPublic, CommonConstants.PUBLIC_PUBLIC)
                .eq(Playlist::getDeleted, CommonConstants.NOT_DELETED)
                .gt(Playlist::getSongCount, 0)
                .orderByDesc(Playlist::getFavoriteCount)
                .orderByDesc(Playlist::getPlayCount)
                .last("LIMIT 500");

        List<Playlist> allPlaylists = filterPublicCreatorPlaylists(playlistMapper.selectList(wrapper));

                         
        Map<Long, Double> playlistScoreMap = new HashMap<>();
        Set<Long> playlistCreatorUserIds = new HashSet<>();

        for (Playlist playlist : allPlaylists) {
            double score = 0.0;

                       
            List<Long> playlistSongIdList = getPlaylistSongIds(playlist.getId());

                      
            int matchedSongs = 0;
            for (Long sid : playlistSongIdList) {
                if (userLikedSongIds.contains(sid) || userPlaylistSongIds.contains(sid)) {
                    matchedSongs++;
                }
            }

                           
            if (!playlistSongIdList.isEmpty()) {
                double matchRate = (double) matchedSongs / playlistSongIdList.size();
                score += matchRate * 50;
            }

                           
            Long favoriteCount = playlist.getFavoriteCount() != null ? playlist.getFavoriteCount() : 0;
            score += Math.min(Math.log(favoriteCount + 1) * 5, 30);

                           
            Long playCount = playlist.getPlayCount() != null ? playlist.getPlayCount() : 0;
            score += Math.min(Math.log(playCount + 1) * 3, 20);

            playlistScoreMap.put(playlist.getId(), score);
            playlistCreatorUserIds.add(playlist.getUserId());
        }

                      
        Map<Long, User> userMap = new HashMap<>();
        if (!playlistCreatorUserIds.isEmpty()) {
            LambdaQueryWrapper<User> userWrapper = UserAccountStatusUtil.publicContentUserQuery()
                    .in(User::getId, playlistCreatorUserIds);
            List<User> users = userMapper.selectList(userWrapper);
            userMap.putAll(users.stream().collect(Collectors.toMap(User::getId, u -> u)));
        }

                      
        List<Long> rankedPlaylistIds = playlistScoreMap.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(actualLimit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

                 
        Map<Long, Playlist> playlistMap = new HashMap<>();
        if (!rankedPlaylistIds.isEmpty()) {
            LambdaQueryWrapper<Playlist> playlistWrapper = new LambdaQueryWrapper<>();
            playlistWrapper.in(Playlist::getId, rankedPlaylistIds)
                    .eq(Playlist::getDeleted, CommonConstants.NOT_DELETED);
            List<Playlist> playlists = playlistMapper.selectList(playlistWrapper);
            playlistMap.putAll(playlists.stream().collect(Collectors.toMap(Playlist::getId, p -> p)));
        }

        List<Map<String, Object>> rankedPlaylists = rankedPlaylistIds.stream()
                .map(plid -> {
                    Playlist playlist = playlistMap.get(plid);
                    if (playlist == null) {
                        return null;
                    }

                    User creator = userMap.get(playlist.getUserId());

                    Map<String, Object> playlistVO = new HashMap<>();
                    playlistVO.put("id", playlist.getId());
                    playlistVO.put("name", playlist.getName());
                    playlistVO.put("description", playlist.getDescription());
                    playlistVO.put("cover", playlist.getCover());
                    playlistVO.put("songCount", playlist.getSongCount());
                    playlistVO.put("playCount", playlist.getPlayCount());
                    playlistVO.put("favoriteCount", playlist.getFavoriteCount());
                    playlistVO.put("isPublic", playlist.getIsPublic());
                    playlistVO.put("creatorName", creator != null ?
                        (creator.getNickname() != null ? creator.getNickname() : creator.getUsername()) : "未知用户");
                    playlistVO.put("creatorAvatar", creator != null ? creator.getAvatar() : null);
                    playlistVO.put("score", playlistScoreMap.get(plid));
                    return playlistVO;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        result.put("playlists", rankedPlaylists);
        result.put("total", playlistScoreMap.size());

        return result;
    }

    @Override
    public Map<String, Object> getUserActiveTimeAnalysis(Long userId) {
        Map<String, Object> result = new HashMap<>();

                  
        Map<String, Integer> periodPlayCount = new HashMap<>();
        periodPlayCount.put("morning", 0);
        periodPlayCount.put("afternoon", 0);
        periodPlayCount.put("evening", 0);
        periodPlayCount.put("night", 0);

        Map<String, Map<String, Integer>> periodGenreCount = new HashMap<>();
        periodGenreCount.put("morning", new HashMap<>());
        periodGenreCount.put("afternoon", new HashMap<>());
        periodGenreCount.put("evening", new HashMap<>());
        periodGenreCount.put("night", new HashMap<>());

                       
        LambdaQueryWrapper<ListenHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ListenHistory::getUserId, userId)
                .eq(ListenHistory::getDeleted, CommonConstants.NOT_DELETED)
                .ge(ListenHistory::getCreateTime, LocalDateTime.now().minusDays(30))
                .last("LIMIT 5000");

        List<ListenHistory> histories = listenHistoryMapper.selectList(wrapper);

                   
        Set<Long> songIds = histories.stream()
                .map(ListenHistory::getSongId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Song> songMap = new HashMap<>();
        if (!songIds.isEmpty()) {
            LambdaQueryWrapper<Song> songWrapper = new LambdaQueryWrapper<>();
            songWrapper.in(Song::getId, songIds)
                    .eq(Song::getStatus, CommonConstants.STATUS_NORMAL)
                    .eq(Song::getDeleted, CommonConstants.NOT_DELETED);
            List<Song> songs = songMapper.selectList(songWrapper);
            songMap.putAll(songs.stream().collect(Collectors.toMap(Song::getId, s -> s)));
        }

        for (ListenHistory history : histories) {
            LocalDateTime playTime = history.getCreateTime();
            if (playTime == null) continue;

            int hour = playTime.getHour();
            String period = getPeriodByHour(hour);
            periodPlayCount.merge(period, 1, Integer::sum);

                         
            Song song = songMap.get(history.getSongId());
            if (song != null && ObjectUtils.isNotEmpty(song.getMainType())) {
                Map<String, Integer> genreMap = periodGenreCount.get(period);
                if (genreMap == null) {
                    genreMap = new HashMap<>();
                    periodGenreCount.put(period, genreMap);
                }
                genreMap.merge(song.getMainType(), 1, Integer::sum);
            }
        }

                  
        String mostActivePeriod = periodPlayCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("evening");

        result.put("periodPlayCount", periodPlayCount);
        result.put("periodGenrePreference", periodGenreCount);
        result.put("mostActivePeriod", mostActivePeriod);
        result.put("totalPlays", histories.size());

        return result;
    }

    @Override
    public Map<String, Object> getUserDeepPreferenceAnalysis(Long userId) {
        Map<String, Object> result = new HashMap<>();

                    
        Map<String, Integer> favoriteGenrePreference = new HashMap<>();
        Set<Long> userLikedSongIds = getUserLikedSongIds(userId);

                    
        LambdaQueryWrapper<ListenHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ListenHistory::getUserId, userId)
                .eq(ListenHistory::getDeleted, CommonConstants.NOT_DELETED)
                .ge(ListenHistory::getCreateTime, LocalDateTime.now().minusDays(30))
                .last("LIMIT 1000");

        List<ListenHistory> histories = listenHistoryMapper.selectList(wrapper);

                    
        Set<Long> userPlaylistSongIds = getUserPlaylistSongIds(userId);

                     
        Set<Long> allSongIds = new HashSet<>();
        allSongIds.addAll(userLikedSongIds);
        allSongIds.addAll(userPlaylistSongIds);
        histories.stream().map(ListenHistory::getSongId).filter(Objects::nonNull).forEach(allSongIds::add);

        Map<Long, Song> songMap = new HashMap<>();
        if (!allSongIds.isEmpty()) {
            LambdaQueryWrapper<Song> songWrapper = new LambdaQueryWrapper<>();
            songWrapper.in(Song::getId, allSongIds)
                    .eq(Song::getStatus, CommonConstants.STATUS_NORMAL)
                    .eq(Song::getDeleted, CommonConstants.NOT_DELETED);
            List<Song> songs = songMapper.selectList(songWrapper);
            songMap.putAll(songs.stream().collect(Collectors.toMap(Song::getId, s -> s)));
        }

                 
        for (Long sid : userLikedSongIds) {
            Song song = songMap.get(sid);
            if (song != null && ObjectUtils.isNotEmpty(song.getMainType())) {
                favoriteGenrePreference.merge(song.getMainType(), 1, Integer::sum);
            }
        }

                 
        Map<String, Integer> playGenrePreference = new HashMap<>();
        for (ListenHistory history : histories) {
            Song song = songMap.get(history.getSongId());
            if (song != null && ObjectUtils.isNotEmpty(song.getMainType())) {
                playGenrePreference.merge(song.getMainType(), 1, Integer::sum);
            }
        }

                 
        Map<String, Integer> playlistGenrePreference = new HashMap<>();
        for (Long sid : userPlaylistSongIds) {
            Song song = songMap.get(sid);
            if (song != null && ObjectUtils.isNotEmpty(song.getMainType())) {
                playlistGenrePreference.merge(song.getMainType(), 1, Integer::sum);
            }
        }

                                          
        Map<String, Double> comprehensivePreference = new HashMap<>();
        addWeightedPreference(comprehensivePreference, favoriteGenrePreference, 0.4);
        addWeightedPreference(comprehensivePreference, playGenrePreference, 0.4);
        addWeightedPreference(comprehensivePreference, playlistGenrePreference, 0.2);

                     
        List<String> topGenres = comprehensivePreference.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        result.put("favoriteGenrePreference", favoriteGenrePreference);
        result.put("playGenrePreference", playGenrePreference);
        result.put("playlistGenrePreference", playlistGenrePreference);
        result.put("comprehensivePreference", comprehensivePreference);
        result.put("topGenres", topGenres);
        result.put("analysisTime", LocalDateTime.now());

        return result;
    }

                                                       

    private Map<String, Object> getUserMusicPreferenceWithHistory(Long userId) {
        Map<String, Object> preference = new HashMap<>();

                      
        Map<String, Integer> favoriteGenreCount = new HashMap<>();
        LambdaQueryWrapper<SongLike> likeWrapper = new LambdaQueryWrapper<>();
        likeWrapper.eq(SongLike::getUserId, userId)
                .eq(SongLike::getIsFavorite, 1)
                .eq(SongLike::getDeleted, CommonConstants.NOT_DELETED)
                .last("LIMIT 50");

        List<SongLike> songLikes = songLikeMapper.selectList(likeWrapper);

                      
        Map<String, Integer> playGenreCount = new HashMap<>();
        LambdaQueryWrapper<ListenHistory> historyWrapper = new LambdaQueryWrapper<>();
        historyWrapper.eq(ListenHistory::getUserId, userId)
                .eq(ListenHistory::getDeleted, CommonConstants.NOT_DELETED)
                .ge(ListenHistory::getCreateTime, LocalDateTime.now().minusDays(30))
                .last("LIMIT 100");

        List<ListenHistory> histories = listenHistoryMapper.selectList(historyWrapper);

                   
        Set<Long> allSongIds = new HashSet<>();
        songLikes.stream().map(SongLike::getSongId).filter(Objects::nonNull).forEach(allSongIds::add);
        histories.stream().map(ListenHistory::getSongId).filter(Objects::nonNull).forEach(allSongIds::add);

        Map<Long, Song> songMap = new HashMap<>();
        if (!allSongIds.isEmpty()) {
            LambdaQueryWrapper<Song> songWrapper = new LambdaQueryWrapper<>();
            songWrapper.in(Song::getId, allSongIds)
                    .eq(Song::getStatus, CommonConstants.STATUS_NORMAL)
                    .eq(Song::getDeleted, CommonConstants.NOT_DELETED);
            List<Song> songs = songMapper.selectList(songWrapper);
            songMap.putAll(songs.stream().collect(Collectors.toMap(Song::getId, s -> s)));
        }

        for (SongLike songLike : songLikes) {
            Song song = songMap.get(songLike.getSongId());
            if (song != null && ObjectUtils.isNotEmpty(song.getMainType())) {
                favoriteGenreCount.merge(song.getMainType(), 2, Integer::sum);
            }
        }

        for (ListenHistory history : histories) {
            Song song = songMap.get(history.getSongId());
            if (song != null && ObjectUtils.isNotEmpty(song.getMainType())) {
                playGenreCount.merge(song.getMainType(), 1, Integer::sum);
            }
        }

               
        Map<String, Integer> totalGenreCount = new HashMap<>();
        for (Map.Entry<String, Integer> entry : favoriteGenreCount.entrySet()) {
            totalGenreCount.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
        for (Map.Entry<String, Integer> entry : playGenreCount.entrySet()) {
            totalGenreCount.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }

                       
        List<String> favoriteGenres = totalGenreCount.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (favoriteGenres.isEmpty()) {
            favoriteGenres = Arrays.asList("Pop", "Rock", "Folk");
        }

        preference.put("favoriteGenres", favoriteGenres);
        preference.put("genreDistribution", totalGenreCount);

        return preference;
    }

    private Set<Long> getUserPlaylistSongIds(Long userId) {
        Set<Long> songIds = new HashSet<>();

        LambdaQueryWrapper<Playlist> playlistWrapper = new LambdaQueryWrapper<>();
        playlistWrapper.eq(Playlist::getUserId, userId)
                .eq(Playlist::getDeleted, CommonConstants.NOT_DELETED);

        List<Playlist> userPlaylists = playlistMapper.selectList(playlistWrapper);

        for (Playlist playlist : userPlaylists) {
            List<Long> playlistSongIdList = getPlaylistSongIds(playlist.getId());
            songIds.addAll(playlistSongIdList);
        }

        return songIds;
    }

    private List<Long> getPlaylistSongIds(Long playlistId) {
        LambdaQueryWrapper<PlaylistSong> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PlaylistSong::getPlaylistId, playlistId)
                .eq(PlaylistSong::getDeleted, CommonConstants.NOT_DELETED);

        return playlistSongMapper.selectList(wrapper).stream()
                .map(PlaylistSong::getSongId)
                .collect(Collectors.toList());
    }

    private String getPeriodByHour(int hour) {
        if (hour >= 6 && hour < 12) {
            return "morning";
        } else if (hour >= 12 && hour < 18) {
            return "afternoon";
        } else if (hour >= 18 && hour < 24) {
            return "evening";
        } else {
            return "night";
        }
    }

    private void addWeightedPreference(Map<String, Double> target, Map<String, Integer> source, double weight) {
        for (Map.Entry<String, Integer> entry : source.entrySet()) {
            target.merge(entry.getKey(), entry.getValue() * weight, Double::sum);
        }
    }

    private double calculateSongScore(Song song, Long userId, String userGenre) {
        double score = 0.0;

        if (userGenre.equals(song.getMainType())) {
            score += 50 * PREFERENCE_SCORE_WEIGHT;
        } else {
            score += 20 * PREFERENCE_SCORE_WEIGHT;
        }

        long playCount = song.getPlayCount() != null ? song.getPlayCount() : 0;
        double playScore = Math.min(Math.log(playCount + 1) * 10, 100);
        score += playScore * PLAY_COUNT_WEIGHT;

        Integer hotScore = song.getHotScore();
        double hotScoreValue = hotScore != null ? Math.min(hotScore, 100.0) : 0;
        score += hotScoreValue * HOT_SCORE_WEIGHT;

        if (song.getIsNew() != null && song.getIsNew() == 1) {
            score += 10 * NEW_SONG_BOOST;
        }

        if (userGenre.equals(song.getMainType())) {
            score += 5;
        }

        log.debug("计算歌曲得分: userId={}, songId={}, genre={}, score={}", userId, song.getId(), userGenre, score);
        return score;
    }

    private Set<Long> getUserLikedSongIds(Long userId) {
        LambdaQueryWrapper<SongLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SongLike::getUserId, userId)
                .eq(SongLike::getIsFavorite, 1)
                .eq(SongLike::getDeleted, CommonConstants.NOT_DELETED);

        return songLikeMapper.selectList(wrapper).stream()
                .map(SongLike::getSongId)
                .collect(Collectors.toSet());
    }

    private List<Long> getFriendLikedSongs(Long friendId) {
        LambdaQueryWrapper<SongLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SongLike::getUserId, friendId)
                .eq(SongLike::getIsFavorite, 1)
                .eq(SongLike::getDeleted, CommonConstants.NOT_DELETED)
                .last("LIMIT 50");

        return songLikeMapper.selectList(wrapper).stream()
                .map(SongLike::getSongId)
                .collect(Collectors.toList());
    }

    private Set<Long> getFollowingCreatorUserIds(Long userId) {
        Set<Long> creatorUserIds = new HashSet<>();

        Set<Long> followingUserIds = getFollowingUsers(userId);

        if (ObjectUtils.isEmpty(followingUserIds)) {
            return creatorUserIds;
        }

        LambdaQueryWrapper<Creator> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Creator::getUserId, followingUserIds)
                .eq(Creator::getStatus, "active")
                .eq(Creator::getDeleted, CommonConstants.NOT_DELETED);

        List<Creator> creators = creatorMapper.selectList(wrapper);
        for (Creator creator : creators) {
            creatorUserIds.add(creator.getUserId());
        }

        return filterPublicSignalUserIds(creatorUserIds);
    }

    private Set<Long> getMutualFriends(Long userId) {
        Set<Long> friends = new HashSet<>();

        Set<Long> following = getFollowingUsers(userId);
        Set<Long> followers = getFollowers(userId);

        friends.addAll(following);
        friends.retainAll(followers);

        return friends;
    }

    private Set<Long> getFollowingUsers(Long userId) {
        LambdaQueryWrapper<UserFollow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFollow::getFollowerId, userId)
                .eq(UserFollow::getDeleted, CommonConstants.NOT_DELETED);

        Set<Long> userIds = userFollowMapper.selectList(wrapper).stream()
                .map(UserFollow::getFolloweeId)
                .collect(Collectors.toSet());
        return filterPublicSignalUserIds(userIds);
    }

    private Set<Long> getFollowers(Long userId) {
        LambdaQueryWrapper<UserFollow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFollow::getFolloweeId, userId)
                .eq(UserFollow::getDeleted, CommonConstants.NOT_DELETED);

        Set<Long> userIds = userFollowMapper.selectList(wrapper).stream()
                .map(UserFollow::getFollowerId)
                .collect(Collectors.toSet());
        return filterPublicSignalUserIds(userIds);
    }

    private Set<Long> filterPublicSignalUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new HashSet<>();
        }
        List<User> users = userMapper.selectBatchIds(userIds);
        return users.stream()
                .filter(UserAccountStatusUtil::canContributePublicStats)
                .map(User::getId)
                .collect(Collectors.toSet());
    }

    private List<Playlist> filterPublicCreatorPlaylists(List<Playlist> playlists) {
        if (playlists == null || playlists.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> creatorIds = playlists.stream()
                .map(Playlist::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = userMapper.selectBatchIds(creatorIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user, (a, b) -> a));
        return playlists.stream()
                .filter(playlist -> UserAccountStatusUtil.canExposePublicContent(userMap.get(playlist.getUserId())))
                .collect(Collectors.toList());
    }

    private List<Song> filterPublicUploaderSongs(List<Song> songs) {
        if (songs == null || songs.isEmpty()) {
            return new ArrayList<>();
        }

        Set<Long> uploaderIds = songs.stream()
                .map(Song::getUploaderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> uploaderMap = uploaderIds.isEmpty()
                ? Collections.emptyMap()
                : userMapper.selectBatchIds(uploaderIds).stream()
                        .collect(Collectors.toMap(User::getId, user -> user, (a, b) -> a));

        return songs.stream()
                .filter(song -> song.getUploaderId() == null
                        || UserAccountStatusUtil.canExposePublicContent(uploaderMap.get(song.getUploaderId())))
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private List<String> getStringList(Map<String, Object> map, String key, List<String> defaultValue) {
        Object value = map.get(key);
        if (value instanceof List) {
            return (List<String>) value;
        }
        return defaultValue;
    }
}
