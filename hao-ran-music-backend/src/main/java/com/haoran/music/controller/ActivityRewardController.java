package com.haoran.music.controller;

import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.context.UserContext;
import com.haoran.music.common.result.Result;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.ActivityRewardService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;







@RestController
@RequestMapping("/user/activity-reward")
public class ActivityRewardController {

    @Resource
    private ActivityRewardService activityRewardService;






    @ApiLog("获取活跃度奖励")
    @GetMapping("/current")
    public Result<Map<String, Object>> getCurrentReward() {
        Long userId = UserContext.getCurrentUserId();
        Map<String, Object> reward = activityRewardService.getCurrentReward(userId);
        return Result.success(reward);
    }







    @ApiLog("领取活跃度奖励")
    @PostMapping("/claim")
    public Result<Boolean> claimReward() {
        Long userId = UserContext.getCurrentUserId();
        Boolean success = activityRewardService.claimReward(userId);
        return Result.success(success);
    }






    @ApiLog("检查奖励领取状态")
    @GetMapping("/check-claimed")
    public Result<Boolean> isClaimedThisMonth() {
        Long userId = UserContext.getCurrentUserId();
        Boolean claimed = activityRewardService.isClaimedThisMonth(userId);
        return Result.success(claimed);
    }








    @ApiLog("获取奖励领取历史")
    @GetMapping("/history")
    public Result<Map<String, Object>> getClaimHistory(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        Long userId = UserContext.getCurrentUserId();
        Map<String, Object> history = activityRewardService.getClaimHistory(userId, page, size);
        return Result.success(history);
    }






    @ApiLog("获取奖励配置")
    @GetMapping("/config")
    public Result<Map<String, Integer>> getRewardConfig() {
        Map<String, Integer> config = activityRewardService.getRewardConfig();
        return Result.success(config);
    }







    @ApiLog("计算奖励积分")
    @GetMapping("/calculate")
    public Result<Integer> calculateRewardPoints(@RequestParam Integer activityScore) {
        Integer points = activityRewardService.calculateRewardPoints(activityScore);
        return Result.success(points);
    }









    @ApiLog("获取指定用户奖励信息")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/admin/current/{userId}")
    public Result<Map<String, Object>> getCurrentRewardAdmin(@PathVariable Long userId) {
        Map<String, Object> reward = activityRewardService.getCurrentReward(userId);
        return Result.success(reward);
    }









    @ApiLog("获取指定用户奖励历史")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/admin/history/{userId}")
    public Result<Map<String, Object>> getClaimHistoryAdmin(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        Map<String, Object> history = activityRewardService.getClaimHistory(userId, page, size);
        return Result.success(history);
    }
}
