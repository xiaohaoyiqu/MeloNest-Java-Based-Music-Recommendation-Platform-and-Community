package com.haoran.music.task;

import com.haoran.music.service.RankingSnapshotBuildService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

   
                
  
                      
   
@Slf4j
@Component
@RequiredArgsConstructor
public class RankingSnapshotRefreshTask {
    private final RankingSnapshotBuildService rankingSnapshotBuildService;

       
                                         
      
  
    @Scheduled(cron = "${schedule.task.ranking-snapshot-build-cron:0 0/30 * * * ?}")
    public void refreshHotSongSnapshot() {
        LocalDateTime windowEnd = alignWindowEnd(LocalDateTime.now());
        try {
            String snapshotId = rankingSnapshotBuildService.buildHotSongSnapshot(windowEnd);
            log.info("七日热歌快照刷新完成: snapshotId={}, windowEnd={}", snapshotId, windowEnd);
        } catch (Exception e) {
            log.error("七日热歌快照刷新失败: windowEnd={}, errorType={}",
                    windowEnd, e.getClass().getSimpleName());
        }
    }

       
                        
      
                       
                            
  
    static LocalDateTime alignWindowEnd(LocalDateTime time) {
        int alignedMinute = time.getMinute() < 30 ? 0 : 30;
        return time.withMinute(alignedMinute).withSecond(0).withNano(0);
    }
}
