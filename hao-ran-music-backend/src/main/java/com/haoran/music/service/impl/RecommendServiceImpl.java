package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.*;
import com.haoran.music.mapper.*;
import com.haoran.music.service.RecommendService;
import com.haoran.music.service.SongLikeService;
import com.haoran.music.service.MusicIntelligenceCacheService;
import com.haoran.music.vo.recommend.RecommendVO;
import com.haoran.music.vo.recommend.RecommendedSongVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

   
                      
                              
   
@Slf4j
@Service
public class RecommendServiceImpl implements RecommendService {

    private static final String RECOMMEND_CACHE_PREFIX = "recommend:";
    private static final String USER_PROFILE_PREFIX = "user:profile:";
    private static final Integer DEFAULT_RECOMMEND_COUNT = 16;

    @Resource
    private SongMapper songMapper;

    @Resource
    private SongLikeMapper songLikeMapper;

    @Resource
    private ListenHistoryMapper listenHistoryMapper;

    @Resource
    private UserFavoriteMapper userFavoriteMapper;

    @Resource
    private ArtistMapper artistMapper;

    @Resource
    private AlbumMapper albumMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private com.haoran.music.mapper.PlaylistMapper PlaylistMapper;

    @Resource
    private SongArtistMapper songArtistMapper;

    @Resource
    private PlaylistSongMapper playlistSongMapper;


    @Resource
    private com.haoran.music.mapper.MVMapper MVMapper;
    private SongLikeService songLikeService;
    private com.haoran.music.service.SocialRecommendService socialRecommendService;


    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private MusicIntelligenceCacheService musicIntelligenceCacheService;

    @Override
    public RecommendVO getDailyRecommend(Long userId) {
        log.debug("获取每日推荐, userId={}", userId);

        String cacheKey = RECOMMEND_CACHE_PREFIX + musicIntelligenceCacheService.recommendVersionSegment()
                + "daily:" + userId;
        RecommendVO cached = (RecommendVO) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }

        RecommendVO result = new RecommendVO();
        result.setType("daily");
        result.setSource("daily");
        result.setSourceName("每日推荐");
        result.setReason("根据你的口味精选推荐");

        List<RecommendVO.SongSimpleVO> recommendations = new ArrayList<>();

                              
        recommendations.addAll(getPersonalizedSongs(userId, 6));

                                 
        recommendations.addAll(getNewSongs(5));

                        
        recommendations.addAll(getHotSongs(4));

                        
        recommendations.addAll(getRandomSongs(1));

                  
        List<RecommendVO.SongSimpleVO> uniqueSongs = removeDuplicates(recommendations, DEFAULT_RECOMMEND_COUNT);

                 
        fillFavoriteStatus(userId, uniqueSongs);

        result.setSongs(uniqueSongs);

                
        redisTemplate.opsForValue().set(cacheKey, result, 6, TimeUnit.HOURS);

