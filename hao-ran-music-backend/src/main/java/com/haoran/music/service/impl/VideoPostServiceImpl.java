package com.haoran.music.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haoran.music.common.config.MusicUploadConfig;
import com.haoran.music.common.config.PostMediaConfig;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.CommonUtil;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.SecurityCheckUtil;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.common.util.VideoCompressUtil;
import com.haoran.music.entity.MediaAsset;
import com.haoran.music.entity.MusicPost;
import com.haoran.music.mapper.MusicPostMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.MediaAssetService;
import com.haoran.music.service.MusicPostService;
import com.haoran.music.service.Node3MediaService;
import com.haoran.music.service.VideoPostService;
import com.haoran.music.service.VirusScanService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import javax.annotation.Resource;

   
            
  
                      
   
@Slf4j
@Service
public class VideoPostServiceImpl implements VideoPostService {

    private static final String STATUS_PROCESSING = "processing";
    private static final String STATUS_READY = "ready";
    private static final String STATUS_FAILED = "failed";
    private static final int MAX_RETRY_COUNT = 3;
    private static final String TARGET_TYPE_VIDEO_POST = "music_post_video";

    private final MusicPostMapper musicPostMapper;
    private final ObjectMapper objectMapper;
    private final PostMediaConfig postMediaConfig;
    private final MusicUploadConfig musicUploadConfig;
    private final VirusScanService virusScanService;
    private final Node3MediaService node3MediaService;
    private final MediaAssetService mediaAssetService;
    private final UserMapper userMapper;
    private final Executor mediaProcessingExecutor;

    @Resource
    private MusicPostService musicPostService;

