   
                      
   
package com.haoran.music.controller;

import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.result.Result;
import com.haoran.music.dto.user.VipExchangeRequest;
import com.haoran.music.service.UserActivityPointsService;
import com.haoran.music.service.UserCheckinService;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

   
                                               
   
@RestController
@RequestMapping("/user")
public class UserCheckinController {

    @Resource
    private UserCheckinService checkinService;

    @Resource
    private UserActivityPointsService activityPointsService;

    @ApiLog("用户签到")
    @PostMapping("/checkin")
    public Result<Map<String, Object>> checkin(@RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(checkinService.checkin(userId));
    }

    @ApiLog("检查今日是否已签到")
    @GetMapping("/checkin/status")
    public Result<Boolean> hasCheckedInToday(@RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.success(false);
        }
        return Result.success(checkinService.hasCheckedInToday(userId));
    }

    @ApiLog("获取签到统计信息")
    @GetMapping("/checkin/stats")
    public Result<Map<String, Object>> getCheckinStats(@RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(checkinService.getCheckinStats(userId));
    }

    @ApiLog("获取本月签到日历")
    @GetMapping("/checkin/calendar")
    public Result<List<LocalDate>> getMonthCheckinDates(
            @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(checkinService.getMonthCheckinDates(userId));
    }

    @ApiLog("获取用户活跃值")
    @GetMapping("/checkin/points")
    public Result<Integer> getUserPoints(@RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(activityPointsService.getUserTotalPoints(userId));
    }

    @ApiLog("用户补签")
    @PostMapping("/checkin/makeup")
    public Result<Map<String, Object>> makeupCheckin(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(checkinService.makeupCheckin(userId, date));
    }

    @ApiLog("获取VIP活跃值兑换套餐")
    @GetMapping("/checkin/vip-packages")
    public Result<List<Map<String, Object>>> getVipExchangePackages() {
        return Result.success(activityPointsService.getVipExchangePackages());
    }

    @ApiLog("活跃值兑换VIP")
    @PostMapping("/checkin/redeem-vip")
    public Result<Map<String, Object>> redeemVip(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestBody VipExchangeRequest request) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        if (request == null) {
            return Result.error(400, "兑换请求不能为空");
        }
        return Result.success(activityPointsService.redeemVip(
                userId, request.getPackageCode(), request.getRequestId()));
    }
}
