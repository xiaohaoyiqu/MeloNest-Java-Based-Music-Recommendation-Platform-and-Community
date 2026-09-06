package com.haoran.music.controller;

import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.context.UserContext;
import com.haoran.music.common.result.Result;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.UserBadgeService;
import com.haoran.music.vo.badge.UserBadgeVO;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;






@RestController
@RequestMapping("/user")
public class UserBadgeController {

    @Resource
    private UserBadgeService userBadgeService;

    @ApiLog("获取用户徽章列表")
    @GetMapping("/badge/list/{userId}")
    public Result<List<UserBadgeVO>> getUserBadges(@PathVariable("userId") Long userId) {
        List<UserBadgeVO> badges = userBadgeService.getPublicUserBadgeVOList(userId);
        return Result.success(badges);
    }

    @ApiLog("获取当前用户徽章")
    @GetMapping("/badge/my")
    public Result<List<UserBadgeVO>> getMyBadges(@RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        List<UserBadgeVO> badges = userBadgeService.getUserBadgeVOList(userId);
        return Result.success(badges);
    }

    @ApiLog("添加用户徽章")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/badge/add")
    public Result<Void> addUserBadge(
            @RequestParam("targetUserId") Long targetUserId,
            @RequestParam("badgeType") String badgeType,
            @RequestParam(value = "days", required = false) Integer days,
            @RequestParam("requestId") String requestId,
            @RequestParam("reason") String reason) {
        userBadgeService.grantBadgeByRule(targetUserId, badgeType, days,
                UserContext.getCurrentUserId(), requestId, reason);
        return Result.success();
    }

    @ApiLog("移除用户徽章")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @DeleteMapping("/badge/remove")
    public Result<Void> removeUserBadge(
            @RequestParam("targetUserId") Long targetUserId,
            @RequestParam("badgeType") String badgeType,
            @RequestParam("requestId") String requestId,
            @RequestParam("reason") String reason) {
        userBadgeService.revokeBadge(targetUserId, badgeType,
                UserContext.getCurrentUserId(), requestId, reason);
        return Result.success();
    }

    @ApiLog("计算用户成就徽章")
    @PostMapping("/badge/calculate/{userId}")
    public Result<List<UserBadgeVO>> calculateBadges(@PathVariable("userId") Long requestedUserId) {
        Long currentUserId = UserContext.getCurrentUserId();
        if (currentUserId == null) {
            return Result.error(401, "请先登录");
        }
        if (!currentUserId.equals(requestedUserId)) {
            return Result.error(403, "只能计算当前用户的成就徽章");
        }
        userBadgeService.calculateAndUpdateAchievementBadges(currentUserId);
        List<UserBadgeVO> badges = userBadgeService.getUserBadgeVOList(currentUserId);
        return Result.success(badges);
    }

    @ApiLog("获取可用徽章列表")
    @GetMapping("/badge/available")
    public Result<List<UserBadgeVO>> getAvailableBadges() {
        List<UserBadgeVO> badges = userBadgeService.getAvailableBadges();
        return Result.success(badges);
    }

    @ApiLog("设置徽章佩戴状态")
    @PutMapping("/badge/equip/{badgeId}")
    public Result<Void> setBadgeEquip(
            @PathVariable("badgeId") Long badgeId,
            @RequestParam("equip") Boolean equip,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        userBadgeService.setBadgeEquip(userId, badgeId, equip);
        return Result.success();
    }
}
