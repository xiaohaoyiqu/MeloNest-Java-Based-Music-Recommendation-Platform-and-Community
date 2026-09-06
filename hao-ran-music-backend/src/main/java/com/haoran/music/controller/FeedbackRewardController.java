   
                      
                       
   

package com.haoran.music.controller;

import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.result.Result;
import com.haoran.music.service.FeedbackRewardService;
import com.haoran.music.enums.UserRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

   
          
                     
   
@Slf4j
@RestController
@RequestMapping("/feedback-reward")
public class FeedbackRewardController {

    private final FeedbackRewardService feedbackRewardService;

    public FeedbackRewardController(FeedbackRewardService feedbackRewardService) {
        this.feedbackRewardService = feedbackRewardService;
    }

       
                  
       
    @ApiLog("创建反馈奖励")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/create")
    public Result createReward(@RequestParam Long feedbackId,
                              @RequestParam String rewardLevel,
                              @RequestParam(required = false) String rewardDescription) {
        return Result.success(feedbackRewardService.createReward(feedbackId, rewardLevel, rewardDescription));
    }

       
                
       
    @ApiLog("发放反馈奖励")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/{id}/grant")
    public Result grantReward(@PathVariable Long id,
                             @RequestAttribute(value = "userId", required = false) Long grantorId) {
        if (grantorId == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(feedbackRewardService.grantReward(id, grantorId));
    }

       
                
       
    @ApiLog("取消反馈奖励")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/{id}/cancel")
    public Result cancelReward(@PathVariable Long id,
                              @RequestParam String cancelReason) {
        return Result.success(feedbackRewardService.cancelReward(id, cancelReason));
    }

       
               
       
    @ApiLog("获取我的奖励列表")
    @GetMapping("/my-rewards")
    public Result getMyRewards(HttpServletRequest request,
                              @RequestParam(defaultValue = "1") Integer page,
                              @RequestParam(defaultValue = "20") Integer size) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(feedbackRewardService.getMyRewards(userId, page, size));
    }

       
                     
       
    @ApiLog("获取待发放奖励")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/pending")
    public Result getPendingRewards(@RequestParam(defaultValue = "1") Integer page,
                                    @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(feedbackRewardService.getPendingRewards(page, size));
    }

       
               
       
    @ApiLog("获取奖励等级配置")
    @GetMapping("/reward-levels")
    public Result getRewardLevels() {
        return Result.success(feedbackRewardService.getRewardLevels());
    }

       
             
       
    @ApiLog("获取奖励详情")
    @GetMapping("/{id}")
    public Result getRewardDetail(@PathVariable Long id) {
        return Result.success(feedbackRewardService.getRewardDetail(id));
    }

       
                  
       
    @ApiLog("批量发放奖励")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/batch-grant")
    public Result batchGrantRewards(@RequestParam Long[] ids,
                                   @RequestAttribute(value = "userId", required = false) Long grantorId) {
        if (grantorId == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(feedbackRewardService.batchGrantRewards(ids, grantorId));
    }

       
             
       
    @ApiLog("获取奖励统计")
    @GetMapping("/statistics")
    public Result getRewardStatistics(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(feedbackRewardService.getRewardStatistics(userId));
    }
}
