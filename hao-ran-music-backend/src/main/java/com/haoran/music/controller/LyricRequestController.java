package com.haoran.music.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.aspect.RateLimit;
import com.haoran.music.common.aspect.RateLimitScope;
import com.haoran.music.common.result.Result;
import com.haoran.music.dto.lyric.LyricRequestDTO;
import com.haoran.music.entity.LyricRequest;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.LyricRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;





@RestController
@RequestMapping("/lyric-request")
public class LyricRequestController {

    @Autowired
    private LyricRequestService lyricRequestService;

    @ApiLog("Submit lyric correction")
    @PostMapping("/submit")
    @RateLimit(maxRequests = 10, timeWindowSeconds = 60, operation = "lyricCorrectionSubmit",
            scope = RateLimitScope.USER, captchaBypass = false, failClosed = true,
            message = "歌词申请提交过于频繁，请稍后再试")
    public Result<Long> submitRequest(@Valid @RequestBody LyricRequestDTO dto,
                                     @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "Please login first");
        }

        Long requestId = lyricRequestService.submitRequest(userId, dto);
        return Result.success(requestId);
    }

    @ApiLog("Check submit permission")
    @GetMapping("/can-submit/{songId}")
    public Result<Boolean> canSubmit(@PathVariable("songId") Long songId,
                                     @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "Please login first");
        }
        Boolean canSubmit = lyricRequestService.canSubmit(userId, songId);
        return Result.success(canSubmit);
    }

    @ApiLog("Query correction requests")
    @GetMapping("/page")
    @RequireRole({UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN})
    public Result<IPage<LyricRequest>> pageRequests(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "size", defaultValue = "10") Integer size,
            @RequestParam(value = "status", required = false) Integer status) {
        IPage<LyricRequest> result = lyricRequestService.pageRequests(current, size, status);
        return Result.success(result);
    }

    @ApiLog("Query song corrections")
    @GetMapping("/song/{songId}")
    @RequireRole({UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN})
    public Result<List<LyricRequest>> getSongRequests(@PathVariable("songId") Long songId) {
        List<LyricRequest> result = lyricRequestService.getSongRequests(songId);
        return Result.success(result);
    }

    @ApiLog("Review lyric correction")
    @PostMapping("/review/{requestId}")
    @RateLimit(maxRequests = 30, timeWindowSeconds = 60, operation = "lyricCorrectionReview",
            scope = RateLimitScope.USER, captchaBypass = false, failClosed = true,
            message = "歌词审核操作过于频繁，请稍后再试")
    @RequireRole({UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN})
    public Result<Void> reviewRequest(
            @PathVariable("requestId") Long requestId,
            @RequestParam("status") Integer status,
            @RequestParam(value = "reviewReason", required = false) String reviewReason,
            @RequestAttribute(value = "userId", required = false) Long reviewerId) {
        if (reviewerId == null) {
            return Result.error(401, "Please login first");
        }

        lyricRequestService.reviewRequest(requestId, reviewerId, status, reviewReason);
        return Result.success(null);
    }

    @ApiLog("Apply lyric correction")
    @PostMapping("/apply/{requestId}")
    @RateLimit(maxRequests = 30, timeWindowSeconds = 60, operation = "lyricCorrectionApply",
            scope = RateLimitScope.USER, captchaBypass = false, failClosed = true,
            message = "歌词应用操作过于频繁，请稍后再试")
    @RequireRole({UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN})
    public Result<Void> applyRequest(
            @PathVariable("requestId") Long requestId,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "Please login first");
        }

        lyricRequestService.applyRequest(requestId, userId);
        return Result.success(null);
    }
}