    public VideoPostServiceImpl(MusicPostMapper musicPostMapper,
                                PostMediaConfig postMediaConfig,
                                MusicUploadConfig musicUploadConfig,
                                VirusScanService virusScanService,
                                Node3MediaService node3MediaService,
                                MediaAssetService mediaAssetService,
                                UserMapper userMapper,
                                @Qualifier(CommonConstants.MEDIA_PROCESSING_EXECUTOR) Executor mediaProcessingExecutor) {
        this.musicPostMapper = musicPostMapper;
        this.postMediaConfig = postMediaConfig;
        this.musicUploadConfig = musicUploadConfig;
        this.virusScanService = virusScanService;
        this.node3MediaService = node3MediaService;
        this.mediaAssetService = mediaAssetService;
        this.userMapper = userMapper;
        this.mediaProcessingExecutor = mediaProcessingExecutor;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Map<String, Object> uploadVideoPost(MultipartFile file, Long userId, String content, String topics, Boolean allowComment) {
        UserAccountStatusUtil.requireCanInteract(userId, userMapper::selectById, "上传视频动态");
        Map<String, Object> result = new HashMap<>();
        File localOriginalFile = null;
        String remoteOriginalPath = null;
        boolean keepRemoteOriginal = false;

        try {
            validateVideoFile(file);

            String baseName = userId + "_" + System.currentTimeMillis();
            String originalExtension = getSafeVideoExtension(file);
            String originalFilename = baseName + "_original" + originalExtension;
            String filename480p = baseName + "_480p.mp4";
            String filename720p = baseName + "_720p.mp4";
            String thumbnailFilename = baseName + "_thumb.jpg";

            Path localDir = Paths.get(getRequiredPath(postMediaConfig.getVideoTempPath(), "video temp path"));
            Files.createDirectories(localDir);
            Path localOriginalPath = localDir.resolve(originalFilename);
            localOriginalFile = localOriginalPath.toFile();
            file.transferTo(localOriginalFile);

            scanVideoFile(localOriginalFile);

            if (!node3MediaService.uploadFile(localOriginalFile, getRequiredPath(postMediaConfig.getVideoOriginalPath(), "video original path"))) {
                throw new IllegalStateException("video upload to node3 failed");
            }

            remoteOriginalPath = appendPath(postMediaConfig.getVideoOriginalPath(), originalFilename);
            VideoCompressUtil.VideoInfo sourceInfo = node3MediaService.probeVideo(remoteOriginalPath);
            validateVideoInfo(sourceInfo, remoteOriginalPath);

            String originalUrl = buildPublicUrl("posts/videos/original/" + originalFilename);
            String url480p = buildPublicUrl("posts/videos/480p/" + filename480p);
            String url720p = buildPublicUrl("posts/videos/720p/" + filename720p);
            String thumbnailUrl = buildPublicUrl("posts/videos/thumbnails/" + thumbnailFilename);

            Map<String, Object> videoInfo = buildInitialVideoInfo(baseName, originalExtension, originalUrl,
                    url480p, url720p, thumbnailUrl, file.getSize(), sourceInfo);

            Map<String, Object> postData = new HashMap<>();
            postData.put("content", content);
            postData.put("topics", topics);
            postData.put("videoInfo", videoInfo);
            postData.put("allowComment", allowComment);
            Long postId = createVideoPost(userId, postData);
            if (ObjectUtils.isEmpty(postId)) {
                throw new IllegalStateException("视频动态记录创建失败");
            }

            keepRemoteOriginal = true;
            registerMediaAssets(postId, videoInfo, remoteOriginalPath);
            submitDerivativeJob(postId, remoteOriginalPath,
                    appendPath(getVideo480pPath(), filename480p),
                    appendPath(getVideo720pPath(), filename720p),
                    appendPath(postMediaConfig.getVideoThumbnailPath(), thumbnailFilename));

            result.put("success", true);
            result.put("videoInfo", videoInfo);
            result.put("baseName", baseName);
            result.put("postId", postId);
            result.put("status", STATUS_PROCESSING);

            log.info("event=video_post_upload_succeeded userId={} postId={} sizeBytes={}",
                    userId, postId, file.getSize());
            return result;
        } catch (Exception e) {
            if (ObjectUtils.isNotEmpty(remoteOriginalPath) && !keepRemoteOriginal) {
                node3MediaService.deleteQuietly(remoteOriginalPath);
            }
            log.error("event=video_post_upload_failed userId={} errorType={}",
                    userId, e.getClass().getSimpleName());
            result.put("success", false);
            result.put("error", "视频上传失败，请稍后重试");
            return result;
        } finally {
            deleteLocalQuietly(localOriginalFile);
        }
    }

       
                                                          
      
                                      
                                       
                                  
                                                                              
       
    public String transcodeVideoOnDemand(String baseName, String quality, int crf) {
        String normalizedQuality = normalizeQuality(quality);
        String variantFilename = baseName + "_" + normalizedQuality + ".mp4";
        String variantPath = "480p".equals(normalizedQuality)
                ? appendPath(getVideo480pPath(), variantFilename)
                : appendPath(getVideo720pPath(), variantFilename);
        if (node3MediaService.exists(variantPath)) {
            return buildPublicUrl("posts/videos/" + normalizedQuality + "/" + variantFilename);
        }
        return buildPublicUrl("posts/videos/original/" + baseName + "_original.mp4");
    }

       
                                                                         
      
                                 
       
    public int cleanupExpiredCache() {
        File cacheDir = new File(postMediaConfig.getVideoCachePath());
        if (!cacheDir.exists()) {
            return 0;
        }

        int count = 0;
        long cutoffTime = System.currentTimeMillis() - (postMediaConfig.getVideoCacheDays() * 24L * 60 * 60 * 1000);
        File[] files = cacheDir.listFiles();
        if (ObjectUtils.isNotEmpty(files)) {
            for (File file : files) {
                if (file.lastModified() < cutoffTime && file.delete()) {
                    count++;
                    log.info("event=video_post_cache_cleanup_completed");
                }
            }
        }
        return count;
    }

    private int toAllowCommentValue(Boolean allowComment) {
        return Boolean.FALSE.equals(allowComment) ? 0 : 1;
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createVideoPost(Long userId, Map<String, Object> postData) {
        try {
            MusicPost post = new MusicPost();
            post.setUserId(userId);
            String content = (String) postData.get("content");
            if (ObjectUtils.isNotEmpty(content)) {
                SecurityCheckUtil.CheckResult contentCheck = SecurityCheckUtil.checkDescription(content);
                if (!contentCheck.isSafe()) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, contentCheck.getMessage());
                }
                post.setContent(contentCheck.getCleanedValue());
            } else {
                post.setContent(content);
            }
            post.setPostType("video");
            post.setVisibility("public");
            post.setIsListenDiary(false);
            post.setLikeCount(0);
            post.setCommentCount(0);
            post.setTopics((String) postData.get("topics"));
            Object videoInfo = postData.get("videoInfo");
            post.setVideoInfo(ObjectUtils.isEmpty(videoInfo) ? null : objectMapper.writeValueAsString(videoInfo));
            post.setAllowComment(toAllowCommentValue((Boolean) postData.get("allowComment")));
            post.setOfficialCommentClosed(false);
            post.setShareCount(0);
            post.setIsDeleted(false);
            post.setCreateTime(LocalDateTime.now());
            post.setUpdateTime(LocalDateTime.now());


            int insertCount = musicPostMapper.insert(post);
            if (insertCount > 0) {
                return post.getId();
            }
            return null;
        } catch (Exception e) {
            log.error("event=video_post_create_failed userId={} errorType={}",
                    userId, e.getClass().getSimpleName());
            return null;
        }
    }

