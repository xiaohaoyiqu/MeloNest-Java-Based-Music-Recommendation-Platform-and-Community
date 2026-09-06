package com.haoran.music.service.search;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.constant.MusicConstants;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UrlHelper;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.*;
import com.haoran.music.mapper.*;
import com.haoran.music.vo.search.SearchResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;







@Slf4j
@Service
public class ElasticsearchSearchEngineAdapter implements SearchEngineAdapter {

    private static final int SEARCH_LIMIT = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String TYPE_SONG = "song";
    private static final String TYPE_ALBUM = "album";
    private static final String TYPE_ARTIST = "artist";
    private static final String TYPE_PLAYLIST = "playlist";
    private static final String TYPE_MV = "mv";
    private static final String TYPE_USER = "user";

    @Resource
    private ElasticsearchHttpClient httpClient;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private MysqlSearchEngineAdapter mysqlFallback;

    @Resource
    private SongMapper songMapper;

    @Resource
    private AlbumMapper albumMapper;

    @Resource
    private ArtistMapper artistMapper;

    @Resource
    private PlaylistMapper playlistMapper;

    @Resource
    private MVMapper mvMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private PlaylistSongMapper playlistSongMapper;

    @Value("${search.elasticsearch.index-prefix:haoran_music}")
    private String indexPrefix;

    @Override
    public String engineName() {
        return "elasticsearch";
    }

    @Override
    public SearchResultVO search(String keyword, Long userId) {
        SearchResultVO result = new SearchResultVO();
        result.setKeyword(keyword);
        result.setSongs(searchSongs(keyword, userId, 1, SEARCH_LIMIT).getSongs());
        result.setAlbums(searchAlbums(keyword, userId, 1, SEARCH_LIMIT).getAlbums());
        result.setArtists(searchArtists(keyword, userId, 1, SEARCH_LIMIT).getArtists());
        result.setPlaylists(searchPlaylists(keyword, userId, 1, SEARCH_LIMIT).getPlaylists());
        result.setMvs(searchMvs(keyword, userId, 1, SEARCH_LIMIT).getMvs());
        return result;
    }

    @Override
    public SearchResultVO searchSongs(String keyword, Long userId, Integer page, Integer size) {
        int currentPage = normalizePage(page);
        int pageSize = normalizeSize(size);
        EsPage esPage = searchPage(TYPE_SONG, keyword, currentPage, pageSize);
        if (esPage == null) {
            return mysqlFallback.searchSongs(keyword, userId, currentPage, pageSize);
        }
        List<SearchResultVO.SongSimpleVO> records = assembleSongs(esPage.ids(), userId);
        if (shouldFallbackAfterAssembly(records, esPage, currentPage, pageSize)) {
            return mysqlFallback.searchSongs(keyword, userId, currentPage, pageSize);
        }
        return pageResult(keyword, records, currentPage, pageSize, esPage.total, TYPE_SONG);
    }

    @Override
    public SearchResultVO searchSongs(String keyword, Long userId, Integer page, Integer size, String field) {
        int currentPage = normalizePage(page);
        int pageSize = normalizeSize(size);
        if (!"all".equals(field)) {

            return mysqlFallback.searchSongs(keyword, userId, currentPage, pageSize, field);
        }
        return searchSongs(keyword, userId, currentPage, pageSize);
    }

    @Override
    public SearchResultVO searchAlbums(String keyword, Long userId, Integer page, Integer size) {
        int currentPage = normalizePage(page);
        int pageSize = normalizeSize(size);
        EsPage esPage = searchPage(TYPE_ALBUM, keyword, currentPage, pageSize);
        if (esPage == null) {
            return mysqlFallback.searchAlbums(keyword, userId, currentPage, pageSize);
        }
        List<SearchResultVO.AlbumSimpleVO> records = assembleAlbums(esPage.ids());
        if (shouldFallbackAfterAssembly(records, esPage, currentPage, pageSize)) {
            return mysqlFallback.searchAlbums(keyword, userId, currentPage, pageSize);
        }
        return pageResult(keyword, records, currentPage, pageSize, esPage.total, TYPE_ALBUM);
    }

