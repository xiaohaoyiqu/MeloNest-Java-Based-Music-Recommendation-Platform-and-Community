package com.haoran.music.service.search;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.constant.MusicConstants;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.*;
import com.haoran.music.mapper.*;
import com.haoran.music.service.MusicIntelligenceCacheService;
import com.haoran.music.service.SearchIndexSyncOutboxService;
import com.haoran.music.util.PinyinUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClientResponseException;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;






@Slf4j
@Service
public class ElasticsearchSearchIndexService implements SearchIndexService {


    private static final int BATCH_SIZE = 100;
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
    private MusicIntelligenceCacheService musicIntelligenceCacheService;

    @Resource
    private SearchIndexSyncOutboxService searchIndexSyncOutboxService;

    @Value("${search.elasticsearch.index-prefix:haoran_music}")
    private String indexPrefix;

    @Value("${search.elasticsearch.enabled:false}")
    private boolean elasticsearchEnabled;

    @Value("${search.engine:mysql}")
    private String searchEngine;

    @Override
    public Map<String, Object> rebuildAll() {
        ensureIndex();
        clearIndex();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put(TYPE_SONG, indexSongs());
        result.put(TYPE_ALBUM, indexAlbums());
        result.put(TYPE_ARTIST, indexArtists());
        result.put(TYPE_PLAYLIST, indexPlaylists());
        result.put(TYPE_MV, indexMvs());
        result.put(TYPE_USER, indexUsers());
        result.put("engine", "elasticsearch");
        result.put("index", indexName());
        bumpSearchCacheVersion("search index rebuilt");
        return result;
    }

    @Override
    public boolean isAvailable() {
        return httpClient.isAvailable();
    }

    @Override
    public long documentCount() {
        try {
            String response = httpClient.request(
                    HttpMethod.GET, "/" + indexName() + "/_count", null);
            return objectMapper.readTree(response).path("count").asLong(-1L);
        } catch (Exception e) {
            if (isNotFound(e)) {
                return 0L;
            }
            log.warn("event=search_index_count_failed errorType={}",
                    e.getClass().getSimpleName());
            return -1L;
        }
    }

