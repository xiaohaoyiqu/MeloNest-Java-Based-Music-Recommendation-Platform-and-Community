package com.haoran.music.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.util.CommonUtil;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.common.util.WorkProcessingUtil;
import com.haoran.music.entity.Album;
import com.haoran.music.entity.Artist;
import com.haoran.music.entity.CreatorWork;
import com.haoran.music.entity.Song;
import com.haoran.music.entity.User;
import com.haoran.music.entity.UserWork;
import com.haoran.music.mapper.AlbumMapper;
import com.haoran.music.mapper.SongMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.MusicIntelligenceCacheService;
import com.haoran.music.service.OfficialMediaDerivativeService;
import com.haoran.music.service.SubmissionAlbumPublishService;
import com.haoran.music.service.SubmissionFileSecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;






@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionAlbumPublishServiceImpl implements SubmissionAlbumPublishService {

    private static final int WORK_TYPE_ALBUM = 2;
    private static final int WORK_TYPE_EP = 3;
    private static final int BATCH_LOOKUP_SIZE = 500;
    private static final String DEFAULT_COVER = "/default-cover.png";
    private static final String ALBUM_TYPE_ALBUM = "\u4e13\u8f91";
    private static final String ALBUM_TYPE_EP = "EP";
    private static final Set<String> AUDIO_EXTENSIONS = new HashSet<>(Arrays.asList(
            "mp3", "flac", "wav", "m4a", "aac", "ogg", "wma", "ape"));
    private static final Set<String> IMAGE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "gif", "webp"));
    private static final Set<String> LYRIC_EXTENSIONS = new HashSet<>(Arrays.asList(
            "lrc", "txt"));

    private final AlbumMapper albumMapper;
    private final SongMapper songMapper;
    private final UserMapper userMapper;
    private final WorkProcessingUtil workProcessingUtil;
    private final MusicIntelligenceCacheService musicIntelligenceCacheService;
    private final OfficialMediaDerivativeService officialMediaDerivativeService;
    private final ObjectMapper objectMapper;
    private final SubmissionFileSecurityService submissionFileSecurityService;

    @javax.annotation.Resource
    private com.haoran.music.service.ArtistProfileService artistProfileService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlbumPublishResult publishCreatorAlbum(CreatorWork work) {
        if (work == null || !isAlbumLikeWork(work.getWorkType())) {
            return AlbumPublishResult.empty();
        }

        SubmissionMetadata metadata = new SubmissionMetadata();
        metadata.source = "creator_work";
        metadata.workId = work.getId();
        metadata.userId = work.getUserId();
        metadata.workType = work.getWorkType();
        metadata.title = work.getWorkName();
        metadata.coverUrl = work.getCoverUrl();
        metadata.fileUrls = work.getFileUrls();
        metadata.description = work.getDescription();
        metadata.tags = work.getTags();
        metadata.language = work.getLanguage();
        metadata.allowDownload = work.getAllowDownload();
        metadata.allowComment = work.getAllowComment();
        metadata.allowShare = work.getAllowShare();


        metadata.isPaid = 0;
        metadata.price = null;
        metadata.artistName = resolveUserName(work.getUserId(), "creator_");
        return publish(metadata);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlbumPublishResult publishUserAlbum(UserWork work) {
        if (work == null || !isAlbumLikeWork(work.getWorkType())) {
            return AlbumPublishResult.empty();
        }

        SubmissionMetadata metadata = new SubmissionMetadata();
        metadata.source = "user_work";
        metadata.workId = work.getId();
        metadata.userId = work.getUserId();
        metadata.workType = work.getWorkType();
        metadata.title = work.getWorkName();
        metadata.coverUrl = work.getCoverUrl();
        metadata.fileUrls = work.getFileUrls();
        metadata.description = work.getDescription();
        metadata.tags = work.getTags();
        metadata.language = work.getLanguage();
        metadata.allowDownload = work.getAllowDownload();
        metadata.allowComment = work.getAllowComment();
        metadata.allowShare = work.getAllowShare();
        metadata.isPaid = 0;
        metadata.artistName = StrUtil.isNotBlank(work.getNickname())
                ? work.getNickname()
                : resolveUserName(work.getUserId(), "user_");
        return publish(metadata);
    }

    @Override
    public void enrichUserWorks(List<UserWork> works) {
        enrichAlbumLinks(works, UserWork::getWorkType, UserWork::getSongId,
                UserWork::setAlbumId, UserWork::setAlbumName);
    }

    @Override
    public void enrichCreatorWorks(List<CreatorWork> works) {
        enrichAlbumLinks(works, CreatorWork::getWorkType, CreatorWork::getAutoSongId,
                CreatorWork::setAlbumId, CreatorWork::setAlbumName);
    }

    private <T> void enrichAlbumLinks(List<T> works,
                                      Function<T, Integer> workTypeGetter,
                                      Function<T, Long> songIdGetter,
                                      BiConsumer<T, Long> albumIdSetter,
                                      BiConsumer<T, String> albumNameSetter) {
        if (works == null || works.isEmpty()) {
            return;
        }
        List<Long> songIds = works.stream()
                .filter(work -> isAlbumLikeWork(workTypeGetter.apply(work)))
                .map(songIdGetter)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (songIds.isEmpty()) {
            return;
        }

        List<Song> songs = selectSongsByIds(songIds);
        if (songs.isEmpty()) {
            return;
        }
        Map<Long, Song> songMap = songs.stream()
                .collect(Collectors.toMap(Song::getId, song -> song, (left, right) -> left));
        List<Long> albumIds = songs.stream()
                .map(Song::getAlbumId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Album> albumMap = Collections.emptyMap();
        if (!albumIds.isEmpty()) {
            List<Album> albums = selectAlbumsByIds(albumIds);
            if (!albums.isEmpty()) {
                albumMap = albums.stream()
                        .collect(Collectors.toMap(Album::getId, album -> album, (left, right) -> left));
            }
        }

        for (T work : works) {
            if (!isAlbumLikeWork(workTypeGetter.apply(work))) {
                continue;
            }
            Song song = songMap.get(songIdGetter.apply(work));
            if (song == null || song.getAlbumId() == null) {
                continue;
            }
            albumIdSetter.accept(work, song.getAlbumId());
            Album album = albumMap.get(song.getAlbumId());
            albumNameSetter.accept(work, album != null ? album.getName() : song.getAlbumName());
        }
    }

    private List<Song> selectSongsByIds(List<Long> songIds) {
        return selectInBatches(songIds, ids -> songMapper.selectBatchIds(ids));
    }

    private List<Album> selectAlbumsByIds(List<Long> albumIds) {
        return selectInBatches(albumIds, ids -> albumMapper.selectBatchIds(ids));
    }

    private <T> List<T> selectInBatches(List<Long> ids, Function<List<Long>, List<T>> selector) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<T> result = new ArrayList<>();
        for (int start = 0; start < ids.size(); start += BATCH_LOOKUP_SIZE) {
            int end = Math.min(start + BATCH_LOOKUP_SIZE, ids.size());
            List<T> batch = selector.apply(ids.subList(start, end));
            if (batch != null && !batch.isEmpty()) {
                result.addAll(batch);
            }
        }
        return result;
    }

    private AlbumPublishResult publish(SubmissionMetadata metadata) {
        User submitter = userMapper.selectById(metadata.userId);
        if (!UserAccountStatusUtil.canExposePublicContent(submitter)) {
            throw new IllegalStateException(UserAccountStatusUtil.currentUnavailableMessage(submitter) + "，无法发布专辑投稿");
        }

        List<SubmittedFile> submittedFiles = parseSubmittedFiles(metadata.fileUrls);
        List<SubmittedFile> audioFiles = filterByType(submittedFiles, "audio");
        if (audioFiles.isEmpty()) {
            log.warn("event=submission_album_publish_rejected reason=no_audio source={} workId={} fileListEmpty={}",
                    metadata.source, metadata.workId, StrUtil.isBlank(metadata.fileUrls));
            throw new IllegalStateException("album/EP submissions contain no audio files");
        }
        submissionFileSecurityService.validateAlbumFileUrls(metadata.userId, metadata.fileUrls, true);
        validateAudioFiles(metadata, audioFiles);

        Artist artist = artistProfileService.resolveOwnedProfile(
                metadata.userId, metadata.artistName, metadata.source);
        Album album = getOrCreateAlbum(metadata, artist, firstFileUrl(filterByType(submittedFiles, "image")));
        List<Long> songIds = new ArrayList<>();

        int trackNo = 1;
        for (SubmittedFile audioFile : audioFiles) {
            Long songId = createSongFromTrack(metadata, album, artist, audioFile, submittedFiles, trackNo);
            if (songId != null) {
                songIds.add(songId);
            }
            trackNo++;
        }

        refreshAlbumSongCount(album.getId());
        log.info("event=submission_album_published source={} workId={} albumId={} trackCount={}",
                metadata.source, metadata.workId, album.getId(), songIds.size());
        if (!songIds.isEmpty()) {
            bumpCandidateCacheVersion("album submission published:" + metadata.source + ":" + metadata.workId);
        }
        return new AlbumPublishResult(album.getId(), songIds);
    }

    private void bumpCandidateCacheVersion(String reason) {
        try {
            musicIntelligenceCacheService.bumpCandidateCacheVersion(reason, null);
            musicIntelligenceCacheService.bumpRecommendCacheVersion(reason, null);
            musicIntelligenceCacheService.bumpRankingCacheVersion(reason, null);
        } catch (Exception e) {
            log.warn("event=submission_album_cache_version_update_failed errorType={}",
                    e.getClass().getSimpleName());
        }
    }

    private void validateAudioFiles(SubmissionMetadata metadata, List<SubmittedFile> audioFiles) {
        for (SubmittedFile audioFile : audioFiles) {
            File sourceFile = submissionFileSecurityService.resolveSubmissionFile(metadata.userId, audioFile.url);
            if (sourceFile == null) {
                throw new IllegalStateException("submitted audio URL cannot be resolved: " + audioFile.url);
            }
            if (audioFile.size == null || audioFile.size <= 0) {
                audioFile.size = sourceFile.length();
            }
            log.debug("event=submission_album_audio_verified source={} workId={} sizeBytes={}",
                    metadata.source, metadata.workId, audioFile.size);
        }
    }

    private Album getOrCreateAlbum(SubmissionMetadata metadata, Artist artist, String fallbackCoverUrl) {
        String albumName = StrUtil.blankToDefault(metadata.title, "Untitled Album");
        Album existing = albumMapper.selectOne(new LambdaQueryWrapper<Album>()
                .eq(Album::getName, albumName)
                .eq(Album::getArtistId, artist.getId())
                .eq(Album::getDeleted, CommonConstants.NOT_DELETED)
                .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }

        LocalDateTime now = LocalDateTime.now();
        Album album = new Album();
        album.setName(albumName);
        album.setOriginalName(albumName);
        album.setArtistId(artist.getId());
        album.setArtistIds(String.valueOf(artist.getId()));
        album.setArtistNames(artist.getName());
        album.setCover(firstNotBlank(metadata.coverUrl, fallbackCoverUrl, DEFAULT_COVER));
        album.setDescription(metadata.description);
        album.setReleaseDate(LocalDate.now());
        album.setType(WORK_TYPE_EP == metadata.workType ? ALBUM_TYPE_EP : ALBUM_TYPE_ALBUM);
        album.setGenres(metadata.tags);
        album.setLanguage(mapLanguage(metadata.language));
        album.setPriority(0);
        album.setIsPaid(0);
        album.setPrice(null);
        album.setSongCount(0L);
        album.setPlayCount(0L);
        album.setFavoriteCount(0L);
        album.setCommentCount(0L);
        album.setStatus(CommonConstants.STATUS_NORMAL);
        album.setAllowDownload(defaultInt(metadata.allowDownload, CommonConstants.YES));
        album.setAllowComment(defaultInt(metadata.allowComment, CommonConstants.YES));
        album.setAllowShare(defaultInt(metadata.allowShare, CommonConstants.YES));
        album.setDeleted(CommonConstants.NOT_DELETED);
        album.setCreateTime(now);
        album.setUpdateTime(now);
        albumMapper.insert(album);
        return album;
    }

    private Long createSongFromTrack(SubmissionMetadata metadata, Album album, Artist artist,
                                     SubmittedFile audioFile, List<SubmittedFile> submittedFiles, int trackNo) {
        if (StrUtil.isBlank(audioFile.url)) {
            return null;
        }

        String trackName = trackName(audioFile, metadata.title, trackNo);
        Song existing = songMapper.selectOne(new LambdaQueryWrapper<Song>()
                .eq(Song::getAlbumId, album.getId())
                .eq(Song::getName, trackName)
                .eq(Song::getDeleted, CommonConstants.NOT_DELETED)
                .last("LIMIT 1"));
        if (existing != null) {
            if (!Objects.equals(existing.getAlbumTrackNo(), trackNo)) {
                existing.setAlbumTrackNo(trackNo);
                existing.setUpdateTime(LocalDateTime.now());
                songMapper.updateById(existing);
            }
            return existing.getId();
        }

        LocalDateTime now = LocalDateTime.now();
        SubmittedFile lyricFile = findMatchingLyric(audioFile, submittedFiles);
        Integer quality = audioFile.qualityLevel != null ? audioFile.qualityLevel : 2;
        Song song = new Song();
        song.setName(trackName);
        song.setOriginalName(StrUtil.blankToDefault(audioFile.name, trackName));
        song.setCover(album.getCover());
        song.setArtistId(artist.getId());
        song.setArtistIds(String.valueOf(artist.getId()));
        song.setArtistNames(artist.getName());
        song.setUploaderId(metadata.userId);
        song.setAlbumId(album.getId());
        song.setAlbumName(album.getName());
        song.setAlbumTrackNo(trackNo);
        song.setReleaseDate(LocalDate.now());
        song.setDuration(audioFile.duration != null ? audioFile.duration : 0);
        song.setMainGenre(metadata.tags);
        song.setMainType(metadata.source);
        song.setTags(metadata.tags);
        song.setLanguage(mapLanguage(metadata.language));
        song.setDescription(metadata.description);
        song.setVersionType(metadata.source);
        song.setHasLyric(lyricFile != null ? CommonConstants.YES : CommonConstants.NO);
        if (lyricFile != null) {
            song.setLyricsFile(lyricFile.url);
            song.setLyricLanguage(mapLanguage(metadata.language));
        }
        applyInitialQuality(song, quality, audioFile.url, audioFile.size);
        song.setPlayCount(0L);
        song.setFavoriteCount(0L);
        song.setCommentCount(0L);
        song.setLikeCount(0L);
        song.setReplyCount(0L);
        song.setShareCount(0L);
        song.setDownloadCount(0L);
        song.setAvgRating(BigDecimal.ZERO);
        song.setPriority(0);
        song.setIsVipOnly(CommonConstants.NO);
        song.setIsPaid(0);
        song.setIsSingle(CommonConstants.NO);
        song.setIsNew(CommonConstants.YES);
        song.setIsHot(CommonConstants.NO);
        song.setStatus(CommonConstants.STATUS_NORMAL);
        song.setDeleted(CommonConstants.NOT_DELETED);
        song.setCreateTime(now);
        song.setUpdateTime(now);

        songMapper.insert(song);
        workProcessingUtil.createSongArtistRelation(song.getId(), artist.getId(), artist.getName());
        officialMediaDerivativeService.submitSongDerivativeJob(song.getId(), audioFile.url, audioFile.size, quality);
        return song.getId();
    }

    private void applyInitialQuality(Song song, Integer quality, String url, Long size) {
        int level = quality != null ? quality : 2;
        if (level <= 1) {
            song.setUrlStandard(url);
            song.setSizeStandard(size);
        } else if (level == 2) {
            song.setUrlHigh(url);
            song.setSizeHigh(size);
        } else {
            song.setUrlLossless(url);
            song.setSizeLossless(size);
        }
    }

    private void refreshAlbumSongCount(Long albumId) {
        Long songCount = songMapper.selectCount(new LambdaQueryWrapper<Song>()
                .eq(Song::getAlbumId, albumId)
                .eq(Song::getDeleted, CommonConstants.NOT_DELETED));
        Album update = new Album();
        update.setId(albumId);
        update.setSongCount(songCount);
        update.setUpdateTime(LocalDateTime.now());
        albumMapper.updateById(update);
    }

    private List<SubmittedFile> parseSubmittedFiles(String fileUrlsJson) {
        List<SubmittedFile> files = new ArrayList<>();
        if (StrUtil.isBlank(fileUrlsJson)) {
            return files;
        }

        try {
            JsonNode root = objectMapper.readTree(fileUrlsJson);
            collectSubmittedFiles(root, files);
        } catch (Exception e) {
            log.warn("event=submission_album_file_list_parse_fallback errorType={}",
                    e.getClass().getSimpleName());
            parsePlainUrls(fileUrlsJson, files);
        }
        return dedupeByUrl(files);
    }

    private void collectSubmittedFiles(JsonNode node, List<SubmittedFile> files) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectSubmittedFiles(child, files);
            }
            return;
        }
        if (node.isTextual()) {
            addTextUrl(node.asText(), files);
            return;
        }
        if (!node.isObject()) {
            return;
        }

        if (looksLikeFileNode(node)) {
            SubmittedFile file = toSubmittedFile(node);
            if (file != null) {
                files.add(file);
            }
            return;
        }

        Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
        while (iterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = iterator.next();
            collectSubmittedFiles(entry.getValue(), files);
        }
    }

    private boolean looksLikeFileNode(JsonNode node) {
        return hasText(node, "url")
                || hasText(node, "fileUrl")
                || hasText(node, "file_url")
                || hasText(node, "path");
    }

    private SubmittedFile toSubmittedFile(JsonNode node) {
        String url = firstText(node, "url", "fileUrl", "file_url", "path");
        if (StrUtil.isBlank(url)) {
            return null;
        }
        SubmittedFile file = new SubmittedFile();
        file.url = url;
        file.name = firstText(node, "originalName", "name", "fileName", "filename");
        if (StrUtil.isBlank(file.name)) {
            file.name = fileNameFromUrl(url);
        }
        file.type = normalizeType(firstText(node, "type", "fileType"), url, file.name);
        file.size = firstLong(node, "size", "fileSize");
        file.qualityLevel = firstInt(node, "qualityLevel", "quality");
        file.duration = firstInt(node, "duration");

        JsonNode qualityInfo = node.get("qualityInfo");
        if (qualityInfo != null && qualityInfo.isObject()) {
            if (file.size == null) {
                file.size = firstLong(qualityInfo, "fileSize", "size");
            }
            if (file.qualityLevel == null) {
                file.qualityLevel = firstInt(qualityInfo, "qualityLevel", "quality");
            }
            if (file.duration == null) {
                file.duration = firstInt(qualityInfo, "duration");
            }
        }
        return file;
    }

    private void parsePlainUrls(String fileUrls, List<SubmittedFile> files) {
        String[] parts = fileUrls.split("[,\\n]");
        for (String part : parts) {
            addTextUrl(part, files);
        }
    }

    private void addTextUrl(String url, List<SubmittedFile> files) {
        if (StrUtil.isBlank(url)) {
            return;
        }
        String cleanUrl = url.trim();
        SubmittedFile file = new SubmittedFile();
        file.url = cleanUrl;
        file.name = fileNameFromUrl(cleanUrl);
        file.type = normalizeType(null, cleanUrl, file.name);
        files.add(file);
    }

    private List<SubmittedFile> dedupeByUrl(List<SubmittedFile> files) {
        Map<String, SubmittedFile> unique = new LinkedHashMap<>();
        for (SubmittedFile file : files) {
            if (file != null && StrUtil.isNotBlank(file.url)) {
                unique.putIfAbsent(file.url, file);
            }
        }
        return new ArrayList<>(unique.values());
    }

    private List<SubmittedFile> filterByType(List<SubmittedFile> files, String type) {
        List<SubmittedFile> result = new ArrayList<>();
        for (SubmittedFile file : files) {
            if (file != null && type.equals(file.type)) {
                result.add(file);
            }
        }
        return result;
    }

    private SubmittedFile findMatchingLyric(SubmittedFile audioFile, List<SubmittedFile> files) {
        String audioBaseName = baseName(audioFile.name);
        SubmittedFile onlyLyric = null;
        int lyricCount = 0;
        for (SubmittedFile file : files) {
            if (!"lyric".equals(file.type)) {
                continue;
            }
            lyricCount++;
            onlyLyric = file;
            if (StrUtil.equalsIgnoreCase(audioBaseName, baseName(file.name))) {
                return file;
            }
        }
        return lyricCount == 1 ? onlyLyric : null;
    }

    private String normalizeType(String providedType, String url, String name) {
        String type = StrUtil.blankToDefault(providedType, "").trim().toLowerCase();
        if ("audio".equals(type) || "video".equals(type) || "lyric".equals(type) || "image".equals(type)) {
            return type;
        }
        String extension = CommonUtil.getFileExtension(StrUtil.blankToDefault(name, url));
        if (StrUtil.isBlank(extension)) {
            extension = CommonUtil.getFileExtension(url);
        }
        if (AUDIO_EXTENSIONS.contains(extension)) {
            return "audio";
        }
        if (IMAGE_EXTENSIONS.contains(extension)) {
            return "image";
        }
        if (LYRIC_EXTENSIONS.contains(extension)) {
            return "lyric";
        }
        return "other";
    }

    private String trackName(SubmittedFile audioFile, String albumTitle, int trackNo) {
        String name = baseName(audioFile.name);
        if (StrUtil.isBlank(name)) {
            name = StrUtil.blankToDefault(albumTitle, "Track") + " Track " + trackNo;
        }
        return name;
    }

    private String fileNameFromUrl(String url) {
        String cleanUrl = StrUtil.blankToDefault(url, "");
        int queryIndex = cleanUrl.indexOf('?');
        if (queryIndex >= 0) {
            cleanUrl = cleanUrl.substring(0, queryIndex);
        }
        cleanUrl = cleanUrl.replace("\\", "/");
        int slashIndex = cleanUrl.lastIndexOf('/');
        return slashIndex >= 0 ? cleanUrl.substring(slashIndex + 1) : cleanUrl;
    }

    private String baseName(String fileName) {
        String name = StrUtil.blankToDefault(fileName, fileNameFromUrl(fileName));
        int slashIndex = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slashIndex >= 0) {
            name = name.substring(slashIndex + 1);
        }
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex > 0) {
            name = name.substring(0, dotIndex);
        }
        return name.trim();
    }

    private String firstFileUrl(List<SubmittedFile> files) {
        return files.isEmpty() ? null : files.get(0).url;
    }

    private String resolveUserName(Long userId, String prefix) {
        if (userId == null) {
            return prefix + "unknown";
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            return prefix + userId;
        }
        return firstNotBlank(user.getNickname(), user.getUsername(), prefix + userId);
    }

    private boolean isAlbumLikeWork(Integer workType) {
        return Integer.valueOf(WORK_TYPE_ALBUM).equals(workType) || Integer.valueOf(WORK_TYPE_EP).equals(workType);
    }

    private String mapLanguage(Integer language) {
        if (language == null) {
            return null;
        }
        switch (language) {
            case 1:
                return "zh";
            case 2:
                return "en";
            case 3:
                return "ja";
            case 4:
                return "ko";
            default:
                return "other";
        }
    }

    private String firstNotBlank(String first, String second, String third) {
        if (StrUtil.isNotBlank(first)) {
            return first;
        }
        if (StrUtil.isNotBlank(second)) {
            return second;
        }
        return third;
    }

    private Integer defaultInt(Integer value, Integer defaultValue) {
        return value != null ? value : defaultValue;
    }

    private boolean hasText(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return child != null && child.isTextual() && StrUtil.isNotBlank(child.asText());
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode child = node.get(field);
            if (child != null && child.isTextual() && StrUtil.isNotBlank(child.asText())) {
                return child.asText();
            }
        }
        return null;
    }

    private Long firstLong(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode child = node.get(field);
            Long value = toLong(child);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Integer firstInt(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode child = node.get(field);
            Integer value = toInt(child);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Long toLong(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.longValue();
        }
        if (node.isTextual()) {
            try {
                return Long.parseLong(node.asText());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Integer toInt(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isInt() || node.isLong() || node.isNumber()) {
            return node.intValue();
        }
        if (node.isTextual()) {
            try {
                return Integer.parseInt(node.asText());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static final class SubmissionMetadata {
        private String source;
        private Long workId;
        private Long userId;
        private Integer workType;
        private String title;
        private String coverUrl;
        private String fileUrls;
        private String description;
        private String tags;
        private Integer language;
        private Integer allowDownload;
        private Integer allowComment;
        private Integer allowShare;
        private Integer isPaid;
        private BigDecimal price;
        private String artistName;
    }

    private static final class SubmittedFile {
        private String url;
        private String name;
        private String type;
        private Long size;
        private Integer qualityLevel;
        private Integer duration;
    }
}
