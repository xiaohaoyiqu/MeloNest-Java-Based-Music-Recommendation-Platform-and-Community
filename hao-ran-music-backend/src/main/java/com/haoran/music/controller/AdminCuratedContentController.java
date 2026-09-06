


package com.haoran.music.controller;

import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.result.Result;
import com.haoran.music.dto.curated.CuratedBannerRequest;
import com.haoran.music.dto.curated.CuratedNewsRequest;
import com.haoran.music.dto.curated.CuratedReviewRequest;
import com.haoran.music.entity.HotEvent;
import com.haoran.music.entity.PushNotification;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.HotEventService;
import com.haoran.music.service.PushNotificationService;
import com.haoran.music.task.NewsFeedRefreshTask;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;




@RestController
@RequestMapping("/admin/curated-content")
@RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
public class AdminCuratedContentController {

    @Resource
    private PushNotificationService pushNotificationService;

    @Resource
    private HotEventService hotEventService;

    @Resource
    private NewsFeedRefreshTask newsFeedRefreshTask;

    @GetMapping("/news")
    public Result<List<PushNotification>> listNews() {
        return Result.success(pushNotificationService.listAdminNews());
    }

    @PostMapping("/news")
    public Result<String> createNews(
            @Valid @RequestBody CuratedNewsRequest request,
            @RequestAttribute(value = "userId", required = false) Long operatorId) {
        try {
            return Result.successData(pushNotificationService.createAdminNews(request, requireOperator(operatorId)));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PutMapping("/news/{pushId}")
    public Result<Void> updateNews(
            @PathVariable String pushId,
            @Valid @RequestBody CuratedNewsRequest request,
            @RequestAttribute(value = "userId", required = false) Long operatorId) {
        try {
            boolean success = pushNotificationService.updateAdminNews(
                    pushId, request, requireOperator(operatorId));
            if (!success) {
                return Result.error(404, "新闻不存在");
            }
            newsFeedRefreshTask.refreshNow();
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PostMapping("/news/{pushId}/review")
    public Result<Void> reviewNews(
            @PathVariable String pushId,
            @Valid @RequestBody CuratedReviewRequest request,
            @RequestAttribute(value = "userId", required = false) Long reviewerId) {
        try {
            boolean success = pushNotificationService.reviewAdminNews(
                    pushId,
                    request.getApproved(),
                    request.getRemark(),
                    requireOperator(reviewerId));
            if (!success) {
                return Result.error(404, "新闻不存在");
            }
            newsFeedRefreshTask.refreshNow();
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/news/{pushId}")
    public Result<Void> disableNews(
            @PathVariable String pushId,
            @RequestAttribute(value = "userId", required = false) Long operatorId) {
        boolean success = pushNotificationService.disableAdminNews(pushId, requireOperator(operatorId));
        if (!success) {
            return Result.error(404, "新闻不存在");
        }
        newsFeedRefreshTask.refreshNow();
        return Result.success();
    }

    @GetMapping("/banners")
    public Result<List<HotEvent>> listBanners() {
        return Result.success(hotEventService.listAdminFeaturedEvents());
    }

    @PostMapping("/banners")
    public Result<Long> createBanner(
            @Valid @RequestBody CuratedBannerRequest request,
            @RequestAttribute(value = "userId", required = false) Long operatorId) {
        try {
            return Result.success(hotEventService.createAdminFeaturedEvent(
                    request, requireOperator(operatorId)));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PutMapping("/banners/{eventId}")
    public Result<Void> updateBanner(
            @PathVariable Long eventId,
            @Valid @RequestBody CuratedBannerRequest request,
            @RequestAttribute(value = "userId", required = false) Long operatorId) {
        try {
            boolean success = hotEventService.updateAdminFeaturedEvent(
                    eventId, request, requireOperator(operatorId));
            return success ? Result.success() : Result.error(404, "轮播不存在");
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PostMapping("/banners/{eventId}/review")
    public Result<Void> reviewBanner(
            @PathVariable Long eventId,
            @Valid @RequestBody CuratedReviewRequest request,
            @RequestAttribute(value = "userId", required = false) Long reviewerId) {
        try {
            boolean success = hotEventService.reviewAdminFeaturedEvent(
                    eventId,
                    request.getApproved(),
                    request.getRemark(),
                    requireOperator(reviewerId));
            return success ? Result.success() : Result.error(404, "轮播不存在");
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/banners/{eventId}")
    public Result<Void> disableBanner(
            @PathVariable Long eventId,
            @RequestAttribute(value = "userId", required = false) Long operatorId) {
        boolean success = hotEventService.disableAdminFeaturedEvent(eventId, requireOperator(operatorId));
        return success ? Result.success() : Result.error(404, "轮播不存在");
    }

    private Long requireOperator(Long operatorId) {
        if (operatorId == null) {
            throw new IllegalArgumentException("请先登录");
        }
        return operatorId;
    }
}
