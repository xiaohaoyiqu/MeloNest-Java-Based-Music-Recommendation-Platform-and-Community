


package com.haoran.music.task;

import com.haoran.music.service.ArtistService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;




@Slf4j
@Component
public class ArtistCountSyncTask {

    @Resource
    private ArtistService artistService;

    @Scheduled(initialDelay = 60000, fixedRate = 1800000)
    public void syncArtistCounts() {
        long startTime = System.currentTimeMillis();
        log.info("Artist count reconciliation started");

        try {
            artistService.syncAllArtistCount();
            long duration = System.currentTimeMillis() - startTime;
            log.info("Artist count reconciliation completed: duration={}ms", duration);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("event=artist_count_reconciliation_failed durationMs={} errorType={}",
                    duration, e.getClass().getSimpleName());
        }
    }
}
