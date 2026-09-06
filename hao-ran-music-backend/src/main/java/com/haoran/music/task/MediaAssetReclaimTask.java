package com.haoran.music.task;

import com.haoran.music.service.MediaAssetService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

   
              
  
                      
   
@Slf4j
@Component
public class MediaAssetReclaimTask {

    private final MediaAssetService mediaAssetService;

    @Value("${schedule.task.media-asset-reclaim-limit:50}")
    private int reclaimLimit;

    public MediaAssetReclaimTask(MediaAssetService mediaAssetService) {
        this.mediaAssetService = mediaAssetService;
    }

       
                               
       
    @Scheduled(cron = "${schedule.task.media-asset-reclaim-cron:0 0 * * * ?}")
    public void reclaimDueAssets() {
        int reclaimed = mediaAssetService.reclaimOrphanAssets(reclaimLimit);
        if (reclaimed > 0) {
            log.info("[MediaAsset] reclaimed {} orphan assets", reclaimed);
        }
    }
}
