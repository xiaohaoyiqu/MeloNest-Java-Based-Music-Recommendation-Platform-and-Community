package com.haoran.music.service.impl;

import cn.hutool.core.util.StrUtil;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.service.CacheService;
import com.haoran.music.common.util.CommonUtil;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.OfficialMediaFileNameUtil;
import com.haoran.music.common.util.VideoCompressUtil;
import com.haoran.music.common.util.WorkProcessingUtil;
import com.haoran.music.entity.MV;
import com.haoran.music.entity.MediaAsset;
import com.haoran.music.entity.MediaDerivativeTask;
import com.haoran.music.entity.Song;
import com.haoran.music.mapper.MVMapper;
import com.haoran.music.mapper.MediaAssetMapper;
import com.haoran.music.mapper.MediaDerivativeTaskMapper;
import com.haoran.music.mapper.SongMapper;
import com.haoran.music.service.Node3MediaService;
import com.haoran.music.service.MediaAssetService;
import com.haoran.music.service.OfficialMediaDerivativeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;






@Slf4j
@Service
public class OfficialMediaDerivativeServiceImpl implements OfficialMediaDerivativeService {

    private static final int AUDIO_STANDARD_BITRATE = 128;
    private static final int AUDIO_HIGH_BITRATE = 320;
    private static final int MAX_RETRY_COUNT = 3;
    private static final int RETRY_BACKOFF_MINUTES = 5;
    private static final int PROCESSING_STALE_HOURS = 6;
    private static final Set<String> LOSSLESS_AUDIO_EXTENSIONS = new HashSet<>(Arrays.asList("flac", "wav"));

    private final SongMapper songMapper;
    private final MVMapper mvMapper;
    private final MediaDerivativeTaskMapper mediaDerivativeTaskMapper;
    private final MediaAssetMapper mediaAssetMapper;
    private final Node3MediaService node3MediaService;
    private final MediaAssetService mediaAssetService;
    private final CacheService cacheService;
    private final Executor mediaProcessingExecutor;

    @Value("${music.upload.song-path:/sdb1/haoranmusicData/Datas/songs/}")
    private String songBasePath;

    @Value("${music.upload.mv-path:/sdb1/haoranmusicData/Datas/mvs/}")
    private String mvBasePath;

    @Value("${music.nginx-url-prefix:http://192.168.153.131:3223/}")
    private String nginxUrlPrefix;

    @Value("${music.upload.multi-file.path}")
    private String multiFilePath;

    @Value("${music.upload.nginx.url}")
    private String multiFileNginxUrl;

    @Value("${music.upload.nginx-url-prefix:}")
    private String uploadNginxUrlPrefix;

    public OfficialMediaDerivativeServiceImpl(SongMapper songMapper,
                                              MVMapper mvMapper,
                                              MediaDerivativeTaskMapper mediaDerivativeTaskMapper,
                                              MediaAssetMapper mediaAssetMapper,
                                              Node3MediaService node3MediaService,
                                              MediaAssetService mediaAssetService,
                                              CacheService cacheService,
                                              @Qualifier(CommonConstants.MEDIA_PROCESSING_EXECUTOR)
                                              Executor mediaProcessingExecutor) {
        this.songMapper = songMapper;
        this.mvMapper = mvMapper;
        this.mediaDerivativeTaskMapper = mediaDerivativeTaskMapper;
        this.mediaAssetMapper = mediaAssetMapper;
        this.node3MediaService = node3MediaService;
        this.mediaAssetService = mediaAssetService;
        this.cacheService = cacheService;
        this.mediaProcessingExecutor = mediaProcessingExecutor;
    }

    @Override
    public void submitSongDerivativeJob(Long songId, String sourceUrl, Long sourceSize, Integer sourceQuality) {
        if (ObjectUtils.isEmpty(songId) || StrUtil.isBlank(sourceUrl)) {
            return;
        }
        MediaDerivativeTask task = registerTask(MediaDerivativeTask.MEDIA_TYPE_SONG, songId, sourceUrl,
                sourceSize, sourceQuality);
        if (ObjectUtils.isEmpty(task)) {
            return;
        }
        submitAfterCommit(() -> submitTask(task.getId()));
    }

