package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.haoran.music.common.context.UserContext;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.SearchLimitUtil;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.Album;
import com.haoran.music.entity.Artist;
import com.haoran.music.entity.Song;
import com.haoran.music.entity.User;
import com.haoran.music.mapper.AlbumMapper;
import com.haoran.music.mapper.ArtistMapper;
import com.haoran.music.mapper.SongMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.SearchEnhanceService;
import com.haoran.music.service.MusicIntelligenceCacheService;
import com.haoran.music.service.UserPortraitService;
import com.haoran.music.vo.search.HotSearchVO;
import com.haoran.music.vo.search.SearchSuggestVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;





@Slf4j
@Service
public class SearchEnhanceServiceImpl implements SearchEnhanceService {

    @Resource
    private SongMapper songMapper;

    @Resource
    private ArtistMapper artistMapper;

    @Resource
    private AlbumMapper albumMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private UserPortraitService userPortraitService;

    @Resource
    private com.haoran.music.mapper.ListenHistoryMapper listenHistoryMapper;

    @Resource
    private MusicIntelligenceCacheService musicIntelligenceCacheService;

    private static final String HOT_SEARCH_KEY = "search:hot:{rank}:";
    private static final String HOT_SEARCH_DB_KEY = "search:hot:db";
    private static final String SEARCH_HISTORY_KEY = "search:enhance:history:";
    private static final int MAX_SEARCH_KEYWORD_LENGTH = 100;
    private static final int HOT_SEARCH_WINDOW_DAYS = 7;
    private static final long MIN_HOT_SEARCH_COUNT = 2L;
    private static final DateTimeFormatter HOT_SEARCH_DAY_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;





    private static final List<String> DEFAULT_HOT_SEARCH = Arrays.asList(
            "周杰伦", "邓紫棋", "稻香", "晴天", "七里香",
            "告白气球", "演员", "薛之谦", "陈奕迅", "十年"
    );



    @Override
    public SearchSuggestVO getSuggest(String keyword, Integer limit) {
        if (ObjectUtils.isEmpty(keyword) || keyword.trim().length() == 0) {
            return emptySearchSuggest();
        }
        keyword = normalizeSearchKeyword(keyword);
        if (!isReadableSearchKeyword(keyword)) {
            return emptySearchSuggest();
        }
        int resultLimit = SearchLimitUtil.normalize(limit);

        SearchSuggestVO result = new SearchSuggestVO();

        try {

            Long userId = UserContext.getCurrentUserId();


            List<String> keywords = getKeywordSuggestions(keyword, 5);
            result.setKeywords(keywords);


            List<SearchSuggestVO.SimpleSongVO> songs = searchSongsWithPersonalization(keyword, resultLimit, userId);
            result.setSongs(songs);


            List<SearchSuggestVO.SimpleArtistVO> artists = searchArtistsWithPersonalization(keyword, resultLimit, userId);
            result.setArtists(artists);


            List<SearchSuggestVO.SimpleAlbumVO> albums = searchAlbumsWithPersonalization(keyword, resultLimit, userId);
            result.setAlbums(albums);

        } catch (Exception e) {
            log.error("获取搜索联想失败: keyword={}", keyword);
        }

        return result;
    }

    private SearchSuggestVO emptySearchSuggest() {
        SearchSuggestVO result = new SearchSuggestVO();
        result.setKeywords(Collections.emptyList());
        result.setSongs(Collections.emptyList());
        result.setArtists(Collections.emptyList());
        result.setAlbums(Collections.emptyList());
        result.setPlaylists(Collections.emptyList());
        return result;
    }




    private List<SearchSuggestVO.SimpleSongVO> searchSongsWithPersonalization(String keyword, Integer limit, Long userId) {
        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Song::getStatus, 1)
                .eq(Song::getDeleted, 0)
                .and(w -> w.like(Song::getName, keyword)
                        .or()
                        .like(Song::getArtistNames, keyword))
                .orderByDesc(Song::getPlayCount)
                .last("LIMIT " + candidateLimit(limit));                 

        List<Song> songs = filterPublicSongs(songMapper.selectList(wrapper));


        if (userId != null && !songs.isEmpty()) {
            songs = sortSongsByUserPreference(songs, userId);
        }


        songs = songs.stream().limit(limit).collect(Collectors.toList());

