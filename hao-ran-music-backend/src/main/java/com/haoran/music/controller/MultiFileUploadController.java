










package com.haoran.music.controller;

import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.aspect.RateLimit;
import com.haoran.music.common.aspect.RateLimitScope;
import com.haoran.music.common.config.MusicUploadConfig;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.Result;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.service.MultiFileUploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;




@Slf4j
@RestController
@RequestMapping("/upload/multi-file")
public class MultiFileUploadController {

    @Autowired
    private MultiFileUploadService multiFileUploadService;

    @Autowired
    private MusicUploadConfig musicUploadConfig;







    @PostMapping("/batch")
    @ApiLog("批量上传文件")
    @RateLimit(maxRequests = 6, timeWindowSeconds = 60, operation = "submissionBatchUpload",
            scope = RateLimitScope.USER, captchaBypass = false, failClosed = true,
            message = "文件上传过于频繁，请稍后再试")
    public Result<Map<String, Object>> uploadMultipleFiles(
            @RequestParam("files") MultipartFile[] files,
            HttpServletRequest request) {

        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        try {
            Map<String, Object> result = multiFileUploadService.uploadMultipleFiles(
                Arrays.asList(files), userId);
            return Result.success(result);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("event=submission_upload_request_failed userId={} errorType={}",
                    userId, e.getClass().getSimpleName());
            return Result.error(500, "批量上传失败，请稍后重试");
        }
    }







    @PostMapping("/extract-zip")
    @ApiLog("上传并解压压缩包")
    @RateLimit(maxRequests = 2, timeWindowSeconds = 300, operation = "submissionArchiveUpload",
            scope = RateLimitScope.USER, captchaBypass = false, failClosed = true,
            message = "压缩包处理过于频繁，请稍后再试")
    public Result<Map<String, Object>> uploadAndExtractZip(
            @RequestParam("file") MultipartFile zipFile,
            HttpServletRequest request) {

        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        try {
            Map<String, Object> result = multiFileUploadService.uploadAndExtractZip(zipFile, userId);
            return Result.success(result);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("event=submission_archive_request_failed userId={} errorType={}",
                    userId, e.getClass().getSimpleName());
            return Result.error(500, "压缩包处理失败，请稍后重试");
        }
    }







    @GetMapping("/identify-type")
    public Result<String> identifyFileType(@RequestParam String fileName) {
        String type = multiFileUploadService.identifyFileType(fileName);
        return Result.successData(type);
    }







    @PostMapping("/detect-audio-batch")
    @ApiLog("批量检测音频质量")
    @RateLimit(maxRequests = 10, timeWindowSeconds = 60, operation = "submissionAudioDetection",
            scope = RateLimitScope.USER, captchaBypass = false, failClosed = true,
            message = "音频检测过于频繁，请稍后再试")
    public Result<List<Map<String, Object>>> batchDetectAudioQuality(
            @RequestBody Map<String, Object> params,
            @RequestAttribute("userId") Long userId) {

        try {
            @SuppressWarnings("unchecked")
            List<String> audioUrls = ObjectUtils.castList(params.get("audioUrls"), String.class);
            List<Map<String, Object>> result = multiFileUploadService.batchDetectAudioQuality(audioUrls, userId);
            return Result.success(result);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("event=submission_audio_detection_request_failed errorType={}",
                    e.getClass().getSimpleName());
            return Result.error(500, "批量音频检测失败，请稍后重试");
        }
    }







    @PostMapping("/detect-video-batch")
    @ApiLog("批量检测视频信息")
    @RateLimit(maxRequests = 10, timeWindowSeconds = 60, operation = "submissionVideoDetection",
            scope = RateLimitScope.USER, captchaBypass = false, failClosed = true,
            message = "视频检测过于频繁，请稍后再试")
    public Result<List<Map<String, Object>>> batchDetectVideoInfo(
            @RequestBody Map<String, Object> params,
            @RequestAttribute("userId") Long userId) {

        try {
            @SuppressWarnings("unchecked")
            List<String> videoUrls = ObjectUtils.castList(params.get("videoUrls"), String.class);
            List<Map<String, Object>> result = multiFileUploadService.batchDetectVideoInfo(videoUrls, userId);
            return Result.success(result);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("event=submission_video_detection_request_failed errorType={}",
                    e.getClass().getSimpleName());
            return Result.error(500, "批量视频检测失败，请稍后重试");
        }
    }






    @GetMapping("/supported-formats")
    public Result<Map<String, String[]>> getSupportedFormats() {
        Map<String, String[]> formats = new HashMap<>();
        formats.put("audio", new String[]{"mp3", "flac", "wav", "m4a", "aac", "ogg", "wma", "ape"});
        formats.put("video", new String[]{"mp4", "mov", "avi", "mkv", "flv", "wmv"});
        formats.put("lyric", new String[]{"lrc", "txt", "json"});
        formats.put("image", new String[]{"jpg", "jpeg", "png", "gif", "webp"});
        formats.put("archive", new String[]{"zip"});
        return Result.success(Collections.unmodifiableMap(formats));
    }






    @GetMapping("/limits")
    public Result<Map<String, Object>> getUploadLimits() {
        Map<String, Object> limits = new HashMap<>();
        limits.put("maxFiles", musicUploadConfig.getMaxFiles());
        limits.put("maxTotalSize", formatSize(musicUploadConfig.getMaxTotalSize()));
        limits.put("maxSingleFileSize", formatSize(musicUploadConfig.getSingleFileMaxSize()));
        limits.put("maxZipSize", formatSize(musicUploadConfig.getArchiveMaxFileSize()));
        limits.put("dailyMaxFilesPerUser", musicUploadConfig.getSubmissionDailyMaxFilesPerUser());
        limits.put("dailyMaxBytesPerUser", musicUploadConfig.getSubmissionDailyMaxBytesPerUser());
        limits.put("maxConcurrentUploads", musicUploadConfig.getSubmissionMaxConcurrentUploads());
        return Result.success(Collections.unmodifiableMap(limits));
    }

    private String formatSize(long bytes) {
        return (bytes / 1024 / 1024) + "MB";
    }
}