    @Override
    public void submitMvDerivativeJob(Long mvId, String sourceUrl) {
        if (ObjectUtils.isEmpty(mvId) || StrUtil.isBlank(sourceUrl)) {
            return;
        }
        MediaDerivativeTask task = registerTask(MediaDerivativeTask.MEDIA_TYPE_MV, mvId, sourceUrl,
                null, null);
        if (ObjectUtils.isEmpty(task)) {
            return;
        }
        submitAfterCommit(() -> submitTask(task.getId()));
    }

    @Override
    public int retryDueDerivativeJobs() {
        List<MediaDerivativeTask> tasks = mediaDerivativeTaskMapper.selectDueTasks(20, staleProcessingBefore());
        if (ObjectUtils.isEmpty(tasks)) {
            return 0;
        }
        for (MediaDerivativeTask task : tasks) {
            if (ObjectUtils.isNotEmpty(task)) {
                submitTask(task.getId());
            }
        }
        return tasks.size();
    }











    private MediaDerivativeTask registerTask(String mediaType, Long mediaId, String sourceUrl,
                                             Long sourceSize, Integer sourceQuality) {
        MediaDerivativeTask existing = mediaDerivativeTaskMapper.selectLatest(mediaType, mediaId, sourceUrl);
        if (ObjectUtils.isNotEmpty(existing)) {
            if (MediaDerivativeTask.STATUS_COMPLETED.equals(existing.getStatus())) {
                return null;
            }
            if (MediaDerivativeTask.STATUS_FAILED.equals(existing.getStatus())
                    && retryCount(existing) >= maxRetryCount(existing)) {
                existing.setStatus(MediaDerivativeTask.STATUS_PENDING);
                existing.setRetryCount(0);
                existing.setLastError(null);
                existing.setNextRetryTime(null);
                existing.setStartedAt(null);
                existing.setFinishedAt(null);
                existing.setUpdatedAt(LocalDateTime.now());
                mediaDerivativeTaskMapper.updateById(existing);
            }
            return existing;
        }

        MediaDerivativeTask task = new MediaDerivativeTask();
        task.setMediaType(mediaType);
        task.setMediaId(mediaId);
        task.setSourceUrl(sourceUrl);
        task.setSourceSize(sourceSize);
        task.setSourceQuality(sourceQuality);
        task.setStatus(MediaDerivativeTask.STATUS_PENDING);
        task.setRetryCount(0);
        task.setMaxRetryCount(MAX_RETRY_COUNT);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        mediaDerivativeTaskMapper.insert(task);
        return task;
    }






    private void submitTask(Long taskId) {
        if (ObjectUtils.isEmpty(taskId)) {
            return;
        }
        try {
            mediaProcessingExecutor.execute(() -> processTask(taskId));
        } catch (RejectedExecutionException e) {
            markTaskFailed(taskId, "MEDIA_PROCESSING_EXECUTOR_REJECTED");
            log.warn("event=official_media_derivative_submission_rejected taskId={} errorType={}",
                    taskId, e.getClass().getSimpleName());
        }
    }






    private void processTask(Long taskId) {
        if (mediaDerivativeTaskMapper.claimForProcessing(taskId, staleProcessingBefore()) != 1) {
            return;
        }
        MediaDerivativeTask task = mediaDerivativeTaskMapper.selectById(taskId);
        if (ObjectUtils.isEmpty(task)) {
            return;
        }
        try {
            if (MediaDerivativeTask.MEDIA_TYPE_SONG.equals(task.getMediaType())) {
                generateSongDerivatives(task.getMediaId(), task.getSourceUrl(),
                        task.getSourceSize(), task.getSourceQuality());
            } else if (MediaDerivativeTask.MEDIA_TYPE_MV.equals(task.getMediaType())) {
                generateMvDerivatives(task.getMediaId(), task.getSourceUrl());
            } else {
                throw new IllegalStateException("不支持的媒体任务类型: " + task.getMediaType());
            }
            markTaskCompleted(taskId);
        } catch (Exception e) {
            String errorCategory = errorCategory(e);
            markTaskFailed(taskId, errorCategory);
            log.error("event=official_media_derivative_failed taskId={} mediaType={} mediaId={} errorType={}",
                    taskId, task.getMediaType(), task.getMediaId(), errorCategory);
        }
    }






