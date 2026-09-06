  
                      
                       
  
              
         
          
          
          
         
           
   
package com.haoran.music.controller;

import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.context.UserContext;
import com.haoran.music.common.result.Result;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.UserBadgeService;
import com.haoran.music.vo.badge.UserBadgeVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

   
          
                   
  
   
@Slf4j
@RestController
@RequestMapping("/badge")
public class BadgeController {

    @Resource
    private UserBadgeService userBadgeService;

                                                       

       
               
      
                   
       
    @ApiLog("获取我的所有徽章")
    @GetMapping("/my-badges")
    public Result<List<UserBadgeVO>> getMyBadges() {
        Long userId = UserContext.getCurrentUserId();
        List<UserBadgeVO> badges = userBadgeService.getUserBadgeVOList(userId);
        return Result.success(badges);
    }

       
                 
      
                   
       
    @ApiLog("获取徽章统计信息")
    @GetMapping("/my-stats")
    public Result<Map<String, Object>> getMyBadgeStats() {
        Long userId = UserContext.getCurrentUserId();
        Map<String, Object> stats = userBadgeService.getUserBadgeStats(userId);
        return Result.success(stats);
    }

       
                  
      
                                                                              
                   
       
    @ApiLog("获取指定分类的我的徽章")
    @GetMapping("/my-badges/category/{category}")
    public Result<List<UserBadgeVO>> getMyBadgesByCategory(@PathVariable String category) {
        Long userId = UserContext.getCurrentUserId();
        List<UserBadgeVO> badges = userBadgeService.getUserBadgesByCategory(userId, category);
        return Result.success(badges);
    }

       
                  
                             
      
                       
       
    @ApiLog("计算并更新成就徽章")
    @PostMapping("/calculate-achievements")
    public Result<List<UserBadgeVO>> calculateAchievements() {
        Long userId = UserContext.getCurrentUserId();
        userBadgeService.calculateAndUpdateAchievementBadges(userId);
        List<UserBadgeVO> badges = userBadgeService.getUserBadgeVOList(userId);
        return Result.success(badges);
    }

                                                          

       
                      
      
                     
       
    @ApiLog("获取徽章商店")
    @GetMapping("/shop")
    public Result<List<UserBadgeVO>> getBadgeShop() {
        List<UserBadgeVO> badges = userBadgeService.getAvailableBadges();
        return Result.success(badges);
    }

       
               
      
                   
       
    @ApiLog("获取徽章分类")
    @GetMapping("/categories")
    public Result<List<Map<String, Object>>> getBadgeCategories() {
        List<Map<String, Object>> categories = userBadgeService.getBadgeCategories();
        return Result.success(categories);
    }

       
                
      
                    
       
    @ApiLog("获取稀有度配置")
    @GetMapping("/rarities")
    public Result<List<Map<String, Object>>> getRarityConfigs() {
        List<Map<String, Object>> rarities = userBadgeService.getRarityConfigs();
        return Result.success(rarities);
    }

       
                  
      
                           
                   
       
    @ApiLog("获取指定分类的徽章")
    @GetMapping("/shop/category/{category}")
    public Result<List<UserBadgeVO>> getBadgesByCategory(@PathVariable String category) {
        List<UserBadgeVO> badges = userBadgeService.getBadgesByCategory(category);
        return Result.success(badges);
    }

                                                      

       
                  
      
                         
                            
                              
                   
       
    @ApiLog("颁发徽章")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/admin/award")
    public Result<Boolean> awardBadge(
            @RequestParam Long userId,
            @RequestParam String badgeType,
            @RequestParam(required = false) Integer days,
            @RequestParam String requestId,
            @RequestParam String reason) {
        Long operatorId = UserContext.getCurrentUserId();
        boolean success = userBadgeService.grantBadgeByRule(
                userId, badgeType, days, operatorId, requestId, reason);
        return Result.success(success);
    }

       
                  
      
                         
                            
                   
       
    @ApiLog("移除徽章")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @DeleteMapping("/admin/remove")
    public Result<Boolean> removeBadge(
            @RequestParam Long userId,
            @RequestParam String badgeType,
            @RequestParam String requestId,
            @RequestParam String reason) {
        Long operatorId = UserContext.getCurrentUserId();
        return Result.success(userBadgeService.revokeBadge(
                userId, badgeType, operatorId, requestId, reason));
    }

       
                       
      
                         
                   
       
    @ApiLog("获取指定用户徽章")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/admin/badges/{userId}")
    public Result<List<UserBadgeVO>> getUserBadges(@PathVariable Long userId) {
        List<UserBadgeVO> badges = userBadgeService.getUserBadgeVOList(userId);
        return Result.success(badges);
    }

       
                         
      
                         
                   
       
    @ApiLog("获取指定用户徽章统计")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/admin/stats/{userId}")
    public Result<Map<String, Object>> getUserBadgeStats(@PathVariable Long userId) {
        Map<String, Object> stats = userBadgeService.getUserBadgeStats(userId);
        return Result.success(stats);
    }

       
                        
      
                         
                       
       
    @ApiLog("批量计算用户成就徽章")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/admin/calculate/{userId}")
    public Result<List<UserBadgeVO>> calculateUserAchievements(@PathVariable Long userId) {
        userBadgeService.calculateAndUpdateAchievementBadges(userId);
        List<UserBadgeVO> badges = userBadgeService.getUserBadgeVOList(userId);
        return Result.success(badges);
    }
}
