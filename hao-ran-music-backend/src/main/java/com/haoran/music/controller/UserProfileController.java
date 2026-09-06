


package com.haoran.music.controller;

import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.aspect.RateLimit;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.Result;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.UserProfileService;
import com.haoran.music.service.PermissionService;
import com.haoran.music.service.MarketplaceService;
import com.haoran.music.service.PlaylistCollaborationService;
import com.haoran.music.service.StoreProductPolicyService;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.entity.User;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.vo.user.UserProfileVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;




@Slf4j
@RestController
@RequestMapping("/user")
public class UserProfileController {

    @Resource
    private UserProfileService userProfileService;

    @Resource
    private PermissionService permissionService;

    @Resource
    private UserMapper userMapper;

    @Resource
    private PlaylistCollaborationService playlistCollaborationService;

    @Resource
    private MarketplaceService marketplaceService;

    @Resource
    private StoreProductPolicyService storeProductPolicyService;




    @ApiLog("获取用户公开陈列")
    @GetMapping("/profile/public-showcase/{userId}")
    public Result<Map<String, Object>> getPublicShowcase(
            @PathVariable("userId") Long userId,
            @RequestAttribute(value = CommonConstants.USER_ID_KEY, required = false) Long viewerId) {
        User owner = userMapper.selectById(userId);
        boolean available = UserAccountStatusUtil.canExposePublicContent(owner);
        Map<String, Object> result = new HashMap<>();
        result.put("available", available);
        result.put("collaborativePlaylists", available
                ? playlistCollaborationService.getPublicCollaborativePlaylists(userId, 6) : java.util.Collections.emptyList());
        result.put("marketplaceItems", available
                ? marketplaceService.getPublicSellerItems(userId, viewerId, 6) : java.util.Collections.emptyList());
        result.put("storeProducts", available
                ? storeProductPolicyService.getPublicSellerProducts(userId, 6) : java.util.Collections.emptyList());
        return Result.success(result);
    }




    @ApiLog("获取用户画像")
    @GetMapping("/profile/{userId}")
    public Result<UserProfileVO> getUserProfile(
            @PathVariable("userId") Long userId,
            @RequestAttribute(value = CommonConstants.USER_ID_KEY, required = false) Long currentUserId) {
        requireSelfOrAdmin(currentUserId, userId);
        UserProfileVO profile = userProfileService.getUserProfile(userId);
        return Result.success(profile);
    }




    @ApiLog("获取用户偏好标签")
    @GetMapping("/profile/preferences/{userId}")
    public Result<Map<String, Object>> getUserPreferenceTags(
            @PathVariable("userId") Long userId,
            @RequestAttribute(value = CommonConstants.USER_ID_KEY, required = false) Long currentUserId) {
        requireSelfOrAdmin(currentUserId, userId);
        Map<String, Object> tags = userProfileService.getUserPreferenceTags(userId);
        return Result.success(tags);
    }




    @ApiLog("更新用户偏好")
    @PostMapping("/profile/preferences/update")
    public Result<Void> updatePreferences(
            @RequestAttribute(value = CommonConstants.USER_ID_KEY, required = false) Long userId,
            @RequestBody Map<String, Object> params) {
        if (userId == null) {
            return Result.error(401, "Unauthorized");
        }
        List<String> genres = ObjectUtils.castList(params.get("preferredGenres"), String.class);
        List<String> languages = ObjectUtils.castList(params.get("preferredLanguages"), String.class);
        List<String> moods = ObjectUtils.castList(params.get("preferredMoods"), String.class);
        userProfileService.updateUserPreferences(userId, genres, languages, moods);
        return Result.success();
    }




    @ApiLog("刷新用户画像")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/profile/refresh/{userId}")
    public Result<Void> refreshProfile(@PathVariable("userId") Long userId) {
        userProfileService.refreshUserProfile(userId);
        return Result.success();
    }




    @ApiLog("批量刷新用户画像")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @RateLimit(maxRequests = 4, timeWindowSeconds = 3600, operation = "batchRefreshUserProfiles",
            message = "批量刷新过于频繁，请稍后再试")
    @PostMapping("/profile/refresh/batch")
    public Result<Map<String, Object>> batchRefreshProfiles(@RequestBody List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Result.error(400, "userIds is required");
        }
        if (userIds.size() > 100) {
            return Result.error(400, "userIds cannot exceed 100 items");
        }
        if (userIds.stream().anyMatch(userId -> userId == null || userId <= 0)) {
            return Result.error(400, "userIds contains invalid id");
        }
        List<Long> distinctUserIds = new java.util.ArrayList<>(new LinkedHashSet<>(userIds));
        int successCount = 0;
        int failCount = 0;
        for (Long userId : distinctUserIds) {
            try {
                userProfileService.refreshUserProfile(userId);
                successCount++;
            } catch (Exception e) {
                failCount++;
                log.warn("event=user_profile_batch_refresh_item_failed userId={} errorType={}",
                        userId, e.getClass().getSimpleName());
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("total", distinctUserIds.size());
        result.put("successCount", successCount);
        result.put("failCount", failCount);
        return Result.success(result);
    }




    @ApiLog("刷新最近活跃用户画像")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @RateLimit(maxRequests = 1, timeWindowSeconds = 3600, operation = "refreshAllUserProfiles",
            message = "全量刷新过于频繁，请稍后再试")
    @PostMapping("/profile/refresh-all")
    public Result<String> refreshAllProfiles() {
        userProfileService.batchRefreshUserProfiles();
        return Result.success("用户画像批量刷新任务已执行");
    }




    @ApiLog("记录用户行为")
    @RateLimit(maxRequests = 120, timeWindowSeconds = 3600, operation = "recordProfileBehavior",
            message = "行为上报过于频繁，请稍后再试")
    @PostMapping("/profile/behavior/record")
    public Result<Void> recordBehavior(
            @RequestAttribute(value = CommonConstants.USER_ID_KEY, required = false) Long userId,
            @RequestBody Map<String, Object> params) {
        if (userId == null) {
            return Result.error(401, "Unauthorized");
        }
        String action = (String) params.get("action");
        Long targetId = params.get("targetId") != null ? Long.parseLong(params.get("targetId").toString()) : null;
        String metadata = (String) params.get("metadata");
        userProfileService.recordUserBehavior(userId, action, targetId, metadata);
        return Result.success();
    }




    @ApiLog("获取用户分层统计")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/profile/segment/stats")
    public Result<Map<String, Object>> getSegmentStats() {
        Map<String, Object> stats = userProfileService.getUserSegmentStats();
        return Result.success(stats);
    }




    @ApiLog("预测用户流失概率")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/profile/churn/predict/{userId}")
    public Result<Integer> predictChurn(@PathVariable("userId") Long userId) {
        Integer probability = userProfileService.predictChurnProbability(userId);
        return Result.success(probability);
    }

    private void requireSelfOrAdmin(Long currentUserId, Long targetUserId) {
        if (currentUserId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        if (currentUserId.equals(targetUserId)) {
            return;
        }
        if (!UserRole.isAdmin(permissionService.getUserRole(currentUserId))) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看其他用户的画像数据");
        }
    }
}
