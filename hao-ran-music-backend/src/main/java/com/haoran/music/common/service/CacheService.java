package com.haoran.music.common.service;

import com.haoran.music.common.config.CacheTtlConfig;
import com.haoran.music.common.util.CacheHelper;
import com.haoran.music.common.util.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

   
        
                            
  
                      
   
@Slf4j
@Service
public class CacheService {

    @Resource
    private RedisUtils redisUtils;

    @Resource
    private CacheTtlConfig cacheTtlConfig;


       
              
       
    public static final String MV_CACHE_PREFIX = "mv:info:";
    public static final String MV_FAVORITE_PREFIX = "mv:favorite:";
    public static final String MV_LIKE_PREFIX = "mv:like:";
    public static final String PLAY_COUNT_PREFIX = "play:count:";

       
           
                      
       
    @PostConstruct
    public void warmUpCache() {
        log.info("开始缓存预热...");

                       
                               

        log.info("缓存预热完成");
    }

       
             
      
                        
       
    public void clearMVCache(Long mvId) {
        String cacheKey = MV_CACHE_PREFIX + mvId;
        redisUtils.delete(cacheKey);
        log.debug("清除MV缓存: mvId={}", mvId);
    }

       
             
      
                          
       
    public void clearSongCache(Long songId) {
        String cacheKey = "song:info:" + songId;
        redisUtils.delete(cacheKey);
        log.debug("清除歌曲缓存: songId={}", songId);
    }

       
               
      
                          
       
    public void clearUserRecommendCache(Long userId) {
        String pattern = "recommend:*:" + userId;
        long deleted = CacheHelper.deleteByPattern(redisUtils, pattern);
        log.debug("清除用户推荐缓存: userId={}, count={}", userId, deleted);
    }

       
               
      
                          
       
    public void clearPlayCountCache(Long songId) {
        String cacheKey = PLAY_COUNT_PREFIX + songId;
        redisUtils.delete(cacheKey);
    }

       
             
      
                          
                    
       
    public long incrementPlayCount(Long songId) {
        String cacheKey = PLAY_COUNT_PREFIX + songId;
        return redisUtils.increment(cacheKey);
    }

       
             
      
                          
                   
       
    public long getPlayCount(Long songId) {
        return CacheHelper.getCounter(redisUtils, PLAY_COUNT_PREFIX + songId);
    }

       
                 
      
                      
       
    public long getMVCacheExpire() {
        return cacheTtlConfig.getMv().getExpire();
    }

       
                 
      
                      
       
    public long getSongCacheExpire() {
        return cacheTtlConfig.getSong().getExpire();
    }

       
                 
      
                      
       
    public long getRecommendCacheExpire() {
        return cacheTtlConfig.getRecommend().getExpire();
    }

       
                   
      
                      
       
    public long getHotCacheExpire() {
        return cacheTtlConfig.getHot().getExpire();
    }

       
                 
      
                      
       
    public long getNullCacheExpire() {
        return cacheTtlConfig.getNullValue().getExpire();
    }

       
                 
      
                           
       
    public void clearMVBatches(java.util.Set<Long> mvIds) {
        if (com.haoran.music.common.util.ObjectUtils.isEmpty(mvIds)) {
            return;
        }

        java.util.Set<String> keys = new java.util.HashSet<>();
        for (Long mvId : mvIds) {
            keys.add(MV_CACHE_PREFIX + mvId);
        }

        redisUtils.delete(keys);
        log.debug("批量清除MV缓存: count={}", mvIds.size());
    }

       
                 
      
                             
       
    public void clearSongBatches(java.util.Set<Long> songIds) {
        if (com.haoran.music.common.util.ObjectUtils.isEmpty(songIds)) {
            return;
        }

        java.util.Set<String> keys = new java.util.HashSet<>();
        for (Long songId : songIds) {
            keys.add("song:info:" + songId);
        }

        redisUtils.delete(keys);
        log.debug("批量清除歌曲缓存: count={}", songIds.size());
    }

       
              
                        
      
                             
       
    public double getCacheHitRate() {
                         
                    
        return 85.0;              
    }

       
                    
      
                     
       
    public String getMemoryInfo() {
        try {
            Long usedMemory = redisUtils.getExpire("memory_info");
            return "Redis memory info not available";
        } catch (Exception e) {
            return "获取内存信息失败: " + e.getMessage();
        }
    }
}
