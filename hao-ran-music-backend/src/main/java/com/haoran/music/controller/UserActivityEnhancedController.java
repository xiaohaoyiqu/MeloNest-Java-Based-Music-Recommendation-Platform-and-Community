package com.haoran.music.controller;

import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.context.UserContext;
import com.haoran.music.common.result.Result;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.ActivityRewardService;
import com.haoran.music.service.UserActivityEnhancedService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;







@RestController
@RequestMapping("/user/activity-enhanced")
public class UserActivityEnhancedController {

    @Resource
    private UserActivityEnhancedService userActivityEnhancedService;

    @Resource
    private ActivityRewardService activityRewardService;









    @ApiLog("获取综合活跃度评分")
    @GetMapping("/score")
    public Result<Integer> getEnhancedActivityScore() {
        Long userId = UserContext.getCurrentUserId();
        Integer score = userActivityEnhancedService.getEnhancedActivityScore(userId);
        return Result.success(score);
    }







    @ApiLog("获取活跃度维度详情")
    @GetMapping("/detail")
    public Result<Map<String, Object>> getActivityDimensionDetail() {
        Long userId = UserContext.getCurrentUserId();
        Map<String, Object> detail = userActivityEnhancedService.getActivityDimensionDetail(userId);
        return Result.success(detail);
    }








    @ApiLog("获取签到活跃度")
    @GetMapping("/checkin-score")
    public Result<Integer> getCheckinActivityScore() {
        Long userId = UserContext.getCurrentUserId();
        Integer score = userActivityEnhancedService.getCheckinActivityScore(userId);
        return Result.success(score);
    }






    @ApiLog("获取社交活跃度")
    @GetMapping("/social-score")
    public Result<Integer> getSocialActivityScore() {
        Long userId = UserContext.getCurrentUserId();
        Integer score = userActivityEnhancedService.getSocialActivityScore(userId);
        return Result.success(score);
    }






    @ApiLog("获取消费活跃度")
    @GetMapping("/consumption-score")
    public Result<Integer> getConsumptionActivityScore() {
        Long userId = UserContext.getCurrentUserId();
        Integer score = userActivityEnhancedService.getConsumptionActivityScore(userId);
        return Result.success(score);
    }






    @ApiLog("获取创作活跃度")
    @GetMapping("/creation-score")
    public Result<Integer> getCreationActivityScore() {
        Long userId = UserContext.getCurrentUserId();
        Integer score = userActivityEnhancedService.getCreationActivityScore(userId);
        return Result.success(score);
    }






    @ApiLog("获取内容活跃度")
    @GetMapping("/content-score")
    public Result<Integer> getContentActivityScore() {
        Long userId = UserContext.getCurrentUserId();
        Integer score = userActivityEnhancedService.getContentActivityScore(userId);
        return Result.success(score);
    }










    @ApiLog("检查行为异常")
    @GetMapping("/check-abnormal")
    public Result<Map<String, Object>> checkBehaviorAbnormal(
            @RequestParam String behaviorType) {
        Long userId = UserContext.getCurrentUserId();
        Map<String, Object> result = userActivityEnhancedService.checkBehaviorAbnormal(userId, behaviorType);
        return Result.success(result);
    }








    @ApiLog("检查频率限制")
    @GetMapping("/check-rate-limit")
    public Result<Map<String, Object>> checkRateLimit(
            @RequestParam String behaviorType) {
        Long userId = UserContext.getCurrentUserId();
        Map<String, Object> result = userActivityEnhancedService.checkRateLimit(userId, behaviorType);
        return Result.success(result);
    }









    @ApiLog("计算行为得分")
    @GetMapping("/calculate-score")
    public Result<Double> calculateBehaviorScore(
            @RequestParam String behaviorType,
            @RequestParam Double baseScore) {
        Long userId = UserContext.getCurrentUserId();
        Double score = userActivityEnhancedService.calculateBehaviorScore(userId, behaviorType, baseScore);
        return Result.success(score);
    }









    @ApiLog("获取行为分析报告")
    @GetMapping("/behavior-analysis")
    public Result<Map<String, Object>> getUserBehaviorAnalysis() {
        Long userId = UserContext.getCurrentUserId();
        Map<String, Object> analysis = userActivityEnhancedService.getUserBehaviorAnalysis(userId);
        return Result.success(analysis);
    }






    @ApiLog("获取信用分层权重")
    @GetMapping("/credit-weight")
    public Result<Double> getCreditWeight() {
        Long userId = UserContext.getCurrentUserId();
        Double weight = userActivityEnhancedService.getCreditWeight(userId);
        return Result.success(weight);
    }










    @ApiLog("获取活跃度排行榜")
    @GetMapping("/ranking")
    public Result<List<Map<String, Object>>> getActivityRanking(
            @RequestParam(defaultValue = "all") String dimension,
            @RequestParam(defaultValue = "50") Integer limit) {
        List<Map<String, Object>> ranking = userActivityEnhancedService.getActivityRanking(dimension, limit);
        return Result.success(ranking);
    }







    @ApiLog("获取用户排名")
    @GetMapping("/my-ranking")
    public Result<Integer> getUserRanking(
            @RequestParam(defaultValue = "all") String dimension) {
        Long userId = UserContext.getCurrentUserId();
        Integer ranking = userActivityEnhancedService.getUserRanking(userId, dimension);
        return Result.success(ranking);
    }









    @ApiLog("获取活跃度奖励")
    @GetMapping("/rewards")
    public Result<Map<String, Object>> getActivityRewards() {
        Long userId = UserContext.getCurrentUserId();
        Map<String, Object> rewards = userActivityEnhancedService.getActivityRewards(userId);
        return Result.success(rewards);
    }







    @ApiLog("领取活跃度奖励")
    @PostMapping("/claim-reward")
    public Result<Boolean> claimActivityReward(@RequestParam Long rewardId) {
        Long userId = UserContext.getCurrentUserId();
        Boolean result = activityRewardService.claimReward(userId);
        return Result.success(result);
    }






    @ApiLog("获取活跃度等级进度")
    @GetMapping("/level-progress")
    public Result<Map<String, Object>> getActivityLevelProgress() {
        Long userId = UserContext.getCurrentUserId();
        Map<String, Object> progress = userActivityEnhancedService.getActivityLevelProgress(userId);
        return Result.success(progress);
    }










    @ApiLog("标记可疑用户")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/admin/mark-suspicious")
    public Result<Boolean> markSuspiciousUser(
            @RequestParam Long userId,
            @RequestParam String reason) {
        Boolean result = userActivityEnhancedService.markSuspiciousUser(userId, reason);
        return Result.success(result);
    }







    @ApiLog("取消可疑用户标记")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/admin/unmark-suspicious")
    public Result<Boolean> unmarkSuspiciousUser(@RequestParam Long userId) {
        Boolean result = userActivityEnhancedService.unmarkSuspiciousUser(userId);
        return Result.success(result);
    }







    @ApiLog("获取指定用户行为分析")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/admin/behavior-analysis/{userId}")
    public Result<Map<String, Object>> getUserBehaviorAnalysisAdmin(
            @PathVariable Long userId) {
        Map<String, Object> analysis = userActivityEnhancedService.getUserBehaviorAnalysis(userId);
        return Result.success(analysis);
    }







    @ApiLog("获取指定用户活跃度详情")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/admin/detail/{userId}")
    public Result<Map<String, Object>> getActivityDimensionDetailAdmin(
            @PathVariable Long userId) {
        Map<String, Object> detail = userActivityEnhancedService.getActivityDimensionDetail(userId);
        return Result.success(detail);
    }
}
