package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.*;
import com.haoran.music.mapper.*;
import com.haoran.music.service.ResourceBasedRecommendService;
import com.haoran.music.vo.recommend.RecommendVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

   
                      
                             
  
        
                               
   
@Slf4j
@Service
public class ResourceBasedRecommendServiceImpl implements ResourceBasedRecommendService {

    private final SongResourceRequestMapper songResourceRequestMapper;
                                         
    private final SongMapper songMapper;
    private final ArtistMapper artistMapper;
    private final SongLikeMapper songLikeMapper;
    private final UserMapper userMapper;

    public ResourceBasedRecommendServiceImpl(SongResourceRequestMapper songResourceRequestMapper,
                                             SongMapper songMapper,
                                             ArtistMapper artistMapper,
                                             SongLikeMapper songLikeMapper,
                                             UserMapper userMapper) {
        this.songResourceRequestMapper = songResourceRequestMapper;
        this.songMapper = songMapper;
        this.artistMapper = artistMapper;
        this.songLikeMapper = songLikeMapper;
        this.userMapper = userMapper;
    }

    @Override
    public RecommendVO getRecommendByRequestedSongs(Long userId, Integer limit) {
        int actualLimit = limit != null && limit > 0 ? limit : 20;

        RecommendVO result = new RecommendVO();
        result.setSource("resource_request");
        result.setSourceName("根据你的申请推荐");

                        
        List<SongResourceRequest> userRequests = getUserRequests(userId, 50);

        if (userRequests.isEmpty()) {
            result.setSongs(new ArrayList<>());
            return result;
        }

                         
        Set<String> requestedArtists = new HashSet<>();
        Set<String> requestedGenres = new HashSet<>();
        Map<String, Integer> artistRequestCount = new HashMap<>();

        for (SongResourceRequest request : userRequests) {
            if (ObjectUtils.isNotEmpty(request.getArtistName())) {
                requestedArtists.add(request.getArtistName());
                artistRequestCount.merge(request.getArtistName(), 1, Integer::sum);
            }
                             
            String genre = inferGenreFromArtist(request.getArtistName());
            if (genre != null) {
                requestedGenres.add(genre);
            }
        }

                          
        Set<Long> userLikedSongIds = getUserLikedSongIds(userId);

                           
        Map<Long, Double> songScoreMap = new HashMap<>();

                               
        for (String artistName : requestedArtists) {
            List<Artist> artists = findArtistsByName(artistName);
            for (Artist artist : artists) {
                LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
                wrapper.like(Song::getArtistIds, artist.getId().toString())
                        .eq(Song::getStatus, CommonConstants.STATUS_NORMAL)
                        .eq(Song::getDeleted, CommonConstants.NOT_DELETED)
                        .last("LIMIT 50");

                List<Song> artistSongs = filterPublicUploaderSongs(songMapper.selectList(wrapper));
                for (Song song : artistSongs) {
                    if (!userLikedSongIds.contains(song.getId())) {
                                              
                        double score = 50.0 + artistRequestCount.getOrDefault(artistName, 1) * 10;
                        songScoreMap.put(song.getId(), Math.max(songScoreMap.getOrDefault(song.getId(), 0.0), score));
                    }
                }
            }
        }

                         
        for (String genre : requestedGenres) {
            LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Song::getMainType, genre)
                    .eq(Song::getStatus, CommonConstants.STATUS_NORMAL)
                    .eq(Song::getDeleted, CommonConstants.NOT_DELETED)
                    .last("LIMIT 30");

            List<Song> genreSongs = filterPublicUploaderSongs(songMapper.selectList(wrapper));
            for (Song song : genreSongs) {
                if (!userLikedSongIds.contains(song.getId())) {
                    songScoreMap.merge(song.getId(), 30.0, Double::sum);
                }
            }
        }

                      
        List<RecommendVO.SongSimpleVO> songs = songScoreMap.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(actualLimit)
                .map(e -> convertToSimpleVO(e.getKey()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        result.setSongs(songs);
        log.info("资源申请推荐: userId={}, 请求歌手数={}, 推荐歌曲数={}",
                userId, requestedArtists.size(), songs.size());

        return result;
    }

    @Override
    public RecommendVO getHotRequestedSongsRecommend(Long userId, Integer limit) {
        int actualLimit = limit != null && limit > 0 ? limit : 20;

        RecommendVO result = new RecommendVO();
        result.setSource("hot_request");
        result.setSourceName("热门申请推荐");

                          
        Map<String, Integer> songRequestCount = getHotRequestedSongs(100);

        if (songRequestCount.isEmpty()) {
            result.setSongs(new ArrayList<>());
            return result;
        }

                          
        Set<Long> userLikedSongIds = getUserLikedSongIds(userId);

                             
        Map<Long, Double> songScoreMap = new HashMap<>();

        for (Map.Entry<String, Integer> entry : songRequestCount.entrySet()) {
            String songName = entry.getKey();
            Integer requestCount = entry.getValue();

                              
            LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
            wrapper.like(Song::getName, songName)
                    .eq(Song::getStatus, CommonConstants.STATUS_NORMAL)
                    .eq(Song::getDeleted, CommonConstants.NOT_DELETED)
                    .last("LIMIT 5");

            List<Song> matchedSongs = filterPublicUploaderSongs(songMapper.selectList(wrapper));
            for (Song song : matchedSongs) {
                if (!userLikedSongIds.contains(song.getId())) {
                                  
                    double score = 50.0 + requestCount * 5;
                    songScoreMap.put(song.getId(), Math.max(songScoreMap.getOrDefault(song.getId(), 0.0), score));
                }
            }
        }

                      
        List<RecommendVO.SongSimpleVO> songs = songScoreMap.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(actualLimit)
                .map(e -> convertToSimpleVO(e.getKey()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        result.setSongs(songs);
        return result;
    }

    @Override
    public RecommendVO getRecommendByRequestPreference(Long userId, Integer limit) {
        int actualLimit = limit != null && limit > 0 ? limit : 20;

        RecommendVO result = new RecommendVO();
        result.setSource("request_preference");
        result.setSourceName("申请偏好推荐");

                             
        Map<String, Integer> genrePreference = analyzeRequestGenrePreference(userId);
        Map<String, Integer> languagePreference = analyzeRequestLanguagePreference(userId);

                          
        Set<Long> userLikedSongIds = getUserLikedSongIds(userId);

                        
        Map<Long, Double> songScoreMap = new HashMap<>();

        for (Map.Entry<String, Integer> entry : genrePreference.entrySet()) {
            String genre = entry.getKey();
            Integer weight = entry.getValue();

            LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Song::getMainType, genre)
                    .eq(Song::getStatus, CommonConstants.STATUS_NORMAL)
                    .eq(Song::getDeleted, CommonConstants.NOT_DELETED)
                    .orderByDesc(Song::getHotScore)
                    .last("LIMIT 50");

            List<Song> genreSongs = filterPublicUploaderSongs(songMapper.selectList(wrapper));
            for (Song song : genreSongs) {
                if (!userLikedSongIds.contains(song.getId())) {
                                  
                    double score = weight * 10.0 + (song.getHotScore() != null ? song.getHotScore() / 10.0 : 0);
                    songScoreMap.put(song.getId(), Math.max(songScoreMap.getOrDefault(song.getId(), 0.0), score));
                }
            }
        }

                              
        for (Map.Entry<String, Integer> entry : languagePreference.entrySet()) {
            String language = entry.getKey();
            Integer weight = entry.getValue();

            LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Song::getLanguage, language)
                    .eq(Song::getStatus, CommonConstants.STATUS_NORMAL)
                    .eq(Song::getDeleted, CommonConstants.NOT_DELETED)
                    .orderByDesc(Song::getHotScore)
                    .last("LIMIT 30");

            List<Song> languageSongs = filterPublicUploaderSongs(songMapper.selectList(wrapper));
            for (Song song : languageSongs) {
                if (!userLikedSongIds.contains(song.getId())) {
                                    
                    double score = weight * 5.0 + (song.getHotScore() != null ? song.getHotScore() / 20.0 : 0);
                    songScoreMap.put(song.getId(), Math.max(songScoreMap.getOrDefault(song.getId(), 0.0), score));
                }
            }
        }

                      
        List<RecommendVO.SongSimpleVO> songs = songScoreMap.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(actualLimit)
                .map(e -> convertToSimpleVO(e.getKey()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        result.setSongs(songs);
        return result;
    }

    @Override
    public Map<String, Object> getUnmetRequestsAnalysis(Long userId) {
        Map<String, Object> analysis = new HashMap<>();

                        
        List<SongResourceRequest> userRequests = getUserRequests(userId, 1000);

                  
        int totalRequests = userRequests.size();
        int pendingCount = 0;
        int completedCount = 0;
        int rejectedCount = 0;

                 
        List<Map<String, String>> unmatchedList = new ArrayList<>();

        for (SongResourceRequest request : userRequests) {
            String status = request.getStatus();
            if ("pending".equals(status)) {
                pendingCount++;
            } else if ("completed".equals(status)) {
                completedCount++;
            } else if ("rejected".equals(status)) {
                rejectedCount++;
            }

                     
            if (ObjectUtils.isEmpty(request.getMatchedSongId()) &&
                    ObjectUtils.isEmpty(request.getAutoSongId())) {
                Map<String, String> unmet = new HashMap<>();
                unmet.put("songName", request.getSongName());
                unmet.put("artistName", request.getArtistName());
                unmet.put("status", status);
                unmet.put("requestTime", request.getCreateTime() != null ?
                        request.getCreateTime().toString() : "");
                unmatchedList.add(unmet);
            }
        }

                       
        Map<String, Integer> unmatchedSongCount = new HashMap<>();
        for (Map<String, String> unmet : unmatchedList) {
            String key = unmet.get("songName") + " - " + unmet.get("artistName");
            unmatchedSongCount.merge(key, 1, Integer::sum);
        }

        List<Map<String, Object>> hotUnmatched = unmatchedSongCount.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(10)
                .map(e -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("songArtist", e.getKey());
                    item.put("count", e.getValue());
                    return item;
                })
                .collect(Collectors.toList());

        analysis.put("totalRequests", totalRequests);
        analysis.put("pendingCount", pendingCount);
        analysis.put("completedCount", completedCount);
        analysis.put("rejectedCount", rejectedCount);
        analysis.put("unmatchedCount", unmatchedList.size());
        analysis.put("hotUnmatched", hotUnmatched);
        analysis.put("recentUnmatched", unmatchedList.stream().limit(20).collect(Collectors.toList()));

        return analysis;
    }

    @Override
    public Map<String, Object> getHotRequestStatistics(Integer limit) {
        int actualLimit = limit != null && limit > 0 ? limit : 20;

        Map<String, Object> statistics = new HashMap<>();

                          
        LambdaQueryWrapper<SongResourceRequest> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(SongResourceRequest::getCreateTime, LocalDateTime.now().minusDays(30))
                .eq(SongResourceRequest::getDeleted, 0);

        List<SongResourceRequest> recentRequests = filterPublicSignalRequests(songResourceRequestMapper.selectList(wrapper));

                      
        Map<String, Integer> songCount = new HashMap<>();
        Map<String, Integer> artistCount = new HashMap<>();

        for (SongResourceRequest request : recentRequests) {
            String songKey = request.getSongName();
            String artistKey = request.getArtistName();

            songCount.merge(songKey, 1, Integer::sum);
            artistCount.merge(artistKey, 1, Integer::sum);
        }

                   
        List<Map<String, Object>> topSongs = songCount.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(actualLimit)
                .map(e -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("songName", e.getKey());
                    item.put("requestCount", e.getValue());
                    return item;
                })
                .collect(Collectors.toList());

                   
        List<Map<String, Object>> topArtists = artistCount.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(actualLimit)
                .map(e -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("artistName", e.getKey());
                    item.put("requestCount", e.getValue());
                    return item;
                })
                .collect(Collectors.toList());

        statistics.put("totalRequests", recentRequests.size());
        statistics.put("topSongs", topSongs);
        statistics.put("topArtists", topArtists);

        return statistics;
    }

                                                       

       
                
       
    private List<SongResourceRequest> getUserRequests(Long userId, int limit) {
        LambdaQueryWrapper<SongResourceRequest> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SongResourceRequest::getUserId, userId)
                .eq(SongResourceRequest::getDeleted, 0)
                .orderByDesc(SongResourceRequest::getCreateTime)
                .last("LIMIT " + limit);

