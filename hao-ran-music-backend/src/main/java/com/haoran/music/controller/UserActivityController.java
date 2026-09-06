package com.haoran.music.controller;

import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.context.UserContext;
import com.haoran.music.common.dto.PageResult;
import com.haoran.music.common.result.Result;
import com.haoran.music.service.UserActivityService;
import com.haoran.music.service.UserActivityQuickService;
import com.haoran.music.service.PermissionService;
import com.haoran.music.vo.user.UserActivityVO;
import com.haoran.music.enums.UserRole;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;





@RestController
@RequestMapping("/user")
public class UserActivityController {

    @Resource
    private UserActivityService userActivityService;

    @Resource
    private UserActivityQuickService userActivityQuickService;

    @Resource
    private PermissionService permissionService;










    @ApiLog("获取用户动态时间轴")
    @GetMapping("/{userId}/activities")
    public Result<PageResult<UserActivityVO>> getUserActivities(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "all") String type,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        if (!canViewPrivateActivities(userId)) {
            return Result.error(403, "最近动态仅本人可见");
        }
        PageResult<UserActivityVO> result = userActivityService.getUserActivities(userId, type, page, size);
        return Result.success(result);
    }







    @ApiLog("获取用户动态统计")
    @GetMapping("/{userId}/activities/stats")
    @SuppressWarnings("unchecked")
    public Result<UserActivityStatsVO> getUserActivityStats(@PathVariable Long userId) {
        if (!canViewPrivateActivities(userId)) {
            return Result.error(403, "动态统计仅本人可见");
        }
        Object statsObj = userActivityService.getUserActivityStats(userId);
        UserActivityStatsVO result = new UserActivityStatsVO();

        if (statsObj instanceof UserActivityStatsVO) {
            return Result.success((UserActivityStatsVO) statsObj);
        }


        if (statsObj instanceof java.util.Map) {
            java.util.Map<String, Object> statsMap = (java.util.Map<String, Object>) statsObj;
            result.setPostCount(getIntValue(statsMap, "postCount"));
            result.setCommentCount(getIntValue(statsMap, "commentCount"));
            result.setLikeCount(getIntValue(statsMap, "likeCount"));
            result.setFavoriteCount(getIntValue(statsMap, "favoriteCount"));
            result.setShareCount(getIntValue(statsMap, "shareCount"));
        }

        return Result.success(result);
    }




    private Integer getIntValue(java.util.Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return 0;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Long) {
            return ((Long) value).intValue();
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    private boolean canViewPrivateActivities(Long targetUserId) {
        Long currentUserId = UserContext.getCurrentUserId();
        return currentUserId != null && (currentUserId.equals(targetUserId) || permissionService.isAdmin(currentUserId));
    }




    @lombok.Data
    public static class UserActivityStatsVO {

        private Integer postCount;

        private Integer commentCount;

        private Integer likeCount;

        private Integer favoriteCount;

        private Integer shareCount;
    }









    @ApiLog("获取活跃度评分")
    @GetMapping("/activity-quick/score")
    public Result<Integer> getActivityScore() {
        Long userId = UserContext.getCurrentUserId();
        Integer score = userActivityQuickService.getActivityScore(userId);
        return Result.success(score);
    }







    @ApiLog("判断是否活跃用户")
    @GetMapping("/activity-quick/is-active")
    public Result<Boolean> isActiveUser() {
        Long userId = UserContext.getCurrentUserId();
        Boolean isActive = userActivityQuickService.isActiveUser(userId);
        return Result.success(isActive);
    }










    @ApiLog("获取活跃等级")
    @GetMapping("/activity-quick/level")
    public Result<String> getActivityLevel() {
        Long userId = UserContext.getCurrentUserId();
        String level = userActivityQuickService.getActivityLevel(userId);
        return Result.successData(level);
    }







    @ApiLog("获取活跃度详情")
    @GetMapping("/activity-quick/detail")
    public Result<Map<String, Object>> getActivityDetail() {
        Long userId = UserContext.getCurrentUserId();
        Map<String, Object> detail = userActivityQuickService.getActivityDetail(userId);
        return Result.success(detail);
    }






    @ApiLog("获取签到统计")
    @GetMapping("/activity-quick/checkin-stats")
    public Result<Map<String, Object>> getCheckinStatistics() {
        Long userId = UserContext.getCurrentUserId();
        Map<String, Object> stats = userActivityQuickService.getCheckinStatistics(userId);
        return Result.success(stats);
    }







    @ApiLog("批量获取活跃度评分")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/activity-quick/batch-score")
    public Result<Map<Long, Integer>> batchGetActivityScore(@RequestBody List<Long> userIds) {
        if (userIds == null || userIds.isEmpty() || userIds.size() > 100
                || userIds.stream().anyMatch(id -> id == null || id <= 0)) {
            return Result.error(400, "用户ID列表应包含1至100个合法ID");
        }
        List<Long> uniqueUserIds = new java.util.ArrayList<>(new LinkedHashSet<>(userIds));
        Map<Long, Integer> scores = userActivityQuickService.batchGetActivityScore(uniqueUserIds);
        return Result.success(scores);
    }







    @ApiLog("获取社交行为统计")
    @GetMapping("/activity-quick/social-stats")
    public Result<Map<String, Object>> getSocialStatistics() {
        Long userId = UserContext.getCurrentUserId();
        Map<String, Object> stats = userActivityQuickService.getSocialStatistics(userId);
        return Result.success(stats);
    }







    @ApiLog("刷新活跃度缓存")
    @PostMapping("/activity-quick/refresh-cache")
    public Result<Boolean> refreshActivityCache() {
        Long userId = UserContext.getCurrentUserId();
        Boolean result = userActivityQuickService.refreshActivityCache(userId);
        return Result.success(result);
    }
}