    @Override
    public Map<String, Object> getVideoPostStatus(Long postId, Long viewerId) {
        Map<String, Object> status = new HashMap<>();
        status.put("postId", postId);

        musicPostService.requirePostOwnerOrModerator(postId, viewerId);

        MusicPost post = musicPostMapper.selectById(postId);
        if (ObjectUtils.isEmpty(post) || !"video".equals(post.getPostType())) {
            status.put("status", "not_found");
            status.put("message", "视频不存在");
            return status;
        }

        Map<String, Object> videoInfo = readVideoInfo(post);
        String currentStatus = ObjectUtils.castString(videoInfo.get("status"));
        if (STATUS_PROCESSING.equals(currentStatus) && isDerivativeReady(videoInfo)) {
            updateVideoInfo(postId, info -> {
                info.put("status", STATUS_READY);
                info.put("progress", 100);
                info.put("message", "视频已就绪");
            });
            currentStatus = STATUS_READY;
            videoInfo.put("progress", 100);
        }

        status.put("status", ObjectUtils.defaultIfNull(currentStatus, STATUS_PROCESSING));
        status.put("message", ObjectUtils.defaultIfNull(ObjectUtils.castString(videoInfo.get("message")), "视频处理中"));
        status.put("progress", ObjectUtils.defaultIfNull(videoInfo.get("progress"), 0));
        status.put("videoInfo", sanitizeVideoInfo(videoInfo));
        return status;
    }

    @Override
    public Map<String, Object> retryVideoPostProcessing(Long postId, Long userId) {
        UserAccountStatusUtil.requireCanInteract(userId, userMapper::selectById, "重试视频处理");
        Map<String, Object> result = new HashMap<>();
        MusicPost post = musicPostMapper.selectById(postId);
        if (ObjectUtils.isEmpty(post) || !"video".equals(post.getPostType())) {
            result.put("success", false);
            result.put("error", "视频不存在");
            return result;
        }
        if (ObjectUtils.isEmpty(userId) || !userId.equals(post.getUserId())) {
            result.put("success", false);
            result.put("error", "无权重试该视频");
            return result;
        }

        Map<String, Object> videoInfo = readVideoInfo(post);
        if (!STATUS_FAILED.equals(ObjectUtils.castString(videoInfo.get("status")))) {
            result.put("success", false);
            result.put("error", "只有失败的视频处理任务可以重试");
            return result;
        }

        int retryCount = toInt(videoInfo.get("retryCount"));
        if (retryCount >= MAX_RETRY_COUNT) {
            result.put("success", false);
            result.put("error", "视频处理重试次数已达上限");
            return result;
        }

        String baseName = ObjectUtils.castString(videoInfo.get("baseName"));
        String extension = ObjectUtils.castString(videoInfo.get("extension"));
        if (ObjectUtils.isEmpty(baseName)) {
            result.put("success", false);
            result.put("error", "视频原件信息不完整，无法重试");
            return result;
        }
        if (ObjectUtils.isEmpty(extension)) {
            extension = ".mp4";
        } else if (!extension.startsWith(".")) {
            extension = "." + extension;
        }

        String remoteOriginalPath = appendPath(postMediaConfig.getVideoOriginalPath(),
                baseName + "_original" + extension);
        if (!node3MediaService.exists(remoteOriginalPath)) {
            result.put("success", false);
            result.put("error", "视频原件不存在，无法重试");
            return result;
        }

        String remote480pPath = appendPath(getVideo480pPath(), baseName + "_480p.mp4");
        String remote720pPath = appendPath(getVideo720pPath(), baseName + "_720p.mp4");
        String remoteThumbnailPath = appendPath(postMediaConfig.getVideoThumbnailPath(), baseName + "_thumb.jpg");
        int nextRetryCount = retryCount + 1;
        updateVideoInfo(postId, info -> {
            info.put("status", STATUS_PROCESSING);
            info.put("progress", 10);
            info.put("message", "已重新提交视频处理任务");
            info.put("retryCount", nextRetryCount);
            info.remove("error");
        });
        submitDerivativeJob(postId, remoteOriginalPath, remote480pPath, remote720pPath, remoteThumbnailPath);

        result.put("success", true);
        result.put("status", STATUS_PROCESSING);
        result.put("retryCount", nextRetryCount);
        return result;
    }