        return songResourceRequestMapper.selectList(wrapper);
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

       
               
       
    private List<Artist> findArtistsByName(String artistName) {
        LambdaQueryWrapper<Artist> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(Artist::getName, artistName)
                .or()
                .like(Artist::getOriginalName, artistName);

        return artistMapper.selectList(wrapper);
    }

       
                 
       
    private Map<String, Integer> getHotRequestedSongs(int limit) {
        LambdaQueryWrapper<SongResourceRequest> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(SongResourceRequest::getCreateTime, LocalDateTime.now().minusDays(30))
                .eq(SongResourceRequest::getDeleted, 0)
                .last("LIMIT " + (limit > 0 ? limit * 3 : limit));

        List<SongResourceRequest> requests = filterPublicSignalRequests(songResourceRequestMapper.selectList(wrapper));

        Map<String, Integer> songCount = new HashMap<>();
        for (SongResourceRequest request : requests) {
            songCount.merge(request.getSongName(), 1, Integer::sum);
        }

        return songCount;
    }

    private List<SongResourceRequest> filterPublicSignalRequests(List<SongResourceRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> userIds = requests.stream()
                .map(SongResourceRequest::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> allowedUserIds = userMapper.selectBatchIds(userIds).stream()
                .filter(UserAccountStatusUtil::canContributePublicStats)
                .map(User::getId)
                .collect(Collectors.toSet());
        return requests.stream()
                .filter(request -> allowedUserIds.contains(request.getUserId()))
                .collect(Collectors.toList());
    }


       
                                                                             
         
      
       
    private String inferGenreFromArtist(String artistName) {
        if (artistName == null || artistName.trim().isEmpty()) {
            return null;
        }
                      
        String name = artistName.trim().toLowerCase();
                     
        if (name.contains("dj") || name.contains("组合") || name.contains("band")) {
            return "电子";
        }
        if (name.contains("乐队") || name.contains("摇滚") || name.contains("rock")) {
            return "摇滚";
        }
        if (name.contains("说唱") || name.contains("rapper") || name.contains("嘻哈")) {
            return "说唱";
        }
        if (name.contains("民谣") || name.contains("folk")) {
            return "民谣";
        }
                           
        String[] popArtists = {"周杰伦", "陈奕迅", "林俊杰", "王力宏", "蔡依林", "张韶涵", "梁静茹", "孙燕姿", "邓紫棋", "薛之谦", "华晨宇", "李荣浩", "毛不易", "汪苏泷"};
        for (String artist : popArtists) {
            if (name.contains(artist.toLowerCase()) || artist.toLowerCase().contains(name)) {
                return "流行";
            }
        }
        String[] rockArtists = {"五月天", "苏打绿", "张震岳", "崔健"};
        for (String artist : rockArtists) {
            if (name.contains(artist.toLowerCase()) || artist.toLowerCase().contains(name)) {
                return "摇滚";
            }
        }
        String[] rapArtists = {"吴亦凡", "pg one", "gai"};
        for (String artist : rapArtists) {
            if (name.contains(artist.toLowerCase()) || artist.toLowerCase().contains(name)) {
                return "说唱";
            }
        }
                      
        log.debug("无法从歌手名推断流派: {}", artistName);
        return null;
    }


       
                  
       
    private Map<String, Integer> analyzeRequestGenrePreference(Long userId) {
                          
        List<SongResourceRequest> requests = getUserRequests(userId, 100);

        Map<String, Integer> genreCount = new HashMap<>();
        for (SongResourceRequest request : requests) {
            if (ObjectUtils.isNotEmpty(request.getMatchedSongId())) {
                Song song = songMapper.selectById(request.getMatchedSongId());
                if (song != null && ObjectUtils.isNotEmpty(song.getMainType())) {
                    genreCount.merge(song.getMainType(), 1, Integer::sum);
                }
            }
        }

        return genreCount;
    }

       
                  
       
    private Map<String, Integer> analyzeRequestLanguagePreference(Long userId) {
        List<SongResourceRequest> requests = getUserRequests(userId, 100);

        Map<String, Integer> languageCount = new HashMap<>();
        for (SongResourceRequest request : requests) {
            if (ObjectUtils.isNotEmpty(request.getMatchedSongId())) {
                Song song = songMapper.selectById(request.getMatchedSongId());
                if (song != null && ObjectUtils.isNotEmpty(song.getLanguage())) {
                    languageCount.merge(song.getLanguage(), 1, Integer::sum);
                }
            }
        }

        return languageCount;
    }

       
              
       
    private RecommendVO.SongSimpleVO convertToSimpleVO(Long songId) {
        Song song = songMapper.selectById(songId);
        if (!isPublicSong(song)) {
            return null;
        }

        RecommendVO.SongSimpleVO vo = new RecommendVO.SongSimpleVO();
        vo.setId(song.getId());
        vo.setName(song.getName());
        vo.setArtistNames(song.getArtistNames());
        vo.setArtistId(song.getArtistId());
        vo.setAlbumName(song.getAlbumName());
        vo.setAlbumId(song.getAlbumId());
        vo.setDuration(song.getDuration());
        vo.setCover(song.getCover());
        return vo;
    }

    private boolean isPublicSong(Song song) {
        return song != null
                && CommonConstants.STATUS_NORMAL.equals(song.getStatus())
                && CommonConstants.NOT_DELETED.equals(song.getDeleted())
                && (song.getUploaderId() == null
                || UserAccountStatusUtil.canExposePublicContent(song.getUploaderId(), userMapper::selectById));
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
}