    private void markTaskCompleted(Long taskId) {
        MediaDerivativeTask task = mediaDerivativeTaskMapper.selectById(taskId);
        if (ObjectUtils.isEmpty(task)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        task.setStatus(MediaDerivativeTask.STATUS_COMPLETED);
        task.setLastError(null);
        task.setNextRetryTime(null);
        task.setFinishedAt(now);
        task.setUpdatedAt(now);
        mediaDerivativeTaskMapper.updateById(task);
    }







    private void markTaskFailed(Long taskId, String error) {
        MediaDerivativeTask task = mediaDerivativeTaskMapper.selectById(taskId);
        if (ObjectUtils.isEmpty(task)) {
            return;
        }
        int nextRetryCount = retryCount(task) + 1;
        int maxRetryCount = maxRetryCount(task);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRetryTime = nextRetryCount < maxRetryCount
                ? now.plusMinutes((long) nextRetryCount * RETRY_BACKOFF_MINUTES)
                : null;
        task.setStatus(MediaDerivativeTask.STATUS_FAILED);
        task.setRetryCount(nextRetryCount);
        task.setMaxRetryCount(maxRetryCount);
        task.setLastError(truncate(error, 2000));
        task.setNextRetryTime(nextRetryTime);
        task.setFinishedAt(ObjectUtils.isEmpty(nextRetryTime) ? now : null);
        task.setUpdatedAt(now);
        mediaDerivativeTaskMapper.updateById(task);
    }

    private int retryCount(MediaDerivativeTask task) {
        return ObjectUtils.isEmpty(task.getRetryCount()) ? 0 : task.getRetryCount();
    }

    private int maxRetryCount(MediaDerivativeTask task) {
        return ObjectUtils.isEmpty(task.getMaxRetryCount()) ? MAX_RETRY_COUNT : task.getMaxRetryCount();
    }

    private LocalDateTime staleProcessingBefore() {
        return LocalDateTime.now().minusHours(PROCESSING_STALE_HOURS);
    }

    private String errorCategory(Exception e) {
        if (ObjectUtils.isEmpty(e)) {
            return "UNKNOWN";
        }
        return "MEDIA_DERIVATIVE_FAILED_" + e.getClass().getSimpleName();
    }

    private String truncate(String value, int maxLength) {
        if (StrUtil.isBlank(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }




    private void generateSongDerivatives(Long songId, String sourceUrl, Long sourceSize, Integer sourceQuality) {
        Song song = songMapper.selectById(songId);
        if (ObjectUtils.isEmpty(song)) {
            throw new IllegalStateException("歌曲不存在，无法生成正式媒体文件");
        }
        File sourceFile = resolveSourceFile(sourceUrl);
        if (ObjectUtils.isEmpty(sourceFile)) {
            log.warn("event=official_media_source_unresolved mediaType=song mediaId={}", songId);
            throw new IllegalStateException("无法解析歌曲源文件");
        }

        String extension = extensionOr(sourceFile.getName(), "mp3");
        String originalName = officialSongFileName(song, extension);
        String originalDir = remotePath(songBasePath, "original");
        String originalPath = remotePath(originalDir, originalName);
        String originalUrl = buildPublicUrl("songs/original/" + originalName);
        reserveSongTarget(songId, "original", originalName, originalUrl, originalPath);
        if (!node3MediaService.uploadFile(sourceFile, originalDir, originalName)) {
            log.warn("event=official_media_source_upload_failed mediaType=song mediaId={}", songId);
            throw new IllegalStateException("歌曲源文件上传 node3 失败");
        }
        registerSongAsset(songId, "original", originalUrl, originalPath,
                positiveSize(node3MediaService.fileSize(originalPath), sourceSize));

        Song update = new Song();
        update.setId(songId);
        boolean changed = false;

        String standardName = officialSongFileName(song, "mp3");
        String standardPath = remotePath(songBasePath, "standard", standardName);
        String standardUrl = buildPublicUrl("songs/standard/" + standardName);
        reserveSongTarget(songId, "standard", standardName, standardUrl, standardPath);
        if (node3MediaService.generateAudioVariant(originalPath, standardPath, AUDIO_STANDARD_BITRATE)) {
            update.setUrlStandard(standardUrl);
            setSongSize(update, "standard", node3MediaService.fileSize(standardPath));
            registerSongAsset(songId, "standard", standardUrl, standardPath,
                    node3MediaService.fileSize(standardPath));
            changed = true;
        }

        String highName = officialSongFileName(song, "mp3");
        String highPath = remotePath(songBasePath, "high", highName);
        String highUrl = buildPublicUrl("songs/high/" + highName);
        reserveSongTarget(songId, "high", highName, highUrl, highPath);
        if (node3MediaService.generateAudioVariant(originalPath, highPath, AUDIO_HIGH_BITRATE)) {
            update.setUrlHigh(highUrl);
            setSongSize(update, "high", node3MediaService.fileSize(highPath));
            registerSongAsset(songId, "high", highUrl, highPath,
                    node3MediaService.fileSize(highPath));
            changed = true;
        }

        String losslessPath = null;
        if (isLosslessSource(extension, sourceQuality)) {
            String losslessName = officialSongFileName(song, extension);
            losslessPath = remotePath(songBasePath, "lossless", losslessName);
            String losslessUrl = buildPublicUrl("songs/lossless/" + losslessName);
            reserveSongTarget(songId, "lossless", losslessName, losslessUrl, losslessPath);
            if (node3MediaService.copyRemoteFile(originalPath, losslessPath)) {
                update.setUrlLossless(losslessUrl);
                long size = node3MediaService.fileSize(losslessPath);
                update.setSizeLossless(size > 0 ? size : sourceSize);
                registerSongAsset(songId, "lossless", losslessUrl, losslessPath, size);
                changed = true;
            }
        }

        if (changed) {
            update.setUpdateTime(LocalDateTime.now());
            songMapper.updateById(update);
            cacheService.clearSongCache(songId);
            log.info("event=official_song_derivatives_ready songId={}", songId);
            return;
        }
        throw new IllegalStateException("歌曲派生文件生成失败");
    }




    private void generateMvDerivatives(Long mvId, String sourceUrl) {
        File sourceFile = resolveSourceFile(sourceUrl);
        if (ObjectUtils.isEmpty(sourceFile)) {
            log.warn("event=official_media_source_unresolved resourceType=mv resourceId={}", mvId);
            throw new IllegalStateException("无法解析 MV 源文件");
        }

        String extension = extensionOr(sourceFile.getName(), "mp4");
        String originalName = mvId + "_original." + extension;
        String originalDir = remotePath(mvBasePath, "original");
        String originalPath = remotePath(originalDir, originalName);
        if (!node3MediaService.uploadFile(sourceFile, originalDir, originalName)) {
            log.warn("[OfficialMedia] upload source video failed: mvId={}, file={}", mvId, sourceFile.getName());
            throw new IllegalStateException("MV 源文件上传 node3 失败");
        }

        VideoCompressUtil.VideoInfo info = node3MediaService.probeVideo(originalPath);
        int sourceHeight = ObjectUtils.isNotEmpty(info) ? info.getHeight() : 0;
        MV update = new MV();
        update.setId(mvId);
        boolean changed = false;

        changed |= generateMvVariant(update, originalPath, mvId, sourceHeight, 360, 28);
        changed |= generateMvVariant(update, originalPath, mvId, sourceHeight, 720, 24);
        changed |= generateMvVariant(update, originalPath, mvId, sourceHeight, 1080, 22);
        changed |= generateMvVariant(update, originalPath, mvId, sourceHeight, 2160, 20);

        if (changed) {
            update.setUpdateTime(LocalDateTime.now());
            mvMapper.updateById(update);
            registerMvAsset(mvId, "original", buildPublicUrl("mvs/original/" + originalName),
                    originalPath, node3MediaService.fileSize(originalPath));
            cacheService.clearMVCache(mvId);
            log.info("[OfficialMedia] MV derivatives ready: mvId={}, sourceHeight={}", mvId, sourceHeight);
            return;
        }
        throw new IllegalStateException("MV 派生文件生成失败");
    }




    private boolean generateMvVariant(MV update, String originalPath, Long mvId, int sourceHeight,
                                      int targetHeight, int crf) {
        if (targetHeight > 360 && (sourceHeight <= 0 || sourceHeight < targetHeight)) {
            return false;
        }
        int ffmpegHeight = sourceHeight > 0 && sourceHeight < targetHeight ? 0 : targetHeight;
        String name = mvId + "_" + targetHeight + "p.mp4";
        String outputPath = remotePath(mvBasePath, targetHeight + "p", name);
        boolean generated = node3MediaService.generateVideoVariant(originalPath, outputPath, ffmpegHeight, crf,
                0, Integer.MAX_VALUE);
        if (!generated) {
            return false;
        }

        String url = buildPublicUrl("mvs/" + targetHeight + "p/" + name);
        long size = node3MediaService.fileSize(outputPath);
        if (targetHeight == 360) {
            update.setUrl360p(url);
            update.setSize360p(size);
        } else if (targetHeight == 720) {
            update.setUrl720p(url);
            update.setSize720p(size);
        } else if (targetHeight == 1080) {
            update.setUrl1080p(url);
            update.setSize1080p(size);
        } else if (targetHeight == 2160) {
            update.setUrl2160p(url);
            update.setSize2160p(size);
        }
        registerMvAsset(mvId, targetHeight + "p", url, outputPath, size);
        return true;
    }




    private void submitAfterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
            return;
        }
        task.run();
    }




    private File resolveSourceFile(String sourceUrl) {
        String localPath = resolveUploadedSubmissionPath(sourceUrl);
        if (StrUtil.isBlank(localPath)) {
            localPath = WorkProcessingUtil.extractLocalPath(sourceUrl);
        }
        if (StrUtil.isBlank(localPath)) {
            localPath = CommonUtil.extractLocalPath(sourceUrl);
        }
        if (StrUtil.isBlank(localPath) || !WorkProcessingUtil.isPathSafe(localPath)) {
            return null;
        }
        File file = new File(localPath);
        if (!file.exists() || !file.isFile()) {
            return null;
        }
        return file;
    }






    private String resolveUploadedSubmissionPath(String sourceUrl) {
        if (StrUtil.isBlank(sourceUrl) || StrUtil.isBlank(multiFilePath)) {
            return null;
        }
        String[] prefixes = {multiFileNginxUrl, uploadNginxUrlPrefix};
        for (String prefix : prefixes) {
            if (StrUtil.isBlank(prefix)) {
                continue;
            }
            String cleanPrefix = trimTrailingSlash(prefix);
            if (!sourceUrl.startsWith(cleanPrefix + "/")) {
                continue;
            }
            String relative = sourceUrl.substring(cleanPrefix.length() + 1);
            int queryIndex = relative.indexOf('?');
            if (queryIndex >= 0) {
                relative = relative.substring(0, queryIndex);
            }
            try {
                relative = URLDecoder.decode(relative, StandardCharsets.UTF_8.name()).replace('\\', '/');
                Path basePath = Paths.get(multiFilePath).toAbsolutePath().normalize();
                Path path = basePath.resolve(relative).normalize();
                if (path.startsWith(basePath)) {
                    return path.toString();
                }
            } catch (Exception e) {
                log.warn("event=official_media_submission_source_unresolved");
            }
        }
        return null;
    }

    private String trimTrailingSlash(String value) {
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }




    private String remotePath(String basePath, String... parts) {
        String path = normalizeDir(basePath);
        for (String part : parts) {
            if (StrUtil.isBlank(part)) {
                continue;
            }
            String cleanPart = part.replace("\\", "/");
            while (cleanPart.startsWith("/")) {
                cleanPart = cleanPart.substring(1);
            }
            while (cleanPart.endsWith("/")) {
                cleanPart = cleanPart.substring(0, cleanPart.length() - 1);
            }
            path = path + cleanPart + "/";
        }
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }




    private String buildPublicUrl(String relativePath) {
        String prefix = normalizeDir(nginxUrlPrefix);
        String cleanPath = relativePath.replace("\\", "/");
        while (cleanPath.startsWith("/")) {
            cleanPath = cleanPath.substring(1);
        }
        return prefix + cleanPath;
    }




    private String normalizeDir(String path) {
        String normalized = StrUtil.blankToDefault(path, "").replace("\\", "/");
        while (normalized.endsWith("//")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }




    private String extensionOr(String fileName, String fallback) {
        String extension = CommonUtil.getFileExtension(fileName);
        return StrUtil.isNotBlank(extension) ? extension : fallback;
    }








    private String officialSongFileName(Song song, String extension) {
        return OfficialMediaFileNameUtil.build(song.getName(), song.getArtistNames(),
                song.getVersionName(), extension);
    }











    private void reserveSongTarget(Long songId, String role, String fileName,
                                   String publicUrl, String storagePath) {
        MediaAsset existing = mediaAssetMapper.selectByStorage(
                MediaAsset.STORAGE_NODE_MEDIA, storagePath);
        if (ObjectUtils.isNotEmpty(existing)) {
            requireOwnedSongTarget(existing, songId, role);
            return;
        }
        if (node3MediaService.exists(storagePath)) {
            throw new IllegalStateException("正式音频目标已存在但未登记，拒绝覆盖");
        }

        LocalDateTime now = LocalDateTime.now();
        MediaAsset reservation = new MediaAsset();
        reservation.setVisibility(MediaAsset.VISIBILITY_PUBLIC);
        reservation.setOriginalName(fileName);
        reservation.setContentType(audioContentType(fileName));
        reservation.setMediaType("audio");
        reservation.setSourceType("official_derivative");
        reservation.setSourceId(songId);
        reservation.setAssetRole(role);
        reservation.setPublicUrl(publicUrl);
        reservation.setStorageNode(MediaAsset.STORAGE_NODE_MEDIA);
        reservation.setStoragePath(storagePath);
        reservation.setScanStatus(MediaAsset.SCAN_STATUS_CLEAN);
        reservation.setStatus(MediaAsset.STATUS_ACTIVE);
        reservation.setGraceUntil(now.plusHours(24));
        reservation.setCreateTime(now);
        reservation.setUpdateTime(now);
        try {
            if (mediaAssetMapper.insert(reservation) != 1) {
                throw new IllegalStateException("正式音频目标预留失败");
            }
        } catch (DuplicateKeyException exception) {
            existing = mediaAssetMapper.selectByStorage(MediaAsset.STORAGE_NODE_MEDIA, storagePath);
            if (ObjectUtils.isEmpty(existing)) {
                throw exception;
            }
            requireOwnedSongTarget(existing, songId, role);
        }
    }

    private void requireOwnedSongTarget(MediaAsset asset, Long songId, String role) {
        if (!MediaAsset.STATUS_ACTIVE.equals(asset.getStatus())) {
            throw new IllegalStateException("正式音频目标正在回收或不可用");
        }
        if (!"official_derivative".equals(asset.getSourceType())
                || !songId.equals(asset.getSourceId())
                || !role.equals(asset.getAssetRole())) {
            throw new IllegalStateException("正式音频文件名与其他媒体资产冲突");
        }
    }

    private String audioContentType(String fileName) {
        String extension = extensionOr(fileName, "").toLowerCase();
        if ("flac".equals(extension)) {
            return "audio/flac";
        }
        if ("ogg".equals(extension)) {
            return "audio/ogg";
        }
        return "audio/mpeg";
    }




    private boolean isLosslessSource(String extension, Integer sourceQuality) {
        return LOSSLESS_AUDIO_EXTENSIONS.contains(extension)
                || (ObjectUtils.isNotEmpty(sourceQuality) && sourceQuality >= 3
                && LOSSLESS_AUDIO_EXTENSIONS.contains(extension));
    }




    private void setSongSize(Song update, String quality, long size) {
        if (size <= 0) {
            return;
        }
        if (CommonConstants.QUALITY_STANDARD.equals(quality)) {
            update.setSizeStandard(size);
        } else if (CommonConstants.QUALITY_HIGH.equals(quality)) {
            update.setSizeHigh(size);
        }
    }

    private void registerSongAsset(Long songId, String role, String publicUrl,
                                   String storagePath, long fileSize) {
        mediaAssetService.registerAndRetain(
                "audio", "official_derivative", songId, role, publicUrl,
                MediaAsset.STORAGE_NODE_MEDIA, storagePath,
                fileSize > 0 ? fileSize : null, "song", songId);
    }

    private void registerMvAsset(Long mvId, String role, String publicUrl,
                                 String storagePath, long fileSize) {
        mediaAssetService.registerAndRetain(
                "video", "official_derivative", mvId, role, publicUrl,
                MediaAsset.STORAGE_NODE_MEDIA, storagePath,
                fileSize > 0 ? fileSize : null, "mv", mvId);
    }

    private long positiveSize(long actualSize, Long fallbackSize) {
        return actualSize > 0 ? actualSize : (fallbackSize != null ? fallbackSize : 0L);
    }
}
