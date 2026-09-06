




package com.haoran.music.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.aspect.RateLimit;
import com.haoran.music.common.aspect.RateLimitScope;
import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.common.result.Result;
import com.haoran.music.entity.Emoji;
import com.haoran.music.entity.EmojiPackage;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.EmojiService;
import com.haoran.music.vo.emoji.EmojiDisplayVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;




@Slf4j
@RestController
@RequestMapping("/emoji")
public class EmojiController {

    private final EmojiService emojiService;

    public EmojiController(EmojiService emojiService) {
        this.emojiService = emojiService;
    }






    @GetMapping("/packages")
    @ApiLog("获取表情包列表")
    public Result<List<EmojiPackage>> getAllPackages(
            @RequestAttribute(value = "userId", required = false) Long userId) {
        List<EmojiPackage> packages = emojiService.getAllPackages(userId);
        return Result.success(packages);
    }






    @GetMapping("/packages/enabled")
    public Result<List<EmojiPackage>> getEnabledPackages(
            @RequestAttribute(value = "userId", required = false) Long userId) {
        List<EmojiPackage> packages = emojiService.getEnabledPackages(userId);
        return Result.success(packages);
    }







    @GetMapping("/package/{packageId}")
    @ApiLog("获取表情包详情")
    public Result<EmojiPackage> getPackageDetail(
            @PathVariable Long packageId,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        EmojiPackage emojiPackage = emojiService.getPackageById(packageId, userId);
        if (emojiPackage == null) {
            return Result.error(404, "表情包不存在");
        }


        List<Emoji> emojis = emojiService.getEmojisByPackage(packageId, userId);
        emojiPackage.setEmojis(emojis);

        return Result.success(emojiPackage);
    }


    @GetMapping("/package/{packageId}/review-detail")
    @RequireRole({UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN})
    public Result<EmojiPackage> getPackageReviewDetail(@PathVariable Long packageId) {
        EmojiPackage emojiPackage = emojiService.getPackageForReview(packageId);
        return emojiPackage == null
                ? Result.error(404, "表情包不存在")
                : Result.success(emojiPackage);
    }






    @GetMapping("/system")
    public Result<List<Emoji>> getSystemEmojis(
            @RequestAttribute(value = "userId", required = false) Long userId) {
        List<Emoji> emojis = emojiService.getSystemEmojis(userId);
        return Result.success(emojis);
    }







    @GetMapping("/category/{category}")
    @ApiLog("获取分类表情")
    public Result<List<Emoji>> getEmojisByCategory(
            @PathVariable String category,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        List<Emoji> emojis = emojiService.getEmojisByCategory(category, userId);
        return Result.success(emojis);
    }







    @GetMapping("/search")
    @ApiLog("搜索表情")
    public Result<List<Emoji>> searchEmojis(
            @RequestParam String keyword,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        List<Emoji> emojis = emojiService.searchEmojis(keyword, userId);
        return Result.success(emojis);
    }





    @GetMapping("/render")
    public Result<List<EmojiDisplayVO>> resolveDisplayEmojis(@RequestParam List<String> codes) {
        return Result.success(emojiService.resolveDisplayEmojis(codes));
    }







    @GetMapping("/hot")
    public Result<List<Emoji>> getHotEmojis(
            @RequestParam(defaultValue = "50") Integer limit,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        List<Emoji> emojis = emojiService.getHotEmojis(limit, userId);
        return Result.success(emojis);
    }








    @GetMapping("/packages/page")
    @ApiLog("分页查询表情包")
    public Result<IPage<EmojiPackage>> pagePackages(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        PageQuery pageQuery = new PageQuery(page, size);
        IPage<EmojiPackage> result = emojiService.pagePackages(pageQuery, userId);
        return Result.success(result);
    }







    @GetMapping("/packages/my")
    @ApiLog("获取我的表情包")
    public Result<List<EmojiPackage>> getMyPackages(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        List<EmojiPackage> packages = emojiService.getUserPackages(userId);
        return Result.success(packages);
    }








    @PostMapping("/package/create")
    @ApiLog("创建表情包")
    @RateLimit(maxRequests = 10, timeWindowSeconds = 3600, scope = RateLimitScope.USER,
            operation = "emojiPackageCreate", captchaBypass = false, failClosed = true)
    public Result<Long> createPackage(@RequestBody EmojiPackage emojiPackage,
                                       HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        Long packageId = emojiService.createCustomPackage(emojiPackage, userId);
        return Result.success(packageId);
    }

    @PutMapping("/package/{packageId}")
    @ApiLog("修改表情包资料")
    public Result<Void> updatePackage(@PathVariable Long packageId,
                                      @RequestBody EmojiPackage emojiPackage,
                                      @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        emojiService.updateCustomPackage(packageId, emojiPackage, userId);
        return Result.success();
    }


    @PostMapping("/package/{packageId}/submit")
    @ApiLog("提交表情包审核")
    @RateLimit(maxRequests = 5, timeWindowSeconds = 3600, scope = RateLimitScope.USER,
            operation = "emojiPackageSubmit", captchaBypass = false, failClosed = true)
    public Result<Void> submitPackage(@PathVariable Long packageId,
                                      @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        emojiService.submitPackage(packageId, userId);
        return Result.success();
    }


    @PutMapping("/package/{packageId}/cover")
    @ApiLog("设置表情包封面")
    public Result<Void> setPackageCover(@PathVariable Long packageId,
                                        @RequestBody Map<String, Object> params,
                                        @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        Object emojiId = params.get("emojiId");
        if (emojiId == null) {
            return Result.error(400, "请选择封面表情");
        }
        emojiService.setCustomPackageCover(packageId, Long.valueOf(emojiId.toString()), userId);
        return Result.success();
    }






    @PostMapping("/{emojiId}/usage")
    @ApiLog("记录表情使用")
    public Result<Void> recordUsage(
            @PathVariable Long emojiId,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        emojiService.recordUsage(emojiId, userId);
        return Result.success();
    }








    @DeleteMapping("/{emojiId}")
    @ApiLog("删除自定义表情")
    public Result<Boolean> deleteEmoji(@PathVariable Long emojiId,
                                    HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        boolean result = emojiService.deleteCustomEmoji(emojiId, userId);
        return Result.success(result);
    }

    @DeleteMapping("/package/{packageId}")
    @ApiLog("删除表情包草稿")
    public Result<Boolean> deletePackage(@PathVariable Long packageId,
                                         HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(emojiService.deleteCustomPackage(packageId, userId));
    }






    @GetMapping("/statistics")
    @ApiLog("获取表情统计")
    public Result<Map<String, Object>> getStatistics() {
        Map<String, Object> stats = emojiService.getEmojiStatistics();
        return Result.success(stats);
    }
}
