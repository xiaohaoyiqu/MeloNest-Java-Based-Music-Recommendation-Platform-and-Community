


package com.haoran.music.controller;

import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.aspect.RateLimit;
import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.config.MusicVideoConfig;
import com.haoran.music.common.result.Result;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.MusicPostService;
import com.haoran.music.service.VideoPostService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.util.Map;




@Slf4j
@RestController
@RequestMapping("/video-post")
public class VideoPostController {

    @Autowired
    private MusicPostService musicPostService;

    @Autowired
    private VideoPostService videoPostService;

    @Autowired
    private MusicVideoConfig musicVideoConfig;




    @PostMapping("/upload")
    @ApiLog("upload video post")
    @RateLimit(maxRequests = 3, timeWindowSeconds = 3600, operation = "uploadVideoPostLegacy",
            message = "视频上传过于频繁，请稍后再试")
    public Result uploadVideo(@RequestParam("file") MultipartFile file,
                              @RequestParam("content") String content,
                              @RequestParam(value = "topics", required = false) String topics,
                              HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (ObjectUtils.isEmpty(userId)) {
            return Result.error("user is not logged in");
        }

        Map<String, Object> result = videoPostService.uploadVideoPost(file, userId, content, topics, true);
        if (!Boolean.TRUE.equals(result.get("success"))) {
            return Result.error("video upload failed, please try again later");
        }
        return Result.success(result);
    }




    @GetMapping("/status/{postId}")
    public Result getUploadStatus(@PathVariable Long postId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(videoPostService.getVideoPostStatus(postId, userId));
    }




    @GetMapping("/my")
    @ApiLog("list my video posts")
    public Result getMyVideoPosts(@RequestParam(value = "status", required = false) Integer status,
                                  HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (ObjectUtils.isEmpty(userId)) {
            return Result.error("user is not logged in");
        }
        return Result.success(musicPostService.getMyVideoPosts(userId, status));
    }




    @GetMapping("/stats")
    @ApiLog("get video post statistics")
    public Result getVideoPostStats(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (ObjectUtils.isEmpty(userId)) {
            return Result.error("user is not logged in");
        }
        return Result.success(musicPostService.getVideoPostStats(userId));
    }




    @DeleteMapping("/{postId}")
    @ApiLog("delete video post")
    public Result deleteVideoPost(@PathVariable Long postId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (ObjectUtils.isEmpty(userId)) {
            return Result.error("user is not logged in");
        }
        return musicPostService.deleteVideoPost(postId, userId)
                ? Result.success("delete success")
                : Result.error("delete failed");
    }




    @PostMapping("/cleanup-temp")
    @ApiLog("cleanup temporary video files")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    public Result cleanupTempFiles() {
        try {
            File tempDir = new File(musicVideoConfig.getTempPath());
            if (!tempDir.exists()) {
                return Result.success("temporary directory does not exist");
            }

            File[] files = tempDir.listFiles();
            int cleaned = 0;
            if (files != null) {
                long retentionMillis = musicVideoConfig.getTempRetentionHours() * 60L * 60 * 1000;
                long cutoffTime = System.currentTimeMillis() - retentionMillis;
                for (File file : files) {
                    if (file.lastModified() < cutoffTime && file.delete()) {
                        cleaned++;
                    }
                }
            }
            return Result.success("cleanup completed, files removed: " + cleaned);
        } catch (Exception e) {
            log.error("event=video_post_temp_cleanup_failed errorType={}", e.getClass().getSimpleName());
            return Result.error(500, "cleanup failed, please try again later");
        }
    }

}
