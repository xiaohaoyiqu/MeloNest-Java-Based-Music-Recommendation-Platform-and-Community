package com.haoran.music.controller;

import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.result.Result;
import com.haoran.music.service.PersonalizedRankingService;

import javax.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

   
            
  
                         
  
                      
   
@RestController
@RequestMapping("/ranking/personal")
public class PersonalizedRankingController {

    @Resource
    private PersonalizedRankingService personalizedRankingService;

       
               
      
                         
                             
                                 
       
    @ApiLog("获取个性化热歌榜")
    @GetMapping("/songs")
    public Result<Map<String, Object>> getPersonalizedHotSongs(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestParam(defaultValue = "50") Integer limit) {
        Map<String, Object> result = personalizedRankingService.getPersonalizedHotSongs(userId, limit);
        return Result.success(result);
    }

       
                
      
                         
                              
                                   
       
    @ApiLog("获取个性化创作者榜")
    @GetMapping("/creators")
    public Result<Map<String, Object>> getPersonalizedCreators(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestParam(defaultValue = "20") Integer limit) {
        Map<String, Object> result = personalizedRankingService.getPersonalizedCreators(userId, limit);
        return Result.success(result);
    }

       
               
                              
      
                         
                             
                                 
       
    @ApiLog("获取个性化歌单榜")
    @GetMapping("/playlists")
    public Result<Map<String, Object>> getPersonalizedPlaylists(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestParam(defaultValue = "20") Integer limit) {
        Map<String, Object> result = personalizedRankingService.getPersonalizedPlaylists(userId, limit);
        return Result.success(result);
    }

       
                 
                     
      
                         
                                    
       
    @ApiLog("获取用户活跃时段分析")
    @GetMapping("/active-time")
    public Result<Map<String, Object>> getUserActiveTimeAnalysis(
            @RequestAttribute("userId") Long userId) {
        Map<String, Object> result = personalizedRankingService.getUserActiveTimeAnalysis(userId);
        return Result.success(result);
    }

       
                 
                                 
      
                         
                       
       
    @ApiLog("获取用户深度偏好分析")
    @GetMapping("/deep-preference")
    public Result<Map<String, Object>> getUserDeepPreferenceAnalysis(
            @RequestAttribute("userId") Long userId) {
        Map<String, Object> result = personalizedRankingService.getUserDeepPreferenceAnalysis(userId);
        return Result.success(result);
    }
}