    @Override
    public SearchResultVO searchArtists(String keyword, Long userId, Integer page, Integer size) {
        int currentPage = normalizePage(page);
        int pageSize = normalizeSize(size);
        EsPage esPage = searchPage(TYPE_ARTIST, keyword, currentPage, pageSize);
        if (esPage == null) {
            return mysqlFallback.searchArtists(keyword, userId, currentPage, pageSize);
        }
        List<SearchResultVO.ArtistSimpleVO> records = assembleArtists(esPage.ids());
        if (shouldFallbackAfterAssembly(records, esPage, currentPage, pageSize)) {
            return mysqlFallback.searchArtists(keyword, userId, currentPage, pageSize);
        }
        return pageResult(keyword, records, currentPage, pageSize, esPage.total, TYPE_ARTIST);
    }

    @Override
    public SearchResultVO searchPlaylists(String keyword, Long userId, Integer page, Integer size) {
        int currentPage = normalizePage(page);
        int pageSize = normalizeSize(size);
        EsPage esPage = searchPage(TYPE_PLAYLIST, keyword, currentPage, pageSize);
        if (esPage == null) {
            return mysqlFallback.searchPlaylists(keyword, userId, currentPage, pageSize);
        }
        List<SearchResultVO.PlaylistSimpleVO> records = assemblePlaylists(esPage.ids());
        if (shouldFallbackAfterAssembly(records, esPage, currentPage, pageSize)) {
            return mysqlFallback.searchPlaylists(keyword, userId, currentPage, pageSize);
        }
        return pageResult(keyword, records, currentPage, pageSize, esPage.total, TYPE_PLAYLIST);
    }

    @Override
    public SearchResultVO searchMvs(String keyword, Long userId, Integer page, Integer size) {
        int currentPage = normalizePage(page);
        int pageSize = normalizeSize(size);
        EsPage esPage = searchPage(TYPE_MV, keyword, currentPage, pageSize);
        if (esPage == null) {
            return mysqlFallback.searchMvs(keyword, userId, currentPage, pageSize);
        }
        List<SearchResultVO.MvSimpleVO> records = assembleMvs(esPage.ids());
        if (shouldFallbackAfterAssembly(records, esPage, currentPage, pageSize)) {
            return mysqlFallback.searchMvs(keyword, userId, currentPage, pageSize);
        }
        return pageResult(keyword, records, currentPage, pageSize, esPage.total, TYPE_MV);
    }

    @Override
    public SearchResultVO searchUsers(String keyword, Long userId, Integer page, Integer size) {
        int currentPage = normalizePage(page);
        int pageSize = normalizeSize(size);
        EsPage esPage = searchPage(TYPE_USER, keyword, currentPage, pageSize);
        if (esPage == null) {
            return mysqlFallback.searchUsers(keyword, userId, currentPage, pageSize);
        }
        List<SearchResultVO.UserSimpleVO> records = assembleUsers(esPage.ids());
        if (shouldFallbackAfterAssembly(records, esPage, currentPage, pageSize)) {
            return mysqlFallback.searchUsers(keyword, userId, currentPage, pageSize);
        }
        return pageResult(keyword, records, currentPage, pageSize, esPage.total, TYPE_USER);
    }

    private EsPage searchPage(String type, String keyword, int page, int size) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", (page - 1) * size);
        body.put("size", size);
        body.put("track_total_hits", true);

        Map<String, Object> bool = new LinkedHashMap<>();
        List<Object> must = new ArrayList<>();
        Map<String, Object> multiMatch = new LinkedHashMap<>();
        multiMatch.put("query", keyword);
        multiMatch.put("fields", Arrays.asList(
                "name^5", "artist_names^4", "album_name^3", "tags^2",
                "description", "pinyin^2", "initials^2"));
        multiMatch.put("type", "best_fields");
        must.add(Collections.singletonMap("multi_match", multiMatch));

