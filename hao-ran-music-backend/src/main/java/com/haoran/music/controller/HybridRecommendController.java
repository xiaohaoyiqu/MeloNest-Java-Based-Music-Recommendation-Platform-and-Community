package com.haoran.music.controller;

import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.result.Result;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.HybridRecommendService;
import com.haoran.music.vo.recommend.RecommendVO;
import com.haoran.music.vo.recommend.RecommendedSongVO;

import javax.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

   
          
  
                      
   
@RestController
@RequestMapping("/hybrid")
public class HybridRecommendController {

    @Resource
    private HybridRecommendService hybridRecommendService;

       
             
      
                         
                        
                   
       
    @GetMapping("/recommend")
    public Result<RecommendVO> getHybridRecommend(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestParam(defaultValue = "16") Integer limit
    ) {
        RecommendVO result = hybridRecommendService.getHybridRecommend(userId, limit);
        return Result.success(result);
    }

       
                
      
                         
                        
                     
       
    @GetMapping("/recommend-with-reason")
    public Result<List<RecommendedSongVO>> getHybridRecommendWithReason(
            @RequestAttribute("userId") Long userId,
            @RequestParam(defaultValue = "16") Integer limit
    ) {
        List<RecommendedSongVO> result = hybridRecommendService.getHybridRecommendWithReason(userId, limit);
        return Result.success(result);
    }

       
            
      
                         
                        
                   
       
    @GetMapping("/cold-start")
    public Result<RecommendVO> getColdStartRecommend(
            @RequestAttribute("userId") Long userId,
            @RequestParam(defaultValue = "16") Integer limit
    ) {
        RecommendVO result = hybridRecommendService.getColdStartRecommend(userId, limit);
        return Result.success(result);
    }

       
             
      
                         
                        
                   
       
    @GetMapping("/discovery")
    public Result<RecommendVO> getDiscoveryRecommend(
            @RequestAttribute("userId") Long userId,
            @RequestParam(defaultValue = "16") Integer limit
    ) {
        RecommendVO result = hybridRecommendService.getDiscoveryRecommend(userId, limit);
        return Result.success(result);
    }

       
            
      
                         
                                                
                        
                   
       
    @GetMapping("/mood/{mood}")
    public Result<RecommendVO> getMoodBasedRecommend(
            @RequestAttribute("userId") Long userId,
            @PathVariable String mood,
            @RequestParam(defaultValue = "16") Integer limit
    ) {
        RecommendVO result = hybridRecommendService.getMoodBasedRecommend(userId, mood, limit);
        return Result.success(result);
    }

       
               
      
                             
                
       
    @PostMapping("/refresh-profile/{userId}")
    public Result<Void> refreshUserProfile(@RequestAttribute("userId") Long userId) {
        hybridRecommendService.refreshUserRecommendProfile(userId);
        return Result.success();
    }

       
               
      
                   
       
    @GetMapping("/weights")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    public Result<Map<String, Double>> getRecommendWeights() {
        Map<String, Double> weights = hybridRecommendService.getRecommendWeights();
        return Result.success(weights);
    }

       
               
      
                            
                
       
    @PostMapping("/weights")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    public Result<Void> updateRecommendWeights(@RequestBody Map<String, Double> weights) {
        hybridRecommendService.updateRecommendWeights(weights);
        return Result.success();
    }

    @GetMapping("/models/status")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    public Result<Map<String, Object>> getModelStatus() {
        return Result.success(hybridRecommendService.getModelStatus());
    }

    @PostMapping("/models/reload")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    public Result<Map<String, Object>> reloadModels(@RequestAttribute("userId") Long operatorId) {
        return Result.success(hybridRecommendService.reloadModels(operatorId));
    }
}
