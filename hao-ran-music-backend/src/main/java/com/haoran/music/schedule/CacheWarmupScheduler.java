package com.haoran.music.schedule;

import com.haoran.music.service.CacheWarmupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;







@Slf4j
@Component
public class CacheWarmupScheduler {

    @Resource
    private CacheWarmupService cacheWarmupService;





    @Scheduled(cron = "${schedule.cache-warmup.hourly-cron}")
    public void warmUpCacheHourly() {
        try {
            log.info("开始执行定时缓存预热");
            Map<String, Object> result = cacheWarmupService.warmUpAll();
            log.info("定时缓存预热完成: {}", result);
        } catch (Exception e) {
            log.error("event=scheduled_cache_warmup_failed errorType={}", e.getClass().getSimpleName());
        }
    }





    @Scheduled(cron = "${schedule.cache-warmup.rankings-cron}")
    public void warmUpRankings() {
        try {
            log.info("开始执行排行榜缓存预热");
            int count = cacheWarmupService.warmUpRankings();
            log.info("排行榜缓存预热完成: count={}", count);
        } catch (Exception e) {
            log.error("event=ranking_cache_warmup_failed errorType={}", e.getClass().getSimpleName());
        }
    }




    @Scheduled(cron = "${schedule.cache-warmup.daily-cron}")
    public void warmUpCacheDaily() {
        try {
            log.info("开始执行每日全面缓存预热");
            Map<String, Object> result = cacheWarmupService.warmUpAll();


            log.info("每日全面缓存预热完成 - 热门歌曲: {}, 热门专辑: {}, 热门歌手: {}, 热门歌单: {}, 总计: {}",
                    result.get("hotSongs"),
                    result.get("hotAlbums"),
                    result.get("hotArtists"),
                    result.get("playlists"),
                    result.get("totalItems"));
        } catch (Exception e) {
            log.error("event=daily_full_cache_warmup_failed errorType={}", e.getClass().getSimpleName());
        }
    }




    @Scheduled(cron = "${schedule.cache-warmup.clean-expired-cron}")
    public void cleanExpiredCache() {
        try {
            log.info("开始执行每周缓存清理");
            Map<String, Object> result = cacheWarmupService.clearAllCache();
            log.info("每周缓存清理完成: {}", result);
        } catch (Exception e) {
            log.error("event=weekly_cache_cleanup_failed errorType={}", e.getClass().getSimpleName());
        }
    }
}