    @Override
    public String getVideoPlayUrl(Long postId, String quality, Long viewerId) {
        musicPostService.requirePostReadable(postId, viewerId);
        MusicPost post = musicPostMapper.selectById(postId);
        if (ObjectUtils.isEmpty(post) || !"video".equals(post.getPostType())) {
            return null;
        }

        Map<String, Object> videoInfo = readVideoInfo(post);
        if (!STATUS_READY.equals(ObjectUtils.castString(videoInfo.get("status")))) {
            return null;
        }
        String normalizedQuality = normalizeQuality(quality);
        if ("original".equals(normalizedQuality)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "视频原件不对外提供");
        }

        String variantUrl = ObjectUtils.castString(videoInfo.get(normalizedQuality));
        String variantPath = qualityRemotePath(videoInfo, normalizedQuality);
        if (ObjectUtils.isNotEmpty(variantUrl) && ObjectUtils.isNotEmpty(variantPath) && node3MediaService.exists(variantPath)) {
            return controlledVideoUrl(postId, normalizedQuality);
        }
        return null;
    }

    @Override
    public String getVideoThumbnailUrl(Long postId, Long viewerId) {
        musicPostService.requirePostReadable(postId, viewerId);
        MusicPost post = musicPostMapper.selectById(postId);
        if (ObjectUtils.isEmpty(post) || !"video".equals(post.getPostType())) {
            return null;
        }
        Map<String, Object> videoInfo = readVideoInfo(post);
        if (!STATUS_READY.equals(ObjectUtils.castString(videoInfo.get("status")))) {
            return null;
        }
        String thumbnail = ObjectUtils.castString(videoInfo.get("thumbnail"));
        return ObjectUtils.isEmpty(thumbnail) ? null : controlledVideoUrl(postId, "thumbnail");
    }

    @Override
    public void deliverVideo(Long postId, String quality, Long viewerId,
                             javax.servlet.http.HttpServletResponse response) {
        musicPostService.requirePostReadable(postId, viewerId);
        MusicPost post = musicPostMapper.selectById(postId);
        if (ObjectUtils.isEmpty(post) || !"video".equals(post.getPostType())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "视频动态不存在");
        }
        Map<String, Object> videoInfo = readVideoInfo(post);
        if (!STATUS_READY.equals(ObjectUtils.castString(videoInfo.get("status")))) {
            throw new BusinessException(ResultCode.NOT_FOUND, "视频尚未就绪");
        }
        String normalizedQuality = normalizeQuality(quality);
        if ("original".equals(normalizedQuality)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "视频原件不对外提供");
        }
        String sourceUrl = "thumbnail".equals(normalizedQuality)
                ? ObjectUtils.castString(videoInfo.get("thumbnail"))
                : ObjectUtils.castString(videoInfo.get(normalizedQuality));
        String sourcePath = validatedPostVideoUriPath(sourceUrl, normalizedQuality);
        response.setHeader("Cache-Control", "private, no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Accept-Ranges", "bytes");
        response.setHeader("X-Accel-Redirect", "/__post_media" + sourcePath);
    }

    private String controlledVideoUrl(Long postId, String quality) {
        return "/api/music-square/posts/videos/" + postId + "/content?quality=" + quality;
    }

    private String validatedPostVideoUriPath(String sourceUrl, String quality) {
        if (ObjectUtils.isEmpty(sourceUrl)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "视频派生文件不存在");
        }
        try {
            String path = java.net.URI.create(sourceUrl).getPath();
            String expectedDirectory = "thumbnail".equals(quality) ? "/posts/videos/thumbnails/"
                    : "/posts/videos/" + quality + "/";
            if (path == null || !path.startsWith(expectedDirectory) || path.contains("..") || path.contains("\\")) {
                throw new BusinessException(ResultCode.FORBIDDEN, "视频来源不受信任");
            }
            return path;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ResultCode.FORBIDDEN, "视频来源不受信任");
        }
    }

    @Override
    public List<Map<String, Object>> getMyVideoPosts(Long userId, Integer status) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("userId", userId);
            params.put("postType", "video");
            if (ObjectUtils.isNotEmpty(status)) {
                params.put("isDeleted", Integer.valueOf(2).equals(status));
            }

            List<MusicPost> posts = musicPostMapper.selectByCondition(params);
            List<Map<String, Object>> result = new ArrayList<>();
            for (MusicPost post : posts) {
                Map<String, Object> videoInfo = readVideoInfo(post);
                Map<String, Object> postInfo = new HashMap<>();
                postInfo.put("id", post.getId());
                postInfo.put("content", post.getContent());
                postInfo.put("createTime", post.getCreateTime());
                postInfo.put("thumbnailUrl", videoInfo.get("thumbnail"));
                postInfo.put("duration", videoInfo.get("duration"));
                postInfo.put("videoUrl", ObjectUtils.defaultIfNull(videoInfo.get("720p"), videoInfo.get("original")));
                postInfo.put("status", Boolean.TRUE.equals(post.getIsDeleted()) ? 2 : 1);
                postInfo.put("processingStatus", videoInfo.get("status"));
                postInfo.put("likeCount", post.getLikeCount());
                postInfo.put("commentCount", post.getCommentCount());
                postInfo.put("shareCount", post.getShareCount());
                result.add(postInfo);
            }
            return result;
        } catch (Exception e) {
            log.error("event=video_post_list_query_failed userId={} errorType={}",
                    userId, e.getClass().getSimpleName());
            return new ArrayList<>();
        }
    }

    @Override
    public Map<String, Object> getVideoPostStats(Long userId) {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("postType", "video");
        List<MusicPost> allPosts = musicPostMapper.selectByCondition(params);

        int publishedCount = 0;
        int rejectedCount = 0;
        for (MusicPost post : allPosts) {
            if (Boolean.TRUE.equals(post.getIsDeleted())) {
                rejectedCount++;
            } else {
                publishedCount++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalCount", allPosts.size());
        result.put("pendingCount", 0);
        result.put("publishedCount", publishedCount);
        result.put("rejectedCount", rejectedCount);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteVideoPost(Long postId, Long userId) {
        MusicPost post = musicPostMapper.selectById(postId);
        if (ObjectUtils.isEmpty(post)) {
            return false;
        }
        if (!post.getUserId().equals(userId)) {
            return false;
        }
        int result = musicPostMapper.deleteById(postId);
        if (result > 0) {
            mediaAssetService.releaseTargetReferences(TARGET_TYPE_VIDEO_POST, postId);
        }
        return result > 0;
    }

    private void submitDerivativeJob(Long postId, String remoteOriginalPath, String remote480pPath,
                                     String remote720pPath, String remoteThumbnailPath) {
        try {
            mediaProcessingExecutor.execute(() -> processDerivatives(postId, remoteOriginalPath, remote480pPath,
                    remote720pPath, remoteThumbnailPath));
        } catch (RejectedExecutionException e) {
            updateVideoInfo(postId, info -> {
                info.put("status", STATUS_FAILED);
                info.put("progress", 0);
                info.put("message", "媒体处理队列已满，请稍后重试");
                info.put("error", "媒体处理暂不可用");
            });
            log.warn("event=video_post_derivative_submission_rejected postId={} errorType={}",
                    postId, e.getClass().getSimpleName());
        }
    }

    private void processDerivatives(Long postId, String remoteOriginalPath, String remote480pPath,
                                    String remote720pPath, String remoteThumbnailPath) {
        try {
            updateProcessingProgress(postId, 20, "正在生成视频封面");
            boolean thumbnailOk = node3MediaService.generateVideoThumbnail(remoteOriginalPath, remoteThumbnailPath,
                    postMediaConfig.getVideoThumbnailTime());

            updateProcessingProgress(postId, 45, "正在生成480p视频");
            boolean variant480Ok = node3MediaService.generateVideoVariant(remoteOriginalPath, remote480pPath, 480,
                    postMediaConfig.getVideoCrf480p(), postMediaConfig.getVideoMinDuration(), postMediaConfig.getVideoMaxDuration());

            updateProcessingProgress(postId, 75, "正在生成720p视频");
            boolean variant720Ok = node3MediaService.generateVideoVariant(remoteOriginalPath, remote720pPath, 720,
                    postMediaConfig.getVideoCrf720p(), postMediaConfig.getVideoMinDuration(), postMediaConfig.getVideoMaxDuration());

            if (variant480Ok || variant720Ok) {
                updateVideoInfo(postId, info -> {
                    info.put("status", STATUS_READY);
                    info.put("progress", 100);
                    info.put("message", "视频已就绪");
                    info.put("thumbnailReady", thumbnailOk);
                    info.put("480pReady", variant480Ok);
                    info.put("720pReady", variant720Ok);
                });
                log.info("event=video_post_derivatives_completed postId={} variant480pReady={} variant720pReady={} thumbnailReady={}",
                        postId, variant480Ok, variant720Ok, thumbnailOk);
            } else {
                updateVideoInfo(postId, info -> {
                    info.put("status", STATUS_FAILED);
                    info.put("progress", 0);
                    info.put("message", "视频转码失败，播放将回退到原件");
                    info.put("thumbnailReady", thumbnailOk);
                });
            }
        } catch (Exception e) {
            updateVideoInfo(postId, info -> {
                info.put("status", STATUS_FAILED);
                info.put("progress", 0);
                info.put("message", "视频处理失败，播放将回退到原件");
                info.put("error", "媒体处理失败");
            });
            log.error("event=video_post_derivative_processing_failed postId={} errorType={}",
                    postId, e.getClass().getSimpleName());
        }
    }

    private void updateProcessingProgress(Long postId, int progress, String message) {
        updateVideoInfo(postId, info -> {
            info.put("status", STATUS_PROCESSING);
            info.put("progress", progress);
            info.put("message", message);
        });
    }

    private void updateVideoInfo(Long postId, Consumer<Map<String, Object>> consumer) {
        try {
            MusicPost post = musicPostMapper.selectById(postId);
            if (ObjectUtils.isEmpty(post)) {
                return;
            }
            Map<String, Object> videoInfo = readVideoInfo(post);
            consumer.accept(videoInfo);
            post.setVideoInfo(objectMapper.writeValueAsString(videoInfo));
            post.setUpdateTime(LocalDateTime.now());
            musicPostMapper.updateById(post);
        } catch (Exception e) {
            log.warn("event=video_post_processing_status_update_failed postId={} errorType={}",
                    postId, e.getClass().getSimpleName());
        }
    }

    private void registerMediaAssets(Long postId, Map<String, Object> videoInfo, String remoteOriginalPath) {
        if (ObjectUtils.isEmpty(postId) || ObjectUtils.isEmpty(videoInfo)) {
            return;
        }
        mediaAssetService.registerAndRetain(
                "video", TARGET_TYPE_VIDEO_POST, postId, "original",
                ObjectUtils.castString(videoInfo.get("original")),
                MediaAsset.STORAGE_NODE_MEDIA, remoteOriginalPath,
                null, TARGET_TYPE_VIDEO_POST, postId);
        mediaAssetService.registerAndRetain(
                "video", TARGET_TYPE_VIDEO_POST, postId, "480p",
                ObjectUtils.castString(videoInfo.get("480p")),
                MediaAsset.STORAGE_NODE_MEDIA, appendPath(getVideo480pPath(),
                        ObjectUtils.castString(videoInfo.get("baseName")) + "_480p.mp4"),
                null, TARGET_TYPE_VIDEO_POST, postId);
        mediaAssetService.registerAndRetain(
                "video", TARGET_TYPE_VIDEO_POST, postId, "720p",
                ObjectUtils.castString(videoInfo.get("720p")),
                MediaAsset.STORAGE_NODE_MEDIA, appendPath(getVideo720pPath(),
                        ObjectUtils.castString(videoInfo.get("baseName")) + "_720p.mp4"),
                null, TARGET_TYPE_VIDEO_POST, postId);
        mediaAssetService.registerAndRetain(
                "image", TARGET_TYPE_VIDEO_POST, postId, "thumbnail",
                ObjectUtils.castString(videoInfo.get("thumbnail")),
                MediaAsset.STORAGE_NODE_MEDIA, appendPath(postMediaConfig.getVideoThumbnailPath(),
                        ObjectUtils.castString(videoInfo.get("baseName")) + "_thumb.jpg"),
                null, TARGET_TYPE_VIDEO_POST, postId);
    }

    private Map<String, Object> buildInitialVideoInfo(String baseName, String originalExtension, String originalUrl,
                                                      String url480p, String url720p, String thumbnailUrl,
                                                      long originalSize, VideoCompressUtil.VideoInfo sourceInfo) {
        Map<String, Object> videoInfo = new HashMap<>();
        videoInfo.put("original", originalUrl);
        videoInfo.put("originalUrl", originalUrl);
        videoInfo.put("480p", url480p);
        videoInfo.put("720p", url720p);
        videoInfo.put("thumbnail", thumbnailUrl);
        videoInfo.put("thumbnailUrl", thumbnailUrl);
        videoInfo.put("originalSize", originalSize);
        videoInfo.put("duration", sourceInfo.getDuration());
        videoInfo.put("width", sourceInfo.getWidth());
        videoInfo.put("height", sourceInfo.getHeight());
        videoInfo.put("format", sourceInfo.getFormat());
        videoInfo.put("baseName", baseName);
        videoInfo.put("extension", originalExtension);
        videoInfo.put("status", STATUS_PROCESSING);
        videoInfo.put("progress", 10);
        videoInfo.put("retryCount", 0);
        videoInfo.put("message", "视频已上传，正在后台生成清晰度文件");
        videoInfo.put("qualities", buildQualityList(originalUrl, url480p, url720p));
        return videoInfo;
    }

    private List<Map<String, String>> buildQualityList(String originalUrl, String url480p, String url720p) {
        List<Map<String, String>> qualities = new ArrayList<>();
        qualities.add(createQualityItem("原件", "original", originalUrl));
        qualities.add(createQualityItem("标清", "480p", url480p));
        qualities.add(createQualityItem("高清", "720p", url720p));
        return qualities;
    }

    private Map<String, String> createQualityItem(String label, String value, String url) {
        Map<String, String> quality = new HashMap<>();
        quality.put("label", label);
        quality.put("value", value);
        quality.put("url", url);
        return quality;
    }

    private boolean isDerivativeReady(Map<String, Object> videoInfo) {
        String path480p = qualityRemotePath(videoInfo, "480p");
        String path720p = qualityRemotePath(videoInfo, "720p");
        return (ObjectUtils.isNotEmpty(path480p) && node3MediaService.exists(path480p))
                || (ObjectUtils.isNotEmpty(path720p) && node3MediaService.exists(path720p));
    }

    private String qualityRemotePath(Map<String, Object> videoInfo, String quality) {
        String baseName = ObjectUtils.castString(videoInfo.get("baseName"));
        if (ObjectUtils.isEmpty(baseName)) {
            return null;
        }
        if ("480p".equals(quality)) {
            return appendPath(getVideo480pPath(), baseName + "_480p.mp4");
        }
        if ("720p".equals(quality)) {
            return appendPath(getVideo720pPath(), baseName + "_720p.mp4");
        }
        return null;
    }

    private int toInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(ObjectUtils.castString(value));
        } catch (Exception e) {
            return 0;
        }
    }

    private Map<String, Object> readVideoInfo(MusicPost post) {
        if (ObjectUtils.isEmpty(post)) {
            return new HashMap<>();
        }
        String videoInfoJson = ObjectUtils.isNotEmpty(post.getVideoInfo()) ? post.getVideoInfo() : post.getTopics();
        if (ObjectUtils.isEmpty(videoInfoJson)) {
            return new HashMap<>();
        }
        try {
            Map<String, Object> videoInfo = objectMapper.readValue(videoInfoJson, Map.class);
            return ObjectUtils.isEmpty(videoInfo) ? new HashMap<>() : videoInfo;
        } catch (Exception e) {
            log.debug("event=video_post_metadata_parse_failed postId={}", post.getId());
            return new HashMap<>();
        }
    }

    private Map<String, Object> sanitizeVideoInfo(Map<String, Object> videoInfo) {
        Map<String, Object> sanitized = new HashMap<>(videoInfo);
        sanitized.remove("remoteOriginalPath");
        sanitized.remove("remote480pPath");
        sanitized.remove("remote720pPath");
        sanitized.remove("remoteThumbnailPath");
        return sanitized;
    }

    private void validateVideoFile(MultipartFile file) {
        if (ObjectUtils.isEmpty(file) || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        if (file.getSize() > postMediaConfig.getVideoMaxSize()) {
            throw new IllegalArgumentException("视频大小不能超过" + formatSize(postMediaConfig.getVideoMaxSize()));
        }
        String contentType = file.getContentType();
        if (ObjectUtils.isEmpty(contentType) || ObjectUtils.isEmpty(postMediaConfig.getVideoAllowedContentTypes())
                || !postMediaConfig.getVideoAllowedContentTypes().contains(contentType)) {
            throw new IllegalArgumentException("只支持MP4、MOV、AVI视频");
        }
    }

    private void scanVideoFile(File file) {
        if (!musicUploadConfig.isVirusScanEnabled()) {
            return;
        }
        try {
            if (!virusScanService.scanFile(file)) {
                throw new SecurityException("video virus scan failed");
            }
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new SecurityException("video virus scan failed", e);
        }
    }

    private void validateVideoInfo(VideoCompressUtil.VideoInfo info, String remotePath) {
        if (ObjectUtils.isEmpty(info) || info.getDuration() <= 0) {
            node3MediaService.deleteQuietly(remotePath);
            throw new IllegalArgumentException("无法读取视频信息");
        }
        if (info.getDuration() < postMediaConfig.getVideoMinDuration()
                || info.getDuration() > postMediaConfig.getVideoMaxDuration()) {
            node3MediaService.deleteQuietly(remotePath);
            throw new IllegalArgumentException("视频时长必须在" + postMediaConfig.getVideoMinDuration()
                    + "-" + postMediaConfig.getVideoMaxDuration() + "秒之间");
        }
    }

    private String getSafeVideoExtension(MultipartFile file) {
        String extension = CommonUtil.getFileExtensionWithDot(file.getOriginalFilename());
        if (ObjectUtils.isEmpty(extension)) {
            extension = inferExtension(file.getContentType());
        }
        List<String> allowed = Arrays.asList(".mp4", ".mov", ".avi");
        if (!allowed.contains(extension.toLowerCase())) {
            extension = inferExtension(file.getContentType());
        }
        return ObjectUtils.isEmpty(extension) ? ".mp4" : extension.toLowerCase();
    }

    private String inferExtension(String contentType) {
        if ("video/quicktime".equalsIgnoreCase(contentType)) {
            return ".mov";
        }
        if ("video/x-msvideo".equalsIgnoreCase(contentType)) {
            return ".avi";
        }
        return ".mp4";
    }

    private String normalizeQuality(String quality) {
        if ("thumbnail".equalsIgnoreCase(quality)) {
            return "thumbnail";
        }
        if ("480p".equalsIgnoreCase(quality)) {
            return "480p";
        }
        if ("original".equalsIgnoreCase(quality)) {
            return "original";
        }
        return "720p";
    }

    private String buildPublicUrl(String relativePath) {
        String prefix = postMediaConfig.getVideoNginxUrlPrefix();
        if (ObjectUtils.isEmpty(prefix)) {
            prefix = postMediaConfig.getNginxUrlPrefix();
        }
        if (!prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        return prefix + relativePath;
    }

    private String appendPath(String dir, String filename) {
        String normalizedDir = dir.endsWith("/") ? dir.substring(0, dir.length() - 1) : dir;
        return normalizedDir + "/" + filename;
    }

    private String getVideo480pPath() {
        return ObjectUtils.isNotEmpty(postMediaConfig.getVideo480pPath())
                ? postMediaConfig.getVideo480pPath()
                : postMediaConfig.getVideoCachePath();
    }

    private String getVideo720pPath() {
        return ObjectUtils.isNotEmpty(postMediaConfig.getVideo720pPath())
                ? postMediaConfig.getVideo720pPath()
                : postMediaConfig.getVideoCachePath();
    }

    private String getRequiredPath(String path, String name) {
        if (ObjectUtils.isEmpty(path)) {
            throw new IllegalStateException(name + " is not configured");
        }
        return path;
    }

    private void deleteLocalQuietly(File file) {
        try {
            if (ObjectUtils.isNotEmpty(file) && file.exists() && !file.delete()) {
                log.warn("event=video_post_temp_cleanup_deferred");
            }
        } catch (Exception e) {
            log.warn("event=video_post_temp_cleanup_failed errorType={}", e.getClass().getSimpleName());
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}


