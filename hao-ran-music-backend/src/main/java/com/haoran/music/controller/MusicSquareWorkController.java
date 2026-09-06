package com.haoran.music.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.aspect.BlockCrawlerContent;
import com.haoran.music.common.result.Result;
import com.haoran.music.entity.MusicSquareWork;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.ContentAccessLimitService;
import com.haoran.music.service.MusicSquareWorkService;
import com.haoran.music.common.util.AudioQualityDetector;
import com.haoran.music.common.dto.PageQuery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;










@Slf4j
@RestController
@RequestMapping("/music-square/work")
public class MusicSquareWorkController {

    private static final int STATUS_PUBLISHED = 1;

    private final MusicSquareWorkService musicSquareWorkService;

    @Resource
    private ContentAccessLimitService contentAccessLimitService;

    public MusicSquareWorkController(MusicSquareWorkService musicSquareWorkService) {
        this.musicSquareWorkService = musicSquareWorkService;
    }




    @ApiLog("提交音乐广场投稿")
    @PostMapping("/submit")
    public Result submitWork(HttpServletRequest request,
                           @RequestParam Integer workType,
                           @RequestParam String title,
                           @RequestParam(required = false) String description,
                           @RequestParam(required = false) String coverUrl,
                           @RequestParam(required = false) String tags,
                           @RequestParam(required = false) String audioUrl,
                           @RequestParam(required = false) String videoUrl,
                           @RequestParam(required = false) String lyricContent,
                           @RequestParam(required = false) String lyricFileUrl,
                           @RequestParam(required = false) Integer uploadType,
                           @RequestParam(required = false) String fileUrls,
                           @RequestParam(required = false) String zipFileUrl) {
        Long userId = (Long) request.getAttribute("userId");

        MusicSquareWorkService.MusicSquareWorkDTO dto = new MusicSquareWorkService.MusicSquareWorkDTO();
        dto.setWorkType(workType);
        dto.setTitle(title);
        dto.setDescription(description);
        dto.setCoverUrl(coverUrl);
        dto.setTags(tags);
        dto.setAudioUrl(audioUrl);
        dto.setVideoUrl(videoUrl);
        dto.setLyricContent(lyricContent);
        dto.setLyricFileUrl(lyricFileUrl);
        dto.setUploadType(uploadType);
        dto.setFileUrls(fileUrls);
        dto.setZipFileUrl(zipFileUrl);

        Long workId = musicSquareWorkService.submitWork(userId, dto);
        return Result.success(workId);
    }




    @ApiLog("检测音频信息")
    @GetMapping("/detect/audio")
    public Result detectAudio(HttpServletRequest request, @RequestParam String audioUrl) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "Unauthorized");
        }
        AudioQualityDetector.AudioInfo info = musicSquareWorkService.detectAudioInfo(userId, audioUrl);
        return Result.success(info);
    }




    @ApiLog("检测视频信息")
    @GetMapping("/detect/video")
    public Result detectVideo(HttpServletRequest request, @RequestParam String videoUrl) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "Unauthorized");
        }
        MusicSquareWorkService.VideoInfo info = musicSquareWorkService.detectVideoInfo(userId, videoUrl);
        return Result.success(info);
    }





    @ApiLog("获取音乐广场投稿列表")
    @GetMapping("/list")
    public Result listWorks(@RequestParam(defaultValue = "1") Integer page,
                           @RequestParam(defaultValue = "20") Integer size,
                           @RequestParam(required = false) Integer workType,
                           @RequestParam(required = false) Integer status) {
        PageQuery pageQuery = new PageQuery(page, size);
        IPage<MusicSquareWork> result = musicSquareWorkService.pageWorks(pageQuery, workType, STATUS_PUBLISHED);
        return Result.success(result);
    }




    @ApiLog("获取我的音乐广场投稿")
    @GetMapping("/my")
    public Result getMyWorks(HttpServletRequest request,
                           @RequestParam(defaultValue = "1") Integer page,
                           @RequestParam(defaultValue = "20") Integer size) {
        Long userId = (Long) request.getAttribute("userId");
        PageQuery pageQuery = new PageQuery(page, size);
        IPage<MusicSquareWork> result = musicSquareWorkService.getMyWorks(userId, pageQuery);
        return Result.success(result);
    }





    @ApiLog("获取音乐广场投稿详情")
    @GetMapping("/{id}")
    public Result getWorkDetail(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");


        if (userId != null && !contentAccessLimitService.checkCreatorBatchAccess(userId)) {
            return Result.error(429, "访问过于频繁，请稍后再试");
        }

        MusicSquareWork work = musicSquareWorkService.getVisibleWorkDetail(id, userId);
        if (work != null) {

            musicSquareWorkService.incrementViewCount(id, userId);

            if (userId != null) {
                contentAccessLimitService.recordCreatorContentAccess(userId, id, "music-square-work");
            }
        }
        return Result.success(work);
    }





    @ApiLog("点赞音乐广场投稿")
    @PostMapping("/{id}/like")
    public Result likeWork(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        musicSquareWorkService.likeWork(id, userId);
        return Result.success();
    }





    @ApiLog("取消点赞音乐广场投稿")
    @DeleteMapping("/{id}/like")
    public Result unlikeWork(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        musicSquareWorkService.unlikeWork(id, userId);
        return Result.success();
    }




    @ApiLog("删除音乐广场投稿")
    @DeleteMapping("/{id}")
    public Result deleteWork(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        musicSquareWorkService.deleteWork(id, userId);
        return Result.success();
    }




    @ApiLog("编辑音乐广场投稿")
    @PutMapping("/{id}")
    public Result updateWork(@PathVariable Long id,
                           HttpServletRequest request,
                           @RequestParam(required = false) Integer workType,
                           @RequestParam(required = false) String title,
                           @RequestParam(required = false) String description,
                           @RequestParam(required = false) String coverUrl,
                           @RequestParam(required = false) String tags,
                           @RequestParam(required = false) String audioUrl,
                           @RequestParam(required = false) String videoUrl,
                           @RequestParam(required = false) String lyricContent,
                           @RequestParam(required = false) String lyricFileUrl) {
        Long userId = (Long) request.getAttribute("userId");

        MusicSquareWorkService.MusicSquareWorkDTO dto = new MusicSquareWorkService.MusicSquareWorkDTO();
        dto.setWorkType(workType);
        dto.setTitle(title);
        dto.setDescription(description);
        dto.setCoverUrl(coverUrl);
        dto.setTags(tags);
        dto.setAudioUrl(audioUrl);
        dto.setVideoUrl(videoUrl);
        dto.setLyricContent(lyricContent);
        dto.setLyricFileUrl(lyricFileUrl);

        musicSquareWorkService.updateWork(id, userId, dto);
        return Result.success();
    }




    @ApiLog("审核音乐广场投稿")
    @PostMapping("/{id}/review")
    @RequireRole({UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN})
    public Result reviewWork(@PathVariable Long id,
                           @RequestAttribute(value = "userId", required = false) Long reviewerId,
                           @RequestParam Integer status,
                           @RequestParam(required = false) String reviewReason) {
        if (reviewerId == null) {
            return Result.error(401, "Unauthorized");
        }
        musicSquareWorkService.reviewWork(id, reviewerId, status, reviewReason);
        return Result.success();
    }




    @ApiLog("获取音乐广场待审核数量")
    @GetMapping("/pending/count")
    @RequireRole({UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN})
    public Result getPendingCount() {
        Long count = musicSquareWorkService.getPendingCount();
        return Result.success(count);
    }
}
