   
                      
                       
   

package com.haoran.music.controller;

import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.result.Result;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.CacheWarmupService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

   
          
   
@RestController
@RequestMapping("/cache")
@RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
public class CacheWarmupController {

    private final CacheWarmupService cacheWarmupService;

    public CacheWarmupController(CacheWarmupService cacheWarmupService) {
        this.cacheWarmupService = cacheWarmupService;
    }

       
               
      
                   
       
    @PostMapping("/warmup")
    @ApiLog("触发缓存预热")
    public Result<Map<String, Object>> warmUpAll() {
        Map<String, Object> result = cacheWarmupService.warmUpAll();
        return Result.success(result);
    }

       
               
      
                   
       
    @PostMapping("/warmup/hot-songs")
    @ApiLog("预热热门歌曲缓存")
    public Result<Integer> warmUpHotSongs() {
        int count = cacheWarmupService.warmUpHotSongs();
        return Result.success(count);
    }

       
               
      
                   
       
    @PostMapping("/warmup/hot-albums")
    @ApiLog("预热热门专辑缓存")
    public Result<Integer> warmUpHotAlbums() {
        int count = cacheWarmupService.warmUpHotAlbums();
        return Result.success(count);
    }

       
               
      
                   
       
    @PostMapping("/warmup/hot-artists")
    @ApiLog("预热热门歌手缓存")
    public Result<Integer> warmUpHotArtists() {
        int count = cacheWarmupService.warmUpHotArtists();
        return Result.success(count);
    }

       
               
      
                   
       
    @PostMapping("/warmup/recommendations")
    @ApiLog("预热推荐数据缓存")
    public Result<Integer> warmUpRecommendations() {
        int count = cacheWarmupService.warmUpRecommendations();
        return Result.success(count);
    }

       
              
      
                   
       
    @PostMapping("/warmup/rankings")
    @ApiLog("预热排行榜缓存")
    public Result<Integer> warmUpRankings() {
        int count = cacheWarmupService.warmUpRankings();
        return Result.success(count);
    }

       
             
      
                   
       
    @DeleteMapping("/clear")
    @ApiLog("清除所有缓存")
    public Result<Map<String, Object>> clearAllCache() {
        Map<String, Object> result = cacheWarmupService.clearAllCache();
        return Result.success(result);
    }

       
             
      
                   
       
    @GetMapping("/status")
    @ApiLog("获取缓存状态")
    public Result<Map<String, Object>> getCacheStatus() {
        Map<String, Object> status = cacheWarmupService.getCacheStatus();
        return Result.success(status);
    }
}
