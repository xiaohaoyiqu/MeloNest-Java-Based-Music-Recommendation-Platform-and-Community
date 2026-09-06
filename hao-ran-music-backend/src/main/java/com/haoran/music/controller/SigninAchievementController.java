   
                      
                              
   

package com.haoran.music.controller;

import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.result.Result;
import com.haoran.music.service.SigninAchievementService;
import com.haoran.music.service.UserCheckinService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

   
                 
              
   
@RestController
@RequestMapping("/user/signin-achievement")
public class SigninAchievementController {

    @Resource
    private SigninAchievementService signinAchievementService;

    @Resource
    private UserCheckinService userCheckinService;

       
                 
       
    @ApiLog("获取签到成就列表")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> getAchievementList(
            @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
                     
        int continuousDays = 0;
        try {
            Integer days = userCheckinService.getContinuousDays(userId);
            if (days != null) {
                continuousDays = days;
            }
        } catch (Exception e) {
                         
            continuousDays = 0;
        }
        List<Map<String, Object>> achievements = signinAchievementService.getUserAchievementProgress(userId, continuousDays);
        return Result.success(achievements);
    }

       
               
       
    @ApiLog("领取签到成就奖励")
    @PostMapping("/claim/{achievementId}")
    public Result<Map<String, Object>> claimReward(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @PathVariable Long achievementId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        Integer continuousDays = userCheckinService.getContinuousDays(userId);
        Map<String, Object> result = signinAchievementService.claimAchievementReward(
                userId, achievementId, continuousDays == null ? 0 : continuousDays);
        if ((Boolean) result.get("success")) {
            return Result.success(result);
        } else {
            return Result.error(500, (String) result.get("message"));
        }
    }
}
