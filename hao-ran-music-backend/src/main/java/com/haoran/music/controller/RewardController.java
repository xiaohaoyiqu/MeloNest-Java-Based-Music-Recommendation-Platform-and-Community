   
                      
                     
   

package com.haoran.music.controller;

import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.result.Result;
import com.haoran.music.service.RewardService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.Map;

   
        
               
   
@Slf4j
@RestController
@RequestMapping("/reward")
public class RewardController {

    private final RewardService rewardService;

    public RewardController(RewardService rewardService) {
        this.rewardService = rewardService;
    }

       
            
       
    @ApiLog("打赏创作者")
    @PostMapping("/{creatorId}")
    public Result rewardCreator(HttpServletRequest request,
                              @PathVariable Long creatorId,
                              @RequestParam BigDecimal amount,
                              @RequestParam(required = false) String message,
                              @RequestParam(required = false) Long resourceId,
                              @RequestParam(required = false) String resourceType,
                              @RequestParam(defaultValue = "false") Boolean isAnonymous) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(rewardService.rewardCreator(userId, creatorId, amount,
                message, resourceId, resourceType, isAnonymous));
    }

       
               
       
    @ApiLog("获取打赏记录")
    @GetMapping("/my")
    public Result getMyRewardRecords(HttpServletRequest request,
                                  @RequestParam(defaultValue = "1") Integer page,
                                  @RequestParam(defaultValue = "20") Integer size) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(rewardService.getMyRewardRecords(userId, page, size));
    }

       
                     
       
    @ApiLog("获取收到的打赏")
    @GetMapping("/received")
    public Result getReceivedRewards(HttpServletRequest request,
                                   @RequestParam(defaultValue = "1") Integer page,
                                   @RequestParam(defaultValue = "20") Integer size) {
        Long creatorId = (Long) request.getAttribute("userId");
        return Result.success(rewardService.getReceivedRewards(creatorId, page, size));
    }

       
                  
       
    @ApiLog("获取打赏统计")
    @GetMapping("/statistics")
    public Result getRewardStatistics(HttpServletRequest request) {
        Long creatorId = (Long) request.getAttribute("userId");
        return Result.success(rewardService.getRewardStatistics(creatorId));
    }

       
             
       
    @ApiLog("获取资源打赏")
    @GetMapping("/resource")
    public Result getResourceRewards(@RequestParam Long resourceId,
                                   @RequestParam String resourceType,
                                   @RequestParam(defaultValue = "1") Integer page,
                                   @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(rewardService.getResourceRewards(resourceId, resourceType, page, size));
    }

       
           
       
    @ApiLog("取消打赏")
    @PostMapping("/{rewardId}/cancel")
    public Result cancelReward(HttpServletRequest request,
                             @PathVariable Long rewardId) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(rewardService.cancelReward(rewardId, userId));
    }

       
             
       
    @ApiLog("获取打赏详情")
    @GetMapping("/{rewardId}")
    public Result getRewardDetail(HttpServletRequest request,
                                  @PathVariable Long rewardId) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(rewardService.getRewardDetail(rewardId, userId));
    }
}