        List<SearchSuggestVO.SimpleSongVO> result = new ArrayList<>();
        for (Song song : songs) {
            SearchSuggestVO.SimpleSongVO vo = new SearchSuggestVO.SimpleSongVO();
            vo.setId(song.getId());
            vo.setName(song.getName());
            vo.setCover(song.getCover());
            vo.setArtistNames(song.getArtistNames() != null ? song.getArtistNames() : "");
            result.add(vo);
        }

        return result;
    }




    private List<SearchSuggestVO.SimpleArtistVO> searchArtistsWithPersonalization(String keyword, Integer limit, Long userId) {
        LambdaQueryWrapper<Artist> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Artist::getStatus, 1)
                .eq(Artist::getDeleted, 0)
                .like(Artist::getName, keyword)
                .orderByDesc(Artist::getFansCount)
                .last("LIMIT " + limit);

        List<Artist> artists = artistMapper.selectList(wrapper);

        List<SearchSuggestVO.SimpleArtistVO> result = new ArrayList<>();
        for (Artist artist : artists) {
            SearchSuggestVO.SimpleArtistVO vo = new SearchSuggestVO.SimpleArtistVO();
            vo.setId(artist.getId());
            vo.setName(artist.getName());

            vo.setAvatar(artist.getAvatar() != null ? artist.getAvatar() : artist.getCover());
            result.add(vo);
        }

        return result;
    }




    private List<SearchSuggestVO.SimpleAlbumVO> searchAlbumsWithPersonalization(String keyword, Integer limit, Long userId) {
        LambdaQueryWrapper<Album> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Album::getStatus, 1)
                .eq(Album::getDeleted, 0)
                .and(w -> w.like(Album::getName, keyword)
                        .or()
                        .like(Album::getArtistNames, keyword))
                .orderByDesc(Album::getPlayCount)
                .last("LIMIT " + candidateLimit(limit));

        List<Album> albums = filterAlbumsWithPublicSongs(albumMapper.selectList(wrapper));


        if (userId != null && !albums.isEmpty()) {
            albums = sortAlbumsByUserPreference(albums, userId);
        }


        albums = albums.stream().limit(limit).collect(Collectors.toList());

        List<SearchSuggestVO.SimpleAlbumVO> result = new ArrayList<>();
        for (Album album : albums) {
            SearchSuggestVO.SimpleAlbumVO vo = new SearchSuggestVO.SimpleAlbumVO();
            vo.setId(album.getId());
            vo.setName(album.getName());
            vo.setCover(album.getCover());
            vo.setArtistName(album.getArtistNames() != null ? album.getArtistNames() : "");
            result.add(vo);
        }

        return result;
    }








    private List<Song> sortSongsByUserPreference(List<Song> songs, Long userId) {
        try {

            Map<String, Object> preference = userPortraitService.getUserMusicPreference(userId);

            @SuppressWarnings("unchecked")
            List<String> favoriteGenres = getStringList(preference, "favoriteGenres", Collections.emptyList());

            @SuppressWarnings("unchecked")
            List<String> favoriteLanguages = getStringList(preference, "favoriteLanguages", Collections.emptyList());


            Map<Song, Integer> scoreMap = new HashMap<>();

            for (Song song : songs) {
                int score = 0;


                if (song.getMainType() != null && favoriteGenres.contains(song.getMainType())) {
                    int index = favoriteGenres.indexOf(song.getMainType());
                    score += (100 - index * 20);           
                }


                if (song.getLanguage() != null && favoriteLanguages.contains(song.getLanguage())) {
                    int index = favoriteLanguages.indexOf(song.getLanguage());
                    score += (50 - index * 15);          
                }


                if (song.getPlayCount() != null) {
                    score += Math.min(song.getPlayCount() / 100, 20);
                }

                scoreMap.put(song, score);
            }


            return songs.stream()
                    .sorted((s1, s2) -> scoreMap.getOrDefault(s2, 0).compareTo(scoreMap.getOrDefault(s1, 0)))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("根据用户偏好排序歌曲失败: userId={}", userId);
            return songs;
        }
    }








    private List<Album> sortAlbumsByUserPreference(List<Album> albums, Long userId) {
        try {

            Map<String, Object> preference = userPortraitService.getUserMusicPreference(userId);

            @SuppressWarnings("unchecked")
            List<String> favoriteGenres = getStringList(preference, "favoriteGenres", Collections.emptyList());

            @SuppressWarnings("unchecked")
            List<String> favoriteLanguages = getStringList(preference, "favoriteLanguages", Collections.emptyList());


            Map<Album, Integer> scoreMap = new HashMap<>();

            for (Album album : albums) {
                int score = 0;


                if (album.getType() != null && favoriteGenres.contains(album.getType())) {
                    int index = favoriteGenres.indexOf(album.getType());
                    score += (100 - index * 20);
                }


                if (album.getLanguage() != null && favoriteLanguages.contains(album.getLanguage())) {
                    int index = favoriteLanguages.indexOf(album.getLanguage());
                    score += (50 - index * 15);
                }


                if (album.getPlayCount() != null) {
                    score += Math.min(album.getPlayCount() / 100, 20);
                }

                scoreMap.put(album, score);
            }


            return albums.stream()
                    .sorted((a1, a2) -> scoreMap.getOrDefault(a2, 0).compareTo(scoreMap.getOrDefault(a1, 0)))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("根据用户偏好排序专辑失败: userId={}", userId);
            return albums;
        }
    }





    @Override
    public List<HotSearchVO> getHotSearch(Integer limit) {
        int resultLimit = SearchLimitUtil.normalize(limit);
        List<HotSearchVO> result = new ArrayList<>();


        Map<String, Long> searchCountMap = getRollingHotSearchCounts(resultLimit);
        if (ObjectUtils.isEmpty(searchCountMap)) {

            return createDefaultHotSearch(resultLimit);
        }


        List<Map.Entry<String, Long>> sortedList = new ArrayList<>(searchCountMap.entrySet());
        sortedList.sort(Map.Entry.<String, Long>comparingByValue().reversed()
                .thenComparing(Map.Entry.comparingByKey()));


        int rank = 1;
        for (Map.Entry<String, Long> entry : sortedList) {
            if (rank > resultLimit) break;

            HotSearchVO vo = new HotSearchVO();
            vo.setKeyword(entry.getKey());
            vo.setHeat(entry.getValue());
            vo.setRank(rank++);


            vo.setTrend(null);

            result.add(vo);
        }


        if (result.size() < resultLimit) {
            Set<String> existingKeywords = result.stream()
                    .map(HotSearchVO::getKeyword)
                    .collect(Collectors.toSet());
            for (HotSearchVO fallback : createDefaultHotSearch(resultLimit)) {
                if (result.size() >= resultLimit) break;
                if (existingKeywords.add(fallback.getKeyword())) {
                    fallback.setRank(result.size() + 1);
                    result.add(fallback);
                }
            }
        }

        return result;
    }








    private List<HotSearchVO> getPersonalizedHotSearch(Long userId, Integer limit) {
        List<HotSearchVO> result = new ArrayList<>();

        try {

            Map<String, Object> preference = userPortraitService.getUserMusicPreference(userId);

            @SuppressWarnings("unchecked")
            List<String> favoriteGenres = getStringList(preference, "favoriteGenres", Collections.emptyList());

            if (favoriteGenres.isEmpty()) {
                return result;
            }


            int rank = 1;
            for (String genre : favoriteGenres) {
                if (rank > limit) break;


                List<Artist> topArtists = getTopArtistsByGenre(genre, 2);

                for (Artist artist : topArtists) {
                    if (rank > limit) break;

                    HotSearchVO vo = new HotSearchVO();
                    vo.setKeyword(artist.getName());
                    vo.setHeat((long) artist.getFansCount());
                    vo.setRank(rank++);
                    vo.setTrend("up");                
                    result.add(vo);
                }
            }


            if (result.size() < limit) {
                @SuppressWarnings("unchecked")
                List<String> favoriteLanguages = getStringList(preference, "favoriteLanguages", Collections.emptyList());

                for (String language : favoriteLanguages) {
                    if (result.size() >= limit) break;

                    String langKeyword = getLanguageHotKeyword(language);
                    if (langKeyword != null) {
                        HotSearchVO vo = new HotSearchVO();
                        vo.setKeyword(langKeyword);
                        vo.setHeat(5000L);
                        vo.setRank(result.size() + 1);
                        vo.setTrend("equal");
                        result.add(vo);
                    }
                }
            }

        } catch (Exception e) {
            log.error("获取个性化热门搜索失败: userId={}", userId);
        }

        return result;
    }




    private List<Artist> getTopArtistsByGenre(String genre, Integer limit) {
        try {

            LambdaQueryWrapper<Song> songWrapper = new LambdaQueryWrapper<>();
            songWrapper.like(Song::getMainType, genre)
                    .eq(Song::getStatus, 1)
                    .eq(Song::getDeleted, 0)
                    .orderByDesc(Song::getHotScore)
                    .last("LIMIT 100");

            List<Song> songs = filterPublicSongs(songMapper.selectList(songWrapper));
            if (songs.isEmpty()) {
                return Collections.emptyList();
            }


            Map<Long, Integer> artistCountMap = new HashMap<>();
            Map<Long, Artist> artistMap = new HashMap<>();

            for (Song song : songs) {

                String artistIds = song.getArtistIds();
                if (artistIds != null && !artistIds.isEmpty()) {
                    String[] ids = artistIds.split(",");
                    for (String idStr : ids) {
                        try {
                            Long artistId = Long.parseLong(idStr.trim());
                            artistCountMap.merge(artistId, 1, Integer::sum);


                            if (!artistMap.containsKey(artistId)) {
                                Artist artist = artistMapper.selectById(artistId);
                                if (artist != null
                                        && Integer.valueOf(1).equals(artist.getStatus())
                                        && Integer.valueOf(0).equals(artist.getDeleted())) {
                                    artistMap.put(artistId, artist);
                                }
                            }
                        } catch (NumberFormatException e) {

                        }
                    }
                }
            }


            return artistCountMap.entrySet().stream()
                    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                    .limit(limit)
                    .map(entry -> artistMap.get(entry.getKey()))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("根据流派获取热门歌手失败: genre={}", genre);
            return Collections.emptyList();
        }
    }




    private String getLanguageHotKeyword(String language) {
        switch (language) {
            case "zh":
                return "华语热门";
            case "en":
                return "欧美金曲";
            case "ja":
                return "日语流行";
            case "ko":
                return "韩流音乐";
            default:
                return null;
        }
    }

    @Override
    public void saveSearchHistory(String keyword) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            log.debug("[SearchEnhanceService] user not logged in, skip enhanced search history");
            return;
        }
        recordSearchActivity(userId, keyword);
    }

    @Override
    public void recordSearchActivity(Long userId, String keyword) {
        if (userId == null || ObjectUtils.isEmpty(keyword) || keyword.trim().length() == 0) {
            return;
        }

        String normalizedKeyword;
        try {
            normalizedKeyword = normalizeSearchKeyword(keyword);
        } catch (BusinessException tooLong) {
            log.debug("忽略超长搜索活动: userId={}, length={}", userId, keyword.length());
            return;
        }

        try {
            String historyKey = SEARCH_HISTORY_KEY + userId;
            redisTemplate.opsForList().remove(historyKey, 0, normalizedKeyword);
            redisTemplate.opsForList().leftPush(historyKey, normalizedKeyword);
            redisTemplate.opsForList().trim(historyKey, 0, 19);
            redisTemplate.expire(historyKey, 30, TimeUnit.DAYS);


            if (!isEligibleForPublicHotSearch(normalizedKeyword)) {
                return;
            }

            User user = userMapper.selectById(userId);
            if (!UserAccountStatusUtil.canContributePublicStats(user)) {
                return;
            }

            String hotSearchKey = hotSearchDayKey(LocalDate.now());
            redisTemplate.opsForZSet().incrementScore(hotSearchKey, normalizedKeyword, 1D);
            redisTemplate.expire(hotSearchKey, HOT_SEARCH_WINDOW_DAYS + 1L, TimeUnit.DAYS);
        } catch (Exception e) {
            log.error("保存增强搜索活动失败: userId={}, keyword={}", userId, normalizedKeyword);
        }
    }

    @Override
    public List<String> getSearchHistory() {
        try {
            Long userId = UserContext.getCurrentUserId();
            if (userId == null) {
                return new ArrayList<>();
            }

            String historyKey = SEARCH_HISTORY_KEY + userId;
            List<Object> history = redisTemplate.opsForList().range(historyKey, 0, 19);

            if (ObjectUtils.isEmpty(history)) {
                return new ArrayList<>();
            }

            List<String> result = new ArrayList<>();
            for (Object item : history) {
                result.add(item.toString());
            }

            return result;

        } catch (Exception e) {
            log.error("获取搜索历史失败: {}", e.getClass().getSimpleName());
            return new ArrayList<>();
        }
    }

    @Override
    public void clearSearchHistory() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return;
        }
        clearSearchActivity(userId);
    }

    @Override
    public void clearSearchActivity(Long userId) {
        if (userId == null) {
            return;
        }

        try {
            String historyKey = SEARCH_HISTORY_KEY + userId;
            redisTemplate.delete(historyKey);
        } catch (Exception e) {
            log.error("清空增强搜索活动失败: userId={}", userId);
        }
    }

    @Override
    public void deleteSearchActivityItem(Long userId, String keyword) {
        if (userId == null || ObjectUtils.isEmpty(keyword) || keyword.trim().length() == 0) {
            return;
        }

        String normalizedKeyword = keyword.trim();

        try {
            String historyKey = SEARCH_HISTORY_KEY + userId;
            redisTemplate.opsForList().remove(historyKey, 0, normalizedKeyword);
        } catch (Exception e) {
            log.error("删除增强搜索活动失败: userId={}, keyword={}", userId, normalizedKeyword);
        }
    }




    private List<String> getKeywordSuggestions(String keyword, Integer limit) {
        List<String> suggestions = new ArrayList<>();


        List<String> hotSearches = getHotSearchFromDatabase(100);


        for (String hotKeyword : hotSearches) {
            if (hotKeyword.contains(keyword) || keyword.contains(hotKeyword)) {
                suggestions.add(hotKeyword);
                if (suggestions.size() >= limit) {
                    break;
                }
            }
        }

        return suggestions;
    }




    private List<HotSearchVO> createDefaultHotSearch(Integer limit) {
        List<HotSearchVO> result = new ArrayList<>();

        List<String> hotSearches = getHotSearchFromDatabase(limit);


        for (int i = 0; i < Math.min(limit, hotSearches.size()); i++) {
            HotSearchVO vo = new HotSearchVO();
            vo.setKeyword(hotSearches.get(i));

            vo.setHeat(null);
            vo.setRank(i + 1);
            vo.setTrend(null);
            result.add(vo);
        }

        return result;
    }




    @SuppressWarnings("unchecked")
    private List<String> getStringList(Map<String, Object> map, String key, List<String> defaultValue) {
        Object value = map.get(key);
        if (value instanceof List) {
            return (List<String>) value;
        }
        return defaultValue;
    }

    private Map<String, Long> getRollingHotSearchCounts(int resultLimit) {
        int perDayLimit = Math.min(500, Math.max(50, resultLimit * 5));
        Map<String, Long> counts = new HashMap<>();
        LocalDate today = LocalDate.now();
        for (int dayOffset = 0; dayOffset < HOT_SEARCH_WINDOW_DAYS; dayOffset++) {
            Set<ZSetOperations.TypedTuple<Object>> tuples = redisTemplate.opsForZSet()
                    .reverseRangeWithScores(hotSearchDayKey(today.minusDays(dayOffset)), 0, perDayLimit - 1L);
            if (tuples == null) {
                continue;
            }
            for (ZSetOperations.TypedTuple<Object> tuple : tuples) {
                if (tuple.getValue() == null || tuple.getScore() == null) {
                    continue;
                }
                String keyword = tuple.getValue().toString();
                if (!isEligibleForPublicHotSearch(keyword)) {
                    continue;
                }
                long score = Math.max(0L, Math.round(tuple.getScore()));
                counts.merge(keyword, score, Long::sum);
            }
        }
        counts.entrySet().removeIf(entry -> entry.getValue() < MIN_HOT_SEARCH_COUNT);
        return counts;
    }

    private String hotSearchDayKey(LocalDate day) {
        return HOT_SEARCH_KEY + HOT_SEARCH_DAY_FORMAT.format(day);
    }

    private String normalizeSearchKeyword(String keyword) {
        String normalized = keyword == null ? "" : keyword.trim();
        if (normalized.length() > MAX_SEARCH_KEYWORD_LENGTH) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "搜索关键词不能超过" + MAX_SEARCH_KEYWORD_LENGTH + "个字符");
        }
        return normalized;
    }




    private boolean isReadableSearchKeyword(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return false;
        }
        boolean hasLetterOrDigit = false;
        for (int offset = 0; offset < keyword.length();) {
            int codePoint = keyword.codePointAt(offset);
            int type = Character.getType(codePoint);
            if (type == Character.CONTROL
                    || type == Character.FORMAT
                    || type == Character.PRIVATE_USE
                    || type == Character.SURROGATE
                    || type == Character.UNASSIGNED
                    || type == Character.LINE_SEPARATOR
                    || type == Character.PARAGRAPH_SEPARATOR) {
                return false;
            }
            hasLetterOrDigit = hasLetterOrDigit || Character.isLetterOrDigit(codePoint);
            offset += Character.charCount(codePoint);
        }
        return hasLetterOrDigit;
    }




    private boolean isEligibleForPublicHotSearch(String keyword) {
        if (!isReadableSearchKeyword(keyword)) {
            return false;
        }
        String compact = keyword.replaceAll("\\s+", "");
        return !compact.matches("(?i)^([a-z0-9])\\1+$");
    }

    private int candidateLimit(Integer limit) {
        int safeLimit = limit != null && limit > 0 ? limit : 20;
        long expanded = Math.max((long) safeLimit * 3L, (long) safeLimit + 10L);
        return (int) Math.min(expanded, 300L);
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
                        && Integer.valueOf(1).equals(song.getStatus())
                        && Integer.valueOf(0).equals(song.getDeleted())
                        && (song.getUploaderId() == null || allowedUploaderIds.contains(song.getUploaderId())))
                .collect(Collectors.toList());
    }

    private boolean isPublicSong(Song song) {
        return song != null
                && Integer.valueOf(1).equals(song.getStatus())
                && Integer.valueOf(0).equals(song.getDeleted())
                && (song.getUploaderId() == null
                || UserAccountStatusUtil.canExposePublicContent(song.getUploaderId(), userMapper::selectById));
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
        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Song::getAlbumId, albumIds)
                .eq(Song::getStatus, 1)
                .eq(Song::getDeleted, 0)
                .select(Song::getAlbumId, Song::getUploaderId);
        Set<Long> allowedAlbumIds = filterPublicSongs(songMapper.selectList(wrapper)).stream()
                .map(Song::getAlbumId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return albums.stream()
                .filter(album -> album != null
                        && Integer.valueOf(1).equals(album.getStatus())
                        && Integer.valueOf(0).equals(album.getDeleted())
                        && allowedAlbumIds.contains(album.getId()))
                .collect(Collectors.toList());
    }







    private List<String> getHotSearchFromDatabase(Integer limit) {
        try {

            String cacheKey = HOT_SEARCH_DB_KEY + ":" + musicIntelligenceCacheService.searchVersionSegment();
            List<Object> cached = ObjectUtils.castList(redisTemplate.opsForValue().get(cacheKey), Object.class);
            if (cached != null && !cached.isEmpty()) {
                return cached.stream()
                        .map(Object::toString)
                        .limit(limit)
                        .collect(Collectors.toList());
            }


            LambdaQueryWrapper<com.haoran.music.entity.ListenHistory> wrapper = new LambdaQueryWrapper<>();
            wrapper.ge(com.haoran.music.entity.ListenHistory::getListenTime,
                    java.time.LocalDateTime.now().minusDays(7))
                    .orderByDesc(com.haoran.music.entity.ListenHistory::getListenTime)
                    .last("LIMIT 1000");

            List<com.haoran.music.entity.ListenHistory> histories = listenHistoryMapper.selectList(wrapper);
            if (histories.isEmpty()) {
                return DEFAULT_HOT_SEARCH.stream().limit(limit).collect(Collectors.toList());
            }


            Map<Long, Integer> songCountMap = new HashMap<>();
            for (com.haoran.music.entity.ListenHistory history : histories) {
                if (history.getSongId() != null) {
                    songCountMap.merge(history.getSongId(), 1, Integer::sum);
                }
            }


            List<String> hotKeywords = new ArrayList<>();
            List<Map.Entry<Long, Integer>> sortedSongs = new ArrayList<>(songCountMap.entrySet());
            sortedSongs.sort(Map.Entry.<Long, Integer>comparingByValue().reversed()
                    .thenComparing(Map.Entry.comparingByKey()));
            for (Map.Entry<Long, Integer> entry : sortedSongs) {
                if (hotKeywords.size() >= limit * 2) break;

                Song song = songMapper.selectById(entry.getKey());
                if (isPublicSong(song)) {

                    if (song.getArtistNames() != null && !song.getArtistNames().isEmpty()) {
                        hotKeywords.add(song.getArtistNames());
                    }

                    if (song.getName() != null && !song.getName().isEmpty()) {
                        hotKeywords.add(song.getName());
                    }
                }
            }


            List<String> result = hotKeywords.stream()
                    .distinct()
                    .limit(limit)
                    .collect(Collectors.toList());


            if (!result.isEmpty()) {
                redisTemplate.opsForValue().set(cacheKey, result, 1, TimeUnit.HOURS);
            }

            return result.isEmpty() ?
                    DEFAULT_HOT_SEARCH.stream().limit(limit).collect(Collectors.toList()) : result;

        } catch (Exception e) {
            log.error("从数据库统计热门搜索失败: {}", e.getClass().getSimpleName());
            return DEFAULT_HOT_SEARCH.stream().limit(limit).collect(Collectors.toList());
        }
    }
}