        return result;
    }

    @Override
    public RecommendVO getPersonalRecommend(Long userId, Integer limit) {
        log.debug("获取个性化推荐, userId={}, limit={}", userId, limit);
        if (limit == null || limit <= 0) {
            limit = DEFAULT_RECOMMEND_COUNT;
        }

        RecommendVO result = new RecommendVO();
        result.setType("personal");
        result.setSource("personal");
        result.setSourceName("个性化推荐");
        result.setReason("懂你喜欢的");

        List<RecommendVO.SongSimpleVO> recommendations = new ArrayList<>();

                   
        List<String> preferredTypes = getUserPreferredTypes(userId);
        List<Long> listenedSongIds = getUserListenedSongIds(userId);

                   
        if (!listenedSongIds.isEmpty()) {
            recommendations.addAll(getSimilarSongsByTypes(listenedSongIds, preferredTypes, (int) (limit * 0.5)));
        }

                          
        if (!preferredTypes.isEmpty()) {
            recommendations.addAll(getHotSongsByTypes(preferredTypes, (int) (limit * 0.25)));
        }

                            
        recommendations.addAll(getNewSongs((int) (limit * 0.15)));

                   
        recommendations.addAll(getRandomSongs((int) (limit * 0.1)));

        List<RecommendVO.SongSimpleVO> uniqueSongs = removeDuplicates(recommendations, limit);
        fillFavoriteStatus(userId, uniqueSongs);

        result.setSongs(uniqueSongs);
        return result;
    }

    @Override
    public RecommendVO getDiscoverRecommend(Long userId, Integer limit) {
        log.debug("获取发现推荐, userId={}, limit={}", userId, limit);
        if (limit == null || limit <= 0) {
            limit = DEFAULT_RECOMMEND_COUNT;
        }

        RecommendVO result = new RecommendVO();
        result.setType("discover");
        result.setSource("discover");
        result.setSourceName("发现音乐");
        result.setReason("探索未知的精彩");

        List<RecommendVO.SongSimpleVO> recommendations = new ArrayList<>();

                 
        recommendations.addAll(getNewSongs((int) (limit * 0.4)));

                   
        recommendations.addAll(getHiddenGems((int) (limit * 0.3)));

                    
        recommendations.addAll(getCrossGenreRecommend(userId, (int) (limit * 0.2)));

                   
        recommendations.addAll(getRandomSongs((int) (limit * 0.1)));

        List<RecommendVO.SongSimpleVO> uniqueSongs = removeDuplicates(recommendations, limit);
        fillFavoriteStatus(userId, uniqueSongs);

        result.setSongs(uniqueSongs);
        return result;
    }

    @Override
    public RecommendVO getSimilarSongs(Long userId, Long songId, Integer limit) {
        log.debug("获取相似推荐, userId={}, songId={}, limit={}", userId, songId, limit);
        if (limit == null || limit <= 0) {
            limit = 8;
        }

        Song song = songMapper.selectById(songId);
        if (song == null) {
            return new RecommendVO();
        }

        RecommendVO result = new RecommendVO();
        result.setType("similar");
        result.setSource("similar");
        result.setSourceName("相似推荐");
        result.setReason("因为你喜欢《" + song.getName() + "》");

        List<RecommendVO.SongSimpleVO> recommendations = getSimilarSongsBySong(song, limit);
        fillFavoriteStatus(userId, recommendations);

        result.setSongs(recommendations);
        return result;
    }

    @Override
    public RecommendVO getArtistRecommend(Long userId, Long artistId, Integer limit) {
        log.debug("获取歌手推荐, userId={}, artistId={}, limit={}", userId, artistId, limit);
        if (limit == null || limit <= 0) {
            limit = 10;
        }

        Artist artist = artistMapper.selectById(artistId);
        if (artist == null) {
            return new RecommendVO();
        }

        RecommendVO result = new RecommendVO();
        result.setType("artist");
        result.setSource("artist");
        result.setSourceName("歌手推荐");
        result.setReason("来自 " + artist.getName() + " 的更多作品");

        List<RecommendVO.SongSimpleVO> recommendations = getSongsByArtist(artistId, limit);
        fillFavoriteStatus(userId, recommendations);

        result.setSongs(recommendations);
        return result;
    }
    @Override
    public RecommendVO getSocialRecommend(Long userId, Integer limit) {
        log.debug("获取社交推荐, userId={}, limit={}", userId, limit);
        if (limit == null || limit <= 0) {
            limit = DEFAULT_RECOMMEND_COUNT;
        }

                   
        RecommendVO result = socialRecommendService.getSocialBasedRecommend(userId, limit);
        
                 
        if (result.getSongs() != null && !result.getSongs().isEmpty()) {
            fillFavoriteStatus(userId, result.getSongs());
        }

        return result;
    }

    @Override
    public void refreshUserRecommendProfile(Long userId) {
        log.debug("刷新用户推荐画像, userId={}", userId);
        if (userId != null && !canUseRecommendProfile(userId)) {
            log.debug("跳过非正常用户推荐画像刷新, userId={}", userId);
            return;
        }

        deleteCurrentRecommendUserCache(userId);

                   
        rebuildUserProfile(userId);
    }

    @Override
    public List<String> getUserPreferenceTags(Long userId) {
        log.debug("获取用户偏好标签, userId={}", userId);

        String cacheKey = USER_PROFILE_PREFIX + musicIntelligenceCacheService.recommendVersionSegment()
                + "tags:" + userId;
        List<String> cached = (List<String>) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<String> tags = new ArrayList<>();

                      
        List<String> preferredTypes = getUserPreferredTypes(userId);
        tags.addAll(preferredTypes);

                            
        List<Long> topArtistIds = getTopPreferredArtists(userId, 3);
        if (!topArtistIds.isEmpty()) {
            LambdaQueryWrapper<Artist> wrapper = new LambdaQueryWrapper<>();
            wrapper.in(Artist::getId, topArtistIds)
                    .eq(Artist::getDeleted, CommonConstants.NOT_DELETED);
            List<Artist> artists = artistMapper.selectList(wrapper);
            artists.forEach(artist -> {
                if (artist.getName() != null) {
                    tags.add(artist.getName());
                }
            });
        }

        redisTemplate.opsForValue().set(cacheKey, tags, 1, TimeUnit.HOURS);
        return tags;
    }

    @Override
    public void recordUserAction(Long userId, String actionType, Long targetId, Integer targetType) {
        log.debug("记录用户行为, userId={}, actionType={}, targetId={}, targetType={}",
                  userId, actionType, targetId, targetType);
        if (!canUseRecommendProfile(userId)) {
            log.debug("跳过非正常用户推荐行为记录, userId={}, actionType={}", userId, actionType);
            return;
        }

                     
        updateUserProfileAsync(userId, actionType, targetId, targetType);
    }

    private boolean canUseRecommendProfile(Long userId) {
        return UserAccountStatusUtil.canContributePublicStats(userId, userMapper::selectById);
    }

    @Override
    public List<RecommendedSongVO> getPersonalRecommendWithReason(Long userId, Integer limit) {
        log.debug("获取个性化推荐(带理由), userId={}, limit={}", userId, limit);
        if (limit == null || limit <= 0) {
            limit = DEFAULT_RECOMMEND_COUNT;
        }

        RecommendVO vo = getPersonalRecommend(userId, limit);
        return convertToRecommendedSongVO(vo.getSongs(), "personal");
    }

    @Override
    public List<RecommendedSongVO> getDailyDiscoveryWithReason(Long userId, Integer limit) {
        log.debug("获取每日发现推荐(带理由), userId={}, limit={}", userId, limit);
        if (limit == null || limit <= 0) {
            limit = DEFAULT_RECOMMEND_COUNT;
        }

        RecommendVO vo = getDiscoverRecommend(userId, limit);
        return convertToRecommendedSongVO(vo.getSongs(), "discovery");
    }

    @Override
    public List<RecommendedSongVO> getSimilarRecommendWithReason(Long userId, Long songId, Integer limit) {
        log.debug("获取相似推荐(带理由), userId={}, songId={}, limit={}", userId, songId, limit);
        if (limit == null || limit <= 0) {
            limit = 8;
        }

        RecommendVO vo = getSimilarSongs(userId, songId, limit);
        List<RecommendedSongVO> result = convertToRecommendedSongVO(vo.getSongs(), "similar");

                   
        Song refSong = songMapper.selectById(songId);
        if (refSong != null) {
            RecommendedSongVO.RelatedSongInfo relatedInfo = new RecommendedSongVO.RelatedSongInfo();
            relatedInfo.setId(refSong.getId());
            relatedInfo.setName(refSong.getName());
            relatedInfo.setCover(refSong.getCover());
            relatedInfo.setArtistName(refSong.getArtistNames());
            result.forEach(item -> item.setRelatedSong(relatedInfo));
        }

        return result;
    }

    @Override
    public List<Long> getPersonalizedPlaylists(Long userId, Integer limit) {
        log.debug("获取个性化歌单推荐, userId={}, limit={}", userId, limit);
        if (limit == null || limit <= 0) {
            limit = 10;
        }

                      
        List<String> preferredTypes = getUserPreferredTypes(userId);
        List<Long> playlistIds = new ArrayList<>();

        if (!preferredTypes.isEmpty()) {
                           
                            
            LambdaQueryWrapper<Playlist> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Playlist::getIsPublic, 1)
                   .eq(Playlist::getDeleted, 0)
                   .gt(Playlist::getSongCount, 5)
                   .eq(Playlist::getStatus, 1)
                   .orderByDesc(Playlist::getFavoriteCount)
                   .last("LIMIT " + candidateLimit(limit));             

            List<Playlist> playlists = filterPublicCreatorPlaylists(PlaylistMapper.selectList(wrapper));

                                 
            if (!playlists.isEmpty()) {
                List<Long> allPlaylistIds = playlists.stream()
                        .map(Playlist::getId)
                        .collect(Collectors.toList());

                             
                LambdaQueryWrapper<PlaylistSong> psWrapper = new LambdaQueryWrapper<>();
                psWrapper.in(PlaylistSong::getPlaylistId, allPlaylistIds)
                          .eq(PlaylistSong::getDeleted, 0)
                          .last("LIMIT 1000");

                List<PlaylistSong> playlistSongs = playlistSongMapper.selectList(psWrapper);

                          
                Map<Long, List<Long>> playlistSongMap = playlistSongs.stream()
                        .collect(Collectors.groupingBy(
                                PlaylistSong::getPlaylistId,
                                Collectors.mapping(PlaylistSong::getSongId, Collectors.toList())
                        ));

                                   
                if (!playlistSongMap.isEmpty()) {
                    List<Long> allSongIds = playlistSongMap.values().stream()
                            .flatMap(List::stream)
                            .distinct()
                            .collect(Collectors.toList());

                                      
                    Map<Long, Song> songMap = new HashMap<>();
                    if (!allSongIds.isEmpty()) {
                        LambdaQueryWrapper<Song> songWrapper = new LambdaQueryWrapper<>();
                        songWrapper.in(Song::getId, allSongIds)
                                .eq(Song::getStatus, CommonConstants.STATUS_NORMAL)
                                .eq(Song::getDeleted, CommonConstants.NOT_DELETED);
                        List<Song> songs = songMapper.selectList(songWrapper);
                        songMap = songs.stream().collect(Collectors.toMap(Song::getId, s -> s));
                    }

                                  
                    Map<Long, Integer> playlistScoreMap = new HashMap<>();
                    for (Map.Entry<Long, List<Long>> entry : playlistSongMap.entrySet()) {
                        Long playlistId = entry.getKey();
                        List<Long> songIds = entry.getValue();

                        int matchCount = 0;
                        for (Long songId : songIds) {
                            Song song = songMap.get(songId);
                            if (song != null && preferredTypes.contains(song.getMainType())) {
                                matchCount++;
                            }
                        }

                        if (matchCount > 0) {
                            playlistScoreMap.put(playlistId, matchCount);
                        }
                    }

                                       
                    playlistIds = playlistScoreMap.entrySet().stream()
                            .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                            .limit(limit)
                            .map(Map.Entry::getKey)
                            .collect(Collectors.toList());
                }
            }
        }

                          
        if (playlistIds.isEmpty()) {
            LambdaQueryWrapper<Playlist> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Playlist::getIsPublic, 1)
                   .eq(Playlist::getDeleted, 0)
                   .eq(Playlist::getStatus, 1)
                   .orderByDesc(Playlist::getFavoriteCount, Playlist::getPlayCount)
                   .last("LIMIT " + candidateLimit(limit));

            List<Playlist> hotPlaylists = filterPublicCreatorPlaylists(PlaylistMapper.selectList(wrapper));
            playlistIds = hotPlaylists.stream()
                    .limit(limit)
                    .map(Playlist::getId)
                    .collect(Collectors.toList());
        }

        return playlistIds;
    }

    @Override
    public List<Long> getPersonalizedAlbums(Long userId, Integer limit) {
        log.debug("获取个性化专辑推荐, userId={}, limit={}", userId, limit);
        if (limit == null || limit <= 0) {
            limit = 5;
        }

                     
        List<String> preferredTypes = getUserPreferredTypes(userId);
        List<Long> albumIds = new ArrayList<>();

        if (!preferredTypes.isEmpty()) {
            LambdaQueryWrapper<Album> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Album::getStatus, 1)
                   .eq(Album::getDeleted, 0)
                   .in(Album::getType, preferredTypes)
                   .orderByDesc(Album::getPlayCount)
                   .last("LIMIT " + candidateLimit(limit));

            List<Album> albums = filterAlbumsWithPublicSongs(albumMapper.selectList(wrapper));
            albumIds = albums.stream().limit(limit).map(Album::getId).collect(Collectors.toList());
        }

        return albumIds;
    }


    @Override
    public List<Long> getPersonalizedMVs(Long userId, Integer limit) {
        log.debug("获取个性化MV推荐, userId={}, limit={}", userId, limit);
        if (limit == null || limit <= 0) {
            limit = 10;
        }

                     
        List<String> preferredTypes = getUserPreferredTypes(userId);
        List<Long> preferredArtistIds = getTopPreferredArtists(userId, 5);

        LambdaQueryWrapper<com.haoran.music.entity.MV> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(com.haoran.music.entity.MV::getStatus, 1)
               .eq(com.haoran.music.entity.MV::getDeleted, 0);

                                     
        if (!preferredTypes.isEmpty()) {
                                     
            wrapper.apply("CONCAT(',', IFNULL(tags, ''), ',') LIKE CONCAT('%%', {0}, '%%')",
                    String.join("%' OR tags LIKE '%", preferredTypes));
        }

                      
        if (!preferredArtistIds.isEmpty()) {
            wrapper.or(w -> w.in(com.haoran.music.entity.MV::getArtistId, preferredArtistIds));
        }

        wrapper.orderByDesc(com.haoran.music.entity.MV::getPlayCount)
               .last("LIMIT " + candidateLimit(limit));              

        List<com.haoran.music.entity.MV> mvs = filterPublicMvs(MVMapper.selectList(wrapper));

                   
        Collections.shuffle(mvs);
        return mvs.stream()
                .limit(limit)
                .map(com.haoran.music.entity.MV::getId)
                .collect(Collectors.toList());
    }
                                                       

       
                      
       
    private List<RecommendVO.SongSimpleVO> getPersonalizedSongs(Long userId, Integer count) {
        List<String> preferredTypes = getUserPreferredTypes(userId);
        if (preferredTypes.isEmpty()) {
            return getHotSongs(count);
        }

        return getHotSongsByTypes(preferredTypes, count);
    }

       
                  
       
    private List<String> getUserPreferredTypes(Long userId) {
        String cacheKey = USER_PROFILE_PREFIX + musicIntelligenceCacheService.recommendVersionSegment()
                + "types:" + userId;
        List<String> cached = (List<String>) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }

                     
        LambdaQueryWrapper<ListenHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ListenHistory::getUserId, userId)
               .orderByDesc(ListenHistory::getCreateTime)
               .last("LIMIT 100");

        List<ListenHistory> histories = listenHistoryMapper.selectList(wrapper);

                          
        List<Long> songIds = histories.stream()
                .map(ListenHistory::getSongId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<String, Integer> typeCount = new HashMap<>();
        if (!songIds.isEmpty()) {
            LambdaQueryWrapper<Song> songWrapper = new LambdaQueryWrapper<>();
            songWrapper.in(Song::getId, songIds)
                    .eq(Song::getStatus, CommonConstants.STATUS_NORMAL)
                    .eq(Song::getDeleted, CommonConstants.NOT_DELETED)
                    .select(Song::getId, Song::getMainType, Song::getUploaderId);
            List<Song> songs = filterPublicUploaderSongs(songMapper.selectList(songWrapper));
            Map<Long, String> songTypeMap = songs.stream()
                    .collect(Collectors.toMap(Song::getId, Song::getMainType));

            for (ListenHistory history : histories) {
                String mainType = songTypeMap.get(history.getSongId());
                if (StringUtils.isNotBlank(mainType)) {
                    typeCount.merge(mainType, 1, Integer::sum);
                }
            }
        }

                   
        List<String> preferredTypes = typeCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (!preferredTypes.isEmpty()) {
            redisTemplate.opsForValue().set(cacheKey, preferredTypes, 2, TimeUnit.HOURS);
        }

        return preferredTypes;
    }

       
                   
       
    private List<Long> getUserListenedSongIds(Long userId) {
        LambdaQueryWrapper<ListenHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ListenHistory::getUserId, userId)
               .select(ListenHistory::getSongId)
               .isNotNull(ListenHistory::getSongId)
               .groupBy(ListenHistory::getSongId)
               .last("LIMIT 50");

        List<ListenHistory> histories = listenHistoryMapper.selectList(wrapper);
        return histories.stream()
                .map(ListenHistory::getSongId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

       
                
       
    private List<Long> getTopPreferredArtists(Long userId, Integer count) {
        LambdaQueryWrapper<SongLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SongLike::getUserId, userId)
               .eq(SongLike::getIsFavorite, 1)
               .eq(SongLike::getDeleted, CommonConstants.NOT_DELETED)
               .orderByDesc(SongLike::getCreateTime)
               .last("LIMIT 50");

        List<SongLike> likes = songLikeMapper.selectList(wrapper);

                             
        List<Long> songIds = likes.stream()
                .map(SongLike::getSongId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, Integer> artistCount = new HashMap<>();
        if (!songIds.isEmpty()) {
            LambdaQueryWrapper<SongArtist> saWrapper = new LambdaQueryWrapper<>();
            saWrapper.in(SongArtist::getSongId, songIds);
            List<SongArtist> songArtists = songArtistMapper.selectList(saWrapper);

            for (SongArtist sa : songArtists) {
                artistCount.merge(sa.getArtistId(), 1, Integer::sum);
            }
        }

        return artistCount.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                .limit(count)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

       
             
       
    private List<RecommendVO.SongSimpleVO> getHotSongs(Integer count) {
        if (count == null || count <= 0) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Song::getStatus, 1)
               .eq(Song::getDeleted, 0)
               .eq(Song::getIsHot, 1)
               .orderByDesc(Song::getPlayCount, Song::getFavoriteCount)
               .last("LIMIT " + candidateLimit(count));

        return convertToSimpleVO(songMapper.selectList(wrapper)).stream().limit(count).collect(Collectors.toList());
    }

       
                
       
    private List<RecommendVO.SongSimpleVO> getHotSongsByTypes(List<String> types, Integer count) {
        if (count == null || count <= 0) {
            return Collections.emptyList();
        }
        if (types == null || types.isEmpty()) {
            return getHotSongs(count);
        }

        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Song::getStatus, 1)
               .eq(Song::getDeleted, 0)
               .in(Song::getMainType, types)
               .orderByDesc(Song::getPlayCount)
               .last("LIMIT " + candidateLimit(count));

        return convertToSimpleVO(songMapper.selectList(wrapper)).stream().limit(count).collect(Collectors.toList());
    }

       
           
       
    private List<RecommendVO.SongSimpleVO> getNewSongs(Integer count) {
        if (count == null || count <= 0) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Song::getStatus, 1)
               .eq(Song::getDeleted, 0)
               .ge(Song::getCreateTime, java.time.LocalDateTime.now().minusDays(30))
               .orderByDesc(Song::getCreateTime)
               .last("LIMIT " + candidateLimit(count));

        return convertToSimpleVO(songMapper.selectList(wrapper)).stream().limit(count).collect(Collectors.toList());
    }

       
                                  
       
    private List<RecommendVO.SongSimpleVO> getRandomSongs(Integer count) {
        if (count == null || count <= 0) {
            return Collections.emptyList();
        }
                               
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

                     
        int queryLimit = candidateLimit(count);
        List<Long> targetIds = randomIds.stream()
                .limit(queryLimit)
                .collect(Collectors.toList());

               
        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Song::getId, targetIds)
                .eq(Song::getStatus, 1)
                .eq(Song::getDeleted, 0);

        List<Song> songs = songMapper.selectList(wrapper);
        Collections.shuffle(songs);
        return convertToSimpleVO(songs).stream().limit(count).collect(Collectors.toList());
    }

       
             
       
    private List<RecommendVO.SongSimpleVO> getHiddenGems(Integer count) {
        if (count == null || count <= 0) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Song::getStatus, 1)
               .eq(Song::getDeleted, 0)
               .gt(Song::getPlayCount, 100)
               .lt(Song::getPlayCount, 5000)
               .gt(Song::getFavoriteCount, 10)
               .orderByDesc(Song::getPlayCount)
               .last("LIMIT " + candidateLimit(count));

        return convertToSimpleVO(songMapper.selectList(wrapper)).stream().limit(count).collect(Collectors.toList());
    }

       
            
       
    private List<RecommendVO.SongSimpleVO> getCrossGenreRecommend(Long userId, Integer count) {
        if (count == null || count <= 0) {
            return Collections.emptyList();
        }
        List<String> preferredTypes = getUserPreferredTypes(userId);

        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Song::getStatus, 1)
               .eq(Song::getDeleted, 0);

        if (!preferredTypes.isEmpty()) {
            wrapper.notIn(Song::getMainType, preferredTypes);
        }

        wrapper.orderByDesc(Song::getPlayCount)
               .last("LIMIT " + candidateLimit(count));

        return convertToSimpleVO(songMapper.selectList(wrapper)).stream().limit(count).collect(Collectors.toList());
    }

       
                   
       
    private List<RecommendVO.SongSimpleVO> getSimilarSongsByTypes(List<Long> songIds, List<String> preferredTypes, Integer count) {
        if (count == null || count <= 0) {
            return Collections.emptyList();
        }
        if (songIds.isEmpty()) {
            return getHotSongsByTypes(preferredTypes, count);
        }

                            
        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Song::getStatus, 1)
               .eq(Song::getDeleted, 0)
               .notIn(Song::getId, songIds);

        if (!preferredTypes.isEmpty()) {
            wrapper.or(w -> w.in(Song::getMainType, preferredTypes));
        }

        wrapper.orderByDesc(Song::getPlayCount)
               .last("LIMIT " + candidateLimit(count));

        return convertToSimpleVO(songMapper.selectList(wrapper)).stream().limit(count).collect(Collectors.toList());
    }

       
                 
       
    private List<RecommendVO.SongSimpleVO> getSimilarSongsBySong(Song song, Integer count) {
        if (count == null || count <= 0) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Song::getStatus, 1)
               .eq(Song::getDeleted, 0)
               .ne(Song::getId, song.getId())
               .and(w -> w.eq(Song::getMainType, song.getMainType())
                           .or()
                           .in(Song::getId, getSimilarSongIdsByArtist(song.getId())))
               .orderByDesc(Song::getPlayCount, Song::getFavoriteCount)
               .last("LIMIT " + candidateLimit(count));

        return convertToSimpleVO(songMapper.selectList(wrapper)).stream().limit(count).collect(Collectors.toList());
    }

       
              
       
    private List<RecommendVO.SongSimpleVO> getSongsByArtist(Long artistId, Integer count) {
        if (count == null || count <= 0) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<SongArtist> saWrapper = new LambdaQueryWrapper<>();
        saWrapper.eq(SongArtist::getArtistId, artistId)
                 .last("LIMIT " + candidateLimit(count));

        List<SongArtist> songArtists = songArtistMapper.selectList(saWrapper);
        List<Long> songIds = songArtists.stream()
                .map(SongArtist::getSongId)
                .collect(Collectors.toList());

        if (songIds.isEmpty()) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Song::getId, songIds)
               .eq(Song::getStatus, 1)
               .eq(Song::getDeleted, 0)
               .orderByDesc(Song::getPlayCount);

        return convertToSimpleVO(songMapper.selectList(wrapper)).stream().limit(count).collect(Collectors.toList());
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
               .eq(SongLike::getDeleted, CommonConstants.NOT_DELETED)
               .in(SongLike::getSongId, songIds)
               .select(SongLike::getSongId);

        List<SongLike> likes = songLikeMapper.selectList(wrapper);
        Set<Long> likedSongIds = likes.stream()
                .map(SongLike::getSongId)
                .collect(Collectors.toSet());

        songs.forEach(song -> song.setIsFavorite(likedSongIds.contains(song.getId())));
    }

       
              
       
    private List<RecommendVO.SongSimpleVO> convertToSimpleVO(List<Song> songs) {
        if (songs == null || songs.isEmpty()) {
            return Collections.emptyList();
        }

        return filterPublicUploaderSongs(songs).stream()
                .filter(this::hasPlayableAudioUrl)
                .map(this::songToSimpleVO)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private boolean hasPlayableAudioUrl(Song song) {
        return song != null && (StringUtils.isNotBlank(song.getUrlStandard())
                || StringUtils.isNotBlank(song.getUrlHigh())
                || StringUtils.isNotBlank(song.getUrlLossless()));
    }

    private int candidateLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return 0;
        }
        long expanded = Math.max((long) limit * 3L, (long) limit + 10L);
        return (int) Math.min(expanded, 1000L);
    }

    private List<Song> filterPublicUploaderSongs(List<Song> songs) {
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
                .filter(song -> song.getUploaderId() == null || allowedUploaderIds.contains(song.getUploaderId()))
                .collect(Collectors.toList());
    }

    private List<Playlist> filterPublicCreatorPlaylists(List<Playlist> playlists) {
        if (playlists == null || playlists.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> userIds = playlists.stream()
                .map(Playlist::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> allowedUserIds = UserAccountStatusUtil.filterPublicContentUserIds(
                userIds, ids -> userMapper.selectBatchIds(ids));
        return playlists.stream()
                .filter(playlist -> playlist.getUserId() != null && allowedUserIds.contains(playlist.getUserId()))
                .collect(Collectors.toList());
    }

    private List<Album> filterAlbumsWithPublicSongs(List<Album> albums) {
        if (albums == null || albums.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> albumIds = albums.stream()
                .map(Album::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (albumIds.isEmpty()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<Song> songWrapper = new LambdaQueryWrapper<>();
        songWrapper.in(Song::getAlbumId, albumIds)
                .eq(Song::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Song::getDeleted, CommonConstants.NOT_DELETED)
                .select(Song::getAlbumId, Song::getUploaderId);
        Set<Long> allowedAlbumIds = filterPublicUploaderSongs(songMapper.selectList(songWrapper)).stream()
                .map(Song::getAlbumId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return albums.stream()
                .filter(album -> allowedAlbumIds.contains(album.getId()))
                .collect(Collectors.toList());
    }

    private List<MV> filterPublicMvs(List<MV> mvs) {
        if (mvs == null || mvs.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> songIds = mvs.stream()
                .map(MV::getSongId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (songIds.isEmpty()) {
            return mvs;
        }
        LambdaQueryWrapper<Song> songWrapper = new LambdaQueryWrapper<>();
        songWrapper.in(Song::getId, songIds)
                .eq(Song::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Song::getDeleted, CommonConstants.NOT_DELETED)
                .select(Song::getId, Song::getUploaderId);
        Set<Long> allowedSongIds = filterPublicUploaderSongs(songMapper.selectList(songWrapper)).stream()
                .map(Song::getId)
                .collect(Collectors.toSet());
        return mvs.stream()
                .filter(mv -> mv.getSongId() == null || allowedSongIds.contains(mv.getSongId()))
                .collect(Collectors.toList());
    }

       
              
       
    private RecommendVO.SongSimpleVO songToSimpleVO(Song song) {
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

       
                     
       
    private List<RecommendedSongVO> convertToRecommendedSongVO(List<RecommendVO.SongSimpleVO> songs, String source) {
        if (songs == null || songs.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, String> sourceNames = new HashMap<>();
        sourceNames.put("personal", "个性化推荐");
        sourceNames.put("daily", "每日推荐");
        sourceNames.put("discovery", "发现音乐");
        sourceNames.put("similar", "相似推荐");
        sourceNames.put("artist", "歌手推荐");
        sourceNames.put("hot", "热门推荐");

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
            vo.setConfidence(80 + (int) (Math.random() * 20));               
            vo.setTags(Arrays.asList(song.getMainType()));

            return vo;
        }).collect(Collectors.toList());
    }

       
               
       
    private void updateUserProfileAsync(Long userId, String actionType, Long targetId, Integer targetType) {
        deleteCurrentRecommendUserCache(userId);
    }

       
                             
       
    private List<Long> getSimilarSongIdsByArtist(Long songId) {
                       
        LambdaQueryWrapper<SongArtist> artistWrapper = new LambdaQueryWrapper<>();
        artistWrapper.eq(SongArtist::getSongId, songId);
        List<SongArtist> currentArtists = songArtistMapper.selectList(artistWrapper);

        if (currentArtists.isEmpty()) {
            return Collections.emptyList();
        }

                         
        List<Long> artistIds = currentArtists.stream()
                .map(SongArtist::getArtistId)
                .collect(Collectors.toList());

        LambdaQueryWrapper<SongArtist> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SongArtist::getArtistId, artistIds)
                .ne(SongArtist::getSongId, songId);

        List<SongArtist> similarArtists = songArtistMapper.selectList(wrapper);
        return similarArtists.stream()
                .map(SongArtist::getSongId)
                .distinct()
                .collect(Collectors.toList());
    }

       
             
       
    private void rebuildUserProfile(Long userId) {
                     
               
        getUserPreferredTypes(userId);
        getTopPreferredArtists(userId, 5);
    }

    private void deleteCurrentRecommendUserCache(Long userId) {
        if (userId == null) {
            return;
        }
        String version = musicIntelligenceCacheService.recommendVersionSegment();
        List<String> keys = Arrays.asList(
                RECOMMEND_CACHE_PREFIX + version + "daily:" + userId,
                USER_PROFILE_PREFIX + version + "tags:" + userId,
                USER_PROFILE_PREFIX + version + "types:" + userId
        );
        redisTemplate.delete(keys);
    }

}