        List<Object> filters = new ArrayList<>();
        filters.add(Collections.singletonMap("term", Collections.singletonMap("type", type)));
        filters.add(Collections.singletonMap("term", Collections.singletonMap("status", CommonConstants.STATUS_NORMAL)));
        filters.add(Collections.singletonMap("term", Collections.singletonMap("deleted", CommonConstants.NOT_DELETED)));
        if (TYPE_PLAYLIST.equals(type)) {
            filters.add(Collections.singletonMap("term", Collections.singletonMap("is_public", CommonConstants.PUBLIC_PUBLIC)));
            filters.add(Collections.singletonMap("term", Collections.singletonMap("playlist_type", MusicConstants.PlaylistType.CUSTOM)));
        }
        bool.put("must", must);
        bool.put("filter", filters);
        body.put("query", Collections.singletonMap("bool", bool));

        List<Object> sorts = new ArrayList<>();
        sorts.add(Collections.singletonMap("_score", "desc"));
        sorts.add(Collections.singletonMap("popularity", "desc"));
        sorts.add(Collections.singletonMap("updated_at", "desc"));
        body.put("sort", sorts);

        try {
            String response = httpClient.request(
                    HttpMethod.POST, "/" + indexName() + "/_search", body);
            JsonNode root = objectMapper.readTree(response);
            JsonNode hitsNode = root.path("hits");
            JsonNode totalNode = hitsNode.path("total");
            long total = totalNode.isObject()
                    ? totalNode.path("value").asLong()
                    : totalNode.asLong();
            List<String> ids = new ArrayList<>();
            for (JsonNode hit : hitsNode.path("hits")) {
                JsonNode source = hit.path("_source");
                String id = source.path("id").asText();
                if (id == null || id.trim().isEmpty()) {
                    id = hit.path("_id").asText();
                    int separator = id.indexOf(':');
                    if (separator >= 0) {
                        id = id.substring(separator + 1);
                    }
                }
                if (id != null && !id.trim().isEmpty()) {
                    ids.add(id);
                }
            }
            return new EsPage(ids, total);
        } catch (Exception e) {
            log.warn("event=search_elasticsearch_fallback resourceType={} errorType={}",
                    type, e.getClass().getSimpleName());
            return null;
        }
    }

    private SearchResultVO pageResult(String keyword, List<?> records, int page, int size,
                                      long total, String type) {
        SearchResultVO result = new SearchResultVO();
        result.setKeyword(keyword);
        if (TYPE_SONG.equals(type)) {
            result.setSongs((List<SearchResultVO.SongSimpleVO>) records);
        } else if (TYPE_ALBUM.equals(type)) {
            result.setAlbums((List<SearchResultVO.AlbumSimpleVO>) records);
        } else if (TYPE_ARTIST.equals(type)) {
            result.setArtists((List<SearchResultVO.ArtistSimpleVO>) records);
        } else if (TYPE_PLAYLIST.equals(type)) {
            result.setPlaylists((List<SearchResultVO.PlaylistSimpleVO>) records);
        } else if (TYPE_MV.equals(type)) {
            result.setMvs((List<SearchResultVO.MvSimpleVO>) records);
        } else if (TYPE_USER.equals(type)) {
            result.setUsers((List<SearchResultVO.UserSimpleVO>) records);
        }
        result.setPage(page);
        result.setSize(size);
        result.setTotal(total);
        result.setPages(total <= 0 ? 0 : (int) Math.ceil((double) total / size));
        return result;
    }

    private boolean shouldFallbackAfterAssembly(List<?> records, EsPage esPage, int page, int size) {
        long expectedPageSize = Math.min(size,
                Math.max(esPage.total - (long) (page - 1) * size, 0L));
        return records == null || records.isEmpty() || records.size() < expectedPageSize;
    }

    private List<SearchResultVO.SongSimpleVO> assembleSongs(List<String> ids, Long userId) {
        List<Long> songIds = toLongIds(ids);
        if (songIds.isEmpty()) return Collections.emptyList();
        List<Song> publicSongs = filterPublicUploaderSongs(songMapper.selectBatchIds(songIds));
        Map<Long, Song> songMap = publicSongs.stream()
                .collect(Collectors.toMap(Song::getId, item -> item, (a, b) -> a));
        Set<Long> publicSongIds = publicSongs.stream().map(Song::getId).collect(Collectors.toSet());
        Set<Long> favoriteIds = getFavoriteSongIds(userId, new ArrayList<>(publicSongIds));
        List<SearchResultVO.SongSimpleVO> result = new ArrayList<>();
        for (Long id : songIds) {
            Song song = songMap.get(id);
            if (song == null || !CommonConstants.STATUS_NORMAL.equals(song.getStatus())
                    || !CommonConstants.NOT_DELETED.equals(song.getDeleted())) continue;
            SearchResultVO.SongSimpleVO vo = new SearchResultVO.SongSimpleVO();
            vo.setId(song.getId());
            vo.setName(song.getName());
            vo.setArtistNames(song.getArtistNames());
            vo.setAlbumName(song.getAlbumName());
            vo.setDuration(song.getDuration());
            vo.setMainType(song.getMainType());
            vo.setCover(UrlHelper.buildRelativeCoverUrl(song.getCover()));
            vo.setUrlStandard(UrlHelper.buildRelativeAudioUrl(song.getUrlStandard()));
            vo.setUrlHigh(UrlHelper.buildRelativeAudioUrl(song.getUrlHigh()));
            vo.setUrlLossless(UrlHelper.buildRelativeAudioUrl(song.getUrlLossless()));
            vo.setVersionType(song.getVersionType());
            vo.setVersionName(song.getVersionName());
            vo.setLanguage(song.getLanguage() == null ? "中文" : song.getLanguage());
            vo.setIsFavorite(favoriteIds.contains(id));
            result.add(vo);
        }
        return result;
    }

    private List<SearchResultVO.AlbumSimpleVO> assembleAlbums(List<String> ids) {
        List<Long> albumIds = toLongIds(ids);
        if (albumIds.isEmpty()) return Collections.emptyList();
        Map<Long, Album> map = filterAlbumsWithPublicSongs(albumMapper.selectBatchIds(albumIds)).stream()
                .collect(Collectors.toMap(Album::getId, item -> item, (a, b) -> a));
        List<SearchResultVO.AlbumSimpleVO> result = new ArrayList<>();
        for (Long id : albumIds) {
            Album album = map.get(id);
            if (album == null || !CommonConstants.STATUS_NORMAL.equals(album.getStatus())
                    || !CommonConstants.NOT_DELETED.equals(album.getDeleted())) continue;
            SearchResultVO.AlbumSimpleVO vo = new SearchResultVO.AlbumSimpleVO();
            vo.setId(album.getId());
            vo.setName(album.getName());
            vo.setArtistNames(album.getArtistNames());
            vo.setReleaseDate(album.getReleaseDate() == null ? null : album.getReleaseDate().toString());
            vo.setCover(album.getCover());
            vo.setLanguage(album.getLanguage());
            result.add(vo);
        }
        return result;
    }

    private List<SearchResultVO.ArtistSimpleVO> assembleArtists(List<String> ids) {
        List<Long> artistIds = toLongIds(ids);
        if (artistIds.isEmpty()) return Collections.emptyList();
        Map<Long, Artist> map = artistMapper.selectBatchIds(artistIds).stream()
                .collect(Collectors.toMap(Artist::getId, item -> item, (a, b) -> a));
        List<SearchResultVO.ArtistSimpleVO> result = new ArrayList<>();
        for (Long id : artistIds) {
            Artist artist = map.get(id);
            if (artist == null || !CommonConstants.STATUS_NORMAL.equals(artist.getStatus())
                    || !CommonConstants.NOT_DELETED.equals(artist.getDeleted())) continue;
            SearchResultVO.ArtistSimpleVO vo = new SearchResultVO.ArtistSimpleVO();
            vo.setId(artist.getId());
            vo.setName(artist.getName());
            vo.setType(artist.getType());
            vo.setAvatar(artist.getAvatar());
            result.add(vo);
        }
        return result;
    }

    private List<SearchResultVO.PlaylistSimpleVO> assemblePlaylists(List<String> ids) {
        List<Long> playlistIds = toLongIds(ids);
        if (playlistIds.isEmpty()) return Collections.emptyList();
        List<Playlist> rows = filterPublicCreatorPlaylists(playlistMapper.selectBatchIds(playlistIds));
        Map<Long, Playlist> map = rows.stream()
                .collect(Collectors.toMap(Playlist::getId, item -> item, (a, b) -> a));
        Map<Long, String> userNames = getUserNames(rows);
        List<SearchResultVO.PlaylistSimpleVO> result = new ArrayList<>();
        for (Long id : playlistIds) {
            Playlist playlist = map.get(id);
            if (playlist == null || !CommonConstants.STATUS_NORMAL.equals(playlist.getStatus())
                    || !CommonConstants.NOT_DELETED.equals(playlist.getDeleted())
                    || !CommonConstants.PUBLIC_PUBLIC.equals(playlist.getIsPublic())
                    || !MusicConstants.PlaylistType.CUSTOM.equals(playlist.getType())) continue;
            SearchResultVO.PlaylistSimpleVO vo = new SearchResultVO.PlaylistSimpleVO();
            vo.setId(playlist.getId());
            vo.setName(playlist.getName());
            vo.setCreatorName(userNames.get(playlist.getUserId()));
            vo.setSongCount(playlist.getSongCount() == null ? 0 : playlist.getSongCount().intValue());
            vo.setCover(playlist.getCover());
            result.add(vo);
        }
        return result;
    }

    private List<SearchResultVO.MvSimpleVO> assembleMvs(List<String> ids) {
        List<Long> mvIds = toLongIds(ids);
        if (mvIds.isEmpty()) return Collections.emptyList();
        List<MV> rows = filterPublicMvs(mvMapper.selectBatchIds(mvIds));
        Map<Long, MV> map = rows.stream()
                .collect(Collectors.toMap(MV::getId, item -> item, (a, b) -> a));
        Map<Long, String> languageMap = getSongLanguageMap(rows);
        List<SearchResultVO.MvSimpleVO> result = new ArrayList<>();
        for (Long id : mvIds) {
            MV mv = map.get(id);
            if (mv == null || !CommonConstants.STATUS_NORMAL.equals(mv.getStatus())
                    || !CommonConstants.NOT_DELETED.equals(mv.getDeleted())) continue;
            SearchResultVO.MvSimpleVO vo = new SearchResultVO.MvSimpleVO();
            vo.setId(mv.getId());
            vo.setName(mv.getName());
            vo.setArtistNames(mv.getArtistNames());
            vo.setDuration(mv.getDuration());
            vo.setPlayCount(mv.getPlayCount());
            vo.setCover(mv.getCover());
            vo.setSongLanguage(languageMap.get(mv.getSongId()));
            result.add(vo);
        }
        return result;
    }

    private List<SearchResultVO.UserSimpleVO> assembleUsers(List<String> ids) {
        List<Long> userIds = toLongIds(ids);
        if (userIds.isEmpty()) return Collections.emptyList();
        Map<Long, User> map = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, item -> item, (a, b) -> a));
        List<SearchResultVO.UserSimpleVO> result = new ArrayList<>();
        for (Long id : userIds) {
            User user = map.get(id);
            if (!UserAccountStatusUtil.canAppearInRecommendations(user)) continue;
            SearchResultVO.UserSimpleVO vo = new SearchResultVO.UserSimpleVO();
            vo.setId(user.getId());
            vo.setNickname(user.getNickname());
            vo.setAvatar(user.getAvatar());
            vo.setFansCount(user.getFansCount());
            vo.setSignature(user.getIntroduction());
            result.add(vo);
        }
        return result;
    }

    private Set<Long> getFavoriteSongIds(Long userId, List<Long> songIds) {
        if (ObjectUtils.isEmpty(userId) || songIds.isEmpty()) return Collections.emptySet();
        Playlist favorite = playlistMapper.selectOne(new LambdaQueryWrapper<Playlist>()
                .eq(Playlist::getUserId, userId)
                .eq(Playlist::getType, MusicConstants.PlaylistType.FAVORITE)
                .eq(Playlist::getDeleted, CommonConstants.NOT_DELETED)
                .select(Playlist::getId));
        if (favorite == null) return Collections.emptySet();
        return playlistSongMapper.selectList(new LambdaQueryWrapper<PlaylistSong>()
                        .eq(PlaylistSong::getPlaylistId, favorite.getId())
                        .in(PlaylistSong::getSongId, songIds)
                        .eq(PlaylistSong::getDeleted, CommonConstants.NOT_DELETED)
                        .select(PlaylistSong::getSongId))
                .stream().map(PlaylistSong::getSongId).collect(Collectors.toSet());
    }

    private Map<Long, String> getUserNames(List<Playlist> playlists) {
        Set<Long> userIds = playlists.stream().map(Playlist::getUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (userIds.isEmpty()) return Collections.emptyMap();
        return userMapper.selectList(new LambdaQueryWrapper<User>()
                        .in(User::getId, userIds)
                        .eq(User::getStatus, CommonConstants.STATUS_NORMAL)
                        .eq(User::getDeleted, CommonConstants.NOT_DELETED)
                        .select(User::getId, User::getNickname))
                .stream()
                .filter(UserAccountStatusUtil::canExposePublicContent)
                .collect(Collectors.toMap(User::getId, User::getNickname, (a, b) -> a));
    }

    private Map<Long, String> getSongLanguageMap(List<MV> mvs) {
        Set<Long> songIds = mvs.stream().map(MV::getSongId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (songIds.isEmpty()) return Collections.emptyMap();
        List<Song> songs = songMapper.selectList(new LambdaQueryWrapper<Song>()
                        .select(Song::getId, Song::getLanguage)
                        .in(Song::getId, songIds)
                        .eq(Song::getStatus, CommonConstants.STATUS_NORMAL)
                        .eq(Song::getDeleted, CommonConstants.NOT_DELETED));
        return filterPublicUploaderSongs(songs)
                .stream()
                .collect(Collectors.toMap(Song::getId, Song::getLanguage, (a, b) -> a));
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
        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Song::getAlbumId, albumIds)
                .eq(Song::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Song::getDeleted, CommonConstants.NOT_DELETED)
                .select(Song::getAlbumId, Song::getUploaderId);
        Set<Long> allowedAlbumIds = filterPublicUploaderSongs(songMapper.selectList(wrapper)).stream()
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
        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Song::getId, songIds)
                .eq(Song::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Song::getDeleted, CommonConstants.NOT_DELETED)
                .select(Song::getId, Song::getUploaderId);
        Set<Long> allowedSongIds = filterPublicUploaderSongs(songMapper.selectList(wrapper)).stream()
                .map(Song::getId)
                .collect(Collectors.toSet());
        return mvs.stream()
                .filter(mv -> mv.getSongId() == null || allowedSongIds.contains(mv.getSongId()))
                .collect(Collectors.toList());
    }

    private List<Long> toLongIds(List<String> ids) {
        List<Long> result = new ArrayList<>();
        for (String id : ids) {
            try {
                result.add(Long.valueOf(id));
            } catch (Exception ignored) {
                log.debug("event=search_elasticsearch_document_skipped reason=invalid_id");
            }
        }
        return result;
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size < 1) return SEARCH_LIMIT;
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private String indexName() {
        String prefix = indexPrefix == null ? "haoran_music" : indexPrefix.trim();
        return prefix.isEmpty() ? "haoran_music_search" : prefix + "_search";
    }

    private static class EsPage {
        private final List<String> ids;
        private final long total;

        private EsPage(List<String> ids, long total) {
            this.ids = ids;
            this.total = total;
        }

        private List<String> ids() {
            return ids;
        }
    }
}