    @Override
    public void sync(String type, Long id) {
        if (type == null || id == null) {
            return;
        }
        String eventId = searchIndexSyncOutboxService.record(type, id);
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronizationAdapter() {
                        @Override
                        public void afterCommit() {
                            searchIndexSyncOutboxService.dispatchEvent(eventId);
                        }
                    });
            return;
        }
        searchIndexSyncOutboxService.dispatchEvent(eventId);
    }

    @Override
    public void applyOutboxSync(String type, Long id) {
        musicIntelligenceCacheService.bumpSearchCacheVersion(
                "search content changed:" + type, null);
        if (isEnabled()) {
            syncNow(type, id);
        }
    }

    private void bumpSearchCacheVersion(String reason) {
        try {
            musicIntelligenceCacheService.bumpSearchCacheVersion(reason, null);
        } catch (Exception e) {
            log.warn("event=search_cache_version_update_failed errorType={}",
                    e.getClass().getSimpleName());
        }
    }

    private void syncNow(String type, Long id) {
        ensureIndex();
        Map<String, Object> document = loadDocument(type, id);
        String endpoint = "/" + indexName() + "/_doc/" + type + ":" + id;
        if (document == null) {
            try {
                httpClient.request(HttpMethod.DELETE, endpoint, null);
            } catch (Exception e) {
                if (!isNotFound(e)) {
                    throw e;
                }
            }
        } else {
            httpClient.request(HttpMethod.PUT, endpoint, document);
        }
    }

    private boolean isEnabled() {
        return elasticsearchEnabled && "elasticsearch".equalsIgnoreCase(searchEngine);
    }

    private Map<String, Object> loadDocument(String type, Long id) {
        if (TYPE_SONG.equals(type)) {
            Song row = songMapper.selectById(id);
            return !canIndexSong(row)
                    ? null : songDocument(row);
        }
        if (TYPE_ALBUM.equals(type)) {
            Album row = albumMapper.selectById(id);
            return row == null || !isIndexable(row.getStatus(), row.getDeleted())
                    || !hasPublicSongInAlbum(row.getId())
                    ? null : albumDocument(row);
        }
        if (TYPE_ARTIST.equals(type)) {
            Artist row = artistMapper.selectById(id);
            return row == null || !isIndexable(row.getStatus(), row.getDeleted())
                    ? null : artistDocument(row);
        }
        if (TYPE_PLAYLIST.equals(type)) {
            Playlist row = playlistMapper.selectById(id);
            return row == null || !isIndexable(row.getStatus(), row.getDeleted())
                    || !CommonConstants.PUBLIC_PUBLIC.equals(row.getIsPublic())
                    || !MusicConstants.PlaylistType.CUSTOM.equals(row.getType())
                    || !UserAccountStatusUtil.canExposePublicContent(row.getUserId(), userMapper::selectById)
                    ? null : playlistDocument(row);
        }
        if (TYPE_MV.equals(type)) {
            MV row = mvMapper.selectById(id);
            return row == null || !isIndexable(row.getStatus(), row.getDeleted())
                    || !canIndexMv(row)
                    ? null : mvDocument(row);
        }
        if (TYPE_USER.equals(type)) {
            User row = userMapper.selectById(id);
            return !UserAccountStatusUtil.canAppearInRecommendations(row)
                    ? null : userDocument(row);
        }
        return null;
    }

    private boolean isIndexable(Integer status, Integer deleted) {
        return CommonConstants.STATUS_NORMAL.equals(status)
                && CommonConstants.NOT_DELETED.equals(deleted);
    }

    private boolean canIndexSong(Song song) {
        return canIndexSongBase(song)
                && (song.getUploaderId() == null
                || UserAccountStatusUtil.canExposePublicContent(song.getUploaderId(), userMapper::selectById));
    }

    private boolean canIndexSongBase(Song song) {
        return song != null && isIndexable(song.getStatus(), song.getDeleted());
    }

    private boolean canIndexMv(MV mv) {
        if (mv == null || !isIndexable(mv.getStatus(), mv.getDeleted())) {
            return false;
        }
        if (mv.getSongId() == null) {
            return true;
        }
        return canIndexSong(songMapper.selectById(mv.getSongId()));
    }

    private boolean hasPublicSongInAlbum(Long albumId) {
        if (albumId == null) {
            return false;
        }
        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Song::getAlbumId, albumId)
                .eq(Song::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Song::getDeleted, CommonConstants.NOT_DELETED)
                .select(Song::getId, Song::getUploaderId)
                .last("LIMIT 1000");
        return songMapper.selectList(wrapper).stream()
                .anyMatch(this::canIndexSong);
    }

    boolean isNotFound(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof RestClientResponseException
                    && ((RestClientResponseException) current).getRawStatusCode() == 404) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && (message.contains("HTTP 404")
                    || message.contains("404 Not Found"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void ensureIndex() {
        boolean exists = false;
        try {
            httpClient.request(HttpMethod.GET, "/" + indexName(), null);
            exists = true;
        } catch (Exception ignored) {

        }
        if (exists) {
            updateIndexMappings();
            return;
        }

        Map<String, Object> properties = indexProperties();
        Map<String, Object> mappings = new LinkedHashMap<>();
        mappings.put("dynamic", false);
        mappings.put("properties", properties);
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("number_of_shards", 1);
        settings.put("number_of_replicas", 0);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("settings", settings);
        body.put("mappings", mappings);
        httpClient.request(HttpMethod.PUT, "/" + indexName(), body);
    }

    private void updateIndexMappings() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("properties", indexProperties());
        httpClient.request(HttpMethod.PUT, "/" + indexName() + "/_mapping", body);
    }

    Map<String, Object> indexProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", field("keyword"));
        properties.put("type", field("keyword"));
        properties.put("name", textField());
        properties.put("artist_names", textField());
        properties.put("album_name", textField());
        properties.put("description", textField());
        properties.put("tags", textField());
        properties.put("main_type", textField());
        properties.put("language", field("keyword"));
        properties.put("pinyin", field("text"));
        properties.put("initials", field("text"));
        properties.put("cover", keywordField(2048));
        properties.put("popularity", field("long"));
        properties.put("status", field("integer"));
        properties.put("deleted", field("integer"));
        properties.put("is_public", field("integer"));
        properties.put("playlist_type", field("integer"));
        properties.put("updated_at", field("date"));
        return properties;
    }

    private void clearIndex() {
        try {
            httpClient.request(HttpMethod.DELETE, "/" + indexName(), null);
        } catch (Exception e) {
            if (!isNotFound(e)) {
                throw e;
            }
        }
        ensureIndex();
    }

    private int indexSongs() {
        return indexPages(TYPE_SONG, (page, wrapper) -> songMapper.selectPage(page, wrapper),
                this::songDocument);
    }

    private int indexAlbums() {
        return indexPages(TYPE_ALBUM, (page, wrapper) -> albumMapper.selectPage(page, wrapper),
                this::albumDocument);
    }

    private int indexArtists() {
        return indexPages(TYPE_ARTIST, (page, wrapper) -> artistMapper.selectPage(page, wrapper),
                this::artistDocument);
    }

    private int indexPlaylists() {
        return indexPages(TYPE_PLAYLIST, (page, wrapper) -> playlistMapper.selectPage(page, wrapper),
                this::playlistDocument);
    }

    private int indexMvs() {
        return indexPages(TYPE_MV, (page, wrapper) -> mvMapper.selectPage(page, wrapper),
                this::mvDocument);
    }

    private int indexUsers() {
        return indexPages(TYPE_USER, (page, wrapper) -> userMapper.selectPage(page, wrapper),
                this::userDocument);
    }

    private <T> int indexPages(String type, PageSelector<T> selector, DocumentBuilder<T> builder) {
        int pageNumber = 1;
        int total = 0;
        while (true) {
            Page<T> current = new Page<>(pageNumber, BATCH_SIZE, false);
            QueryWrapper<T> wrapper = new QueryWrapper<T>()
                    .eq("status", CommonConstants.STATUS_NORMAL)
                    .eq("deleted", CommonConstants.NOT_DELETED)
                    .orderByAsc("id");
            if (TYPE_PLAYLIST.equals(type)) {
                wrapper.eq("is_public", CommonConstants.PUBLIC_PUBLIC)
                        .eq("type", MusicConstants.PlaylistType.CUSTOM);
            }
            IPage<T> result = selector.select(current, wrapper);
            if (result.getRecords() == null || result.getRecords().isEmpty()) {
                break;
            }
            total += bulkIndex(type, result.getRecords(), builder);
            if (result.getRecords().size() < BATCH_SIZE) {
                break;
            }
            pageNumber++;
        }
        log.info("event=search_index_batch_completed resourceType={} documentCount={}", type, total);
        return total;
    }

    private <T> int bulkIndex(String type, List<T> rows, DocumentBuilder<T> builder) {
        StringBuilder payload = new StringBuilder();
        int indexed = 0;
        for (T row : filterRowsForPublicIndex(type, rows)) {
            Map<String, Object> document = builder.build(row);
            if (document == null) {
                continue;
            }
            Map<String, Object> action = new LinkedHashMap<>();
            action.put("index", Collections.singletonMap("_id", type + ":" + idOf(row)));
            payload.append(write(action)).append('\n');
            payload.append(write(document)).append('\n');
            indexed++;
        }
        if (indexed == 0) {
            return 0;
        }
        String response = httpClient.request(HttpMethod.POST, "/" + indexName() + "/_bulk",
                payload.toString(), MediaType.parseMediaType("application/x-ndjson"));
        try {
            JsonNode root = objectMapper.readTree(response);
            int failed = countBulkFailures(root, type);
            if (failed > 0) {
                log.warn("event=search_index_bulk_partial_failure resourceType={} successCount={} failureCount={}",
                        type, indexed - failed, failed);
            }
            return indexed - failed;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("无法解析 ES bulk 响应", e);
        }
    }

    int countBulkFailures(JsonNode root, String type) {
        if (root == null || !root.path("errors").asBoolean(false)) {
            return 0;
        }
        int failed = 0;
        for (JsonNode item : root.path("items")) {
            JsonNode result = item.path("index");
            if (result.path("status").asInt(200) < 300) {
                continue;
            }
            failed++;
            if (failed <= 5) {
                log.warn("event=search_index_document_rejected resourceType={} documentId={} status={} errorType={} errorReason={}",
                        type,
                        result.path("_id").asText("unknown"),
                        result.path("status").asInt(),
                        result.path("error").path("type").asText("unknown"),
                        compactLogValue(result.path("error").path("reason").asText("unknown")));
            }
        }
        return failed;
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> filterRowsForPublicIndex(String type, List<T> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        if (TYPE_SONG.equals(type)) {
            return (List<T>) filterPublicSongs((List<Song>) (List<?>) rows);
        }
        if (TYPE_ALBUM.equals(type)) {
            return (List<T>) filterAlbumsWithPublicSongs((List<Album>) (List<?>) rows);
        }
        if (TYPE_PLAYLIST.equals(type)) {
            return (List<T>) filterPublicPlaylists((List<Playlist>) (List<?>) rows);
        }
        if (TYPE_MV.equals(type)) {
            return (List<T>) filterPublicMvs((List<MV>) (List<?>) rows);
        }
        if (TYPE_USER.equals(type)) {
            return rows.stream()
                    .map(row -> (User) row)
                    .filter(UserAccountStatusUtil::canAppearInRecommendations)
                    .map(user -> (T) user)
                    .collect(java.util.stream.Collectors.toList());
        }
        return rows;
    }

    private List<Song> filterPublicSongs(List<Song> songs) {
        if (songs == null || songs.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> uploaderIds = songs.stream()
                .map(Song::getUploaderId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        Set<Long> allowedUploaderIds = UserAccountStatusUtil.filterPublicContentUserIds(
                uploaderIds, ids -> userMapper.selectBatchIds(ids));
        return songs.stream()
                .filter(song -> canIndexSongBase(song)
                        && (song.getUploaderId() == null || allowedUploaderIds.contains(song.getUploaderId())))
                .collect(java.util.stream.Collectors.toList());
    }

    private List<Playlist> filterPublicPlaylists(List<Playlist> playlists) {
        if (playlists == null || playlists.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> userIds = playlists.stream()
                .map(Playlist::getUserId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        Set<Long> allowedUserIds = UserAccountStatusUtil.filterPublicContentUserIds(
                userIds, ids -> userMapper.selectBatchIds(ids));
        return playlists.stream()
                .filter(playlist -> playlist != null
                        && isIndexable(playlist.getStatus(), playlist.getDeleted())
                        && CommonConstants.PUBLIC_PUBLIC.equals(playlist.getIsPublic())
                        && MusicConstants.PlaylistType.CUSTOM.equals(playlist.getType())
                        && playlist.getUserId() != null
                        && allowedUserIds.contains(playlist.getUserId()))
                .collect(java.util.stream.Collectors.toList());
    }

    private List<Album> filterAlbumsWithPublicSongs(List<Album> albums) {
        if (albums == null || albums.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> albumIds = albums.stream()
                .filter(album -> album != null && isIndexable(album.getStatus(), album.getDeleted()))
                .map(Album::getId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (albumIds.isEmpty()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Song::getAlbumId, albumIds)
                .eq(Song::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Song::getDeleted, CommonConstants.NOT_DELETED)
                .select(Song::getAlbumId, Song::getUploaderId);
        Set<Long> allowedAlbumIds = filterPublicSongs(songMapper.selectList(wrapper)).stream()
                .map(Song::getAlbumId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        return albums.stream()
                .filter(album -> album != null
                        && isIndexable(album.getStatus(), album.getDeleted())
                        && allowedAlbumIds.contains(album.getId()))
                .collect(java.util.stream.Collectors.toList());
    }

    private List<MV> filterPublicMvs(List<MV> mvs) {
        if (mvs == null || mvs.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> songIds = mvs.stream()
                .map(MV::getSongId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (songIds.isEmpty()) {
            return mvs.stream()
                    .filter(mv -> mv != null && isIndexable(mv.getStatus(), mv.getDeleted()))
                    .collect(java.util.stream.Collectors.toList());
        }
        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Song::getId, songIds)
                .eq(Song::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Song::getDeleted, CommonConstants.NOT_DELETED)
                .select(Song::getId, Song::getUploaderId);
        Set<Long> allowedSongIds = filterPublicSongs(songMapper.selectList(wrapper)).stream()
                .map(Song::getId)
                .collect(java.util.stream.Collectors.toSet());
        return mvs.stream()
                .filter(mv -> mv != null
                        && isIndexable(mv.getStatus(), mv.getDeleted())
                        && (mv.getSongId() == null || allowedSongIds.contains(mv.getSongId())))
                .collect(java.util.stream.Collectors.toList());
    }

    private Map<String, Object> songDocument(Song song) {
        Map<String, Object> doc = baseDocument(TYPE_SONG, song.getId(), song.getName(),
                song.getDescription(), song.getTags(), song.getStatus(), song.getDeleted(),
                song.getUpdateTime(), popularity(song.getPlayCount(), song.getFavoriteCount()));
        doc.put("artist_names", safeText(song.getArtistNames(), 1024));
        doc.put("album_name", safeText(song.getAlbumName(), 512));
        doc.put("main_type", safeText(song.getMainType(), 256));
        doc.put("language", safeText(song.getLanguage(), 64));
        addPinyin(doc, song.getName(), song.getArtistNames(), song.getAlbumName());
        doc.put("cover", safeText(song.getCover(), 2048));
        return doc;
    }

    private Map<String, Object> albumDocument(Album album) {
        Map<String, Object> doc = baseDocument(TYPE_ALBUM, album.getId(), album.getName(),
                album.getDescription(), album.getGenres(), album.getStatus(), album.getDeleted(),
                album.getUpdateTime(), popularity(album.getPlayCount(), album.getFavoriteCount()));
        doc.put("artist_names", safeText(album.getArtistNames(), 1024));
        doc.put("language", safeText(album.getLanguage(), 64));
        addPinyin(doc, album.getName(), album.getArtistNames(), null);
        doc.put("cover", safeText(album.getCover(), 2048));
        return doc;
    }

    private Map<String, Object> artistDocument(Artist artist) {
        Map<String, Object> doc = baseDocument(TYPE_ARTIST, artist.getId(), artist.getName(),
                artist.getDescription(), artist.getGenres(), artist.getStatus(), artist.getDeleted(),
                artist.getUpdateTime(), popularity(artist.getPlayCount(), artist.getFansCount()));
        addPinyin(doc, artist.getName(), null, null);
        doc.put("cover", safeText(artist.getCover(), 2048));
        return doc;
    }

    private Map<String, Object> playlistDocument(Playlist playlist) {
        Map<String, Object> doc = baseDocument(TYPE_PLAYLIST, playlist.getId(), playlist.getName(),
                join(playlist.getDescription(), playlist.getIntro()), playlist.getTags(),
                playlist.getStatus(), playlist.getDeleted(), playlist.getUpdateTime(),
                popularity(playlist.getPlayCount(), playlist.getFavoriteCount()));
        doc.put("is_public", playlist.getIsPublic());
        doc.put("playlist_type", playlist.getType());
        addPinyin(doc, playlist.getName(), null, null);
        doc.put("cover", safeText(playlist.getCover(), 2048));
        return doc;
    }

    private Map<String, Object> mvDocument(MV mv) {
        Map<String, Object> doc = baseDocument(TYPE_MV, mv.getId(), mv.getName(),
                mv.getDescription(), mv.getTags(), mv.getStatus(), mv.getDeleted(),
                mv.getUpdateTime(), mv.getPlayCount());
        doc.put("artist_names", safeText(mv.getArtistNames(), 1024));
        addPinyin(doc, mv.getName(), mv.getArtistNames(), null);
        doc.put("cover", safeText(mv.getCover(), 2048));
        return doc;
    }

    private Map<String, Object> userDocument(User user) {
        Map<String, Object> doc = baseDocument(TYPE_USER, user.getId(), user.getNickname(),
                user.getIntroduction(), null, user.getStatus(), user.getDeleted(),
                user.getUpdateTime(), user.getFansCount() == null
                        ? 0L : user.getFansCount().longValue());
        doc.put("artist_names", safeText(user.getUsername(), 256));
        addPinyin(doc, user.getNickname(), user.getUsername(), null);
        doc.put("cover", safeText(user.getAvatar(), 2048));
        return doc;
    }

    private Map<String, Object> baseDocument(String type, Long id, String name, String description,
                                             String tags, Integer status, Integer deleted,
                                             LocalDateTime updatedAt, Long popularity) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", String.valueOf(id));
        doc.put("type", type);
        doc.put("name", safeText(name, 512));
        doc.put("description", safeText(description, 4096));
        doc.put("tags", safeText(tags, 1024));
        doc.put("status", status);
        doc.put("deleted", deleted);
        doc.put("popularity", popularity == null ? 0L : popularity);
        doc.put("updated_at", updatedAt == null ? null : updatedAt.toString());
        doc.put("is_public", 1);
        return doc;
    }

    private void addPinyin(Map<String, Object> doc, String... values) {
        StringBuilder text = new StringBuilder();
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                if (text.length() > 0) text.append(' ');
                text.append(value.trim());
            }
        }
        String source = text.toString();
        if (!source.isEmpty()) {
            String boundedSource = safeText(source, 2048);
            doc.put("pinyin", safeText(PinyinUtil.toPinyin(boundedSource).replace(" ", ""), 4096));
            doc.put("initials", safeText(PinyinUtil.toPinyinInitial(boundedSource), 2048));
        }
    }

    String safeText(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String compactLogValue(String value) {
        if (value == null) {
            return "unknown";
        }
        return safeText(value.replace('\r', ' ').replace('\n', ' '), 240);
    }

    private Long popularity(Long first, Long second) {
        return safe(first) + safe(second);
    }

    private Long safe(Long value) {
        return value == null ? 0L : value;
    }

    private String join(String first, String second) {
        if (first == null || first.trim().isEmpty()) return second;
        if (second == null || second.trim().isEmpty()) return first;
        return first + " " + second;
    }

    private Object field(String type) {
        return Collections.singletonMap("type", type);
    }

    private Map<String, Object> textField() {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("type", "text");
        field.put("fields", Collections.singletonMap("keyword", keywordField(256)));
        return field;
    }

    private Map<String, Object> keywordField(int ignoreAbove) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("type", "keyword");
        field.put("ignore_above", ignoreAbove);
        return field;
    }

    private Long idOf(Object entity) {
        if (entity instanceof Song) return ((Song) entity).getId();
        if (entity instanceof Album) return ((Album) entity).getId();
        if (entity instanceof Artist) return ((Artist) entity).getId();
        if (entity instanceof Playlist) return ((Playlist) entity).getId();
        if (entity instanceof MV) return ((MV) entity).getId();
        if (entity instanceof User) return ((User) entity).getId();
        throw new IllegalArgumentException("不支持的 ES 索引实体: " + entity.getClass());
    }

    private String indexName() {
        String prefix = indexPrefix == null ? "haoran_music" : indexPrefix.trim();
        return prefix.isEmpty() ? "haoran_music_search" : prefix + "_search";
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("无法序列化 ES 文档", e);
        }
    }

    private interface PageSelector<T> {
        IPage<T> select(Page<T> page, QueryWrapper<T> wrapper);
    }

    private interface DocumentBuilder<T> {
        Map<String, Object> build(T value);
    }
}
