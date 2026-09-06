


package com.haoran.music.task;

import com.haoran.music.service.impl.ContentStatisticReconcileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;




@Slf4j
@Component
public class ContentStatisticSyncTask {

    @Resource
    private ContentStatisticReconcileService statisticReconcileService;

    @Scheduled(initialDelay = 300000, fixedRate = 21600000)
    public void syncContentStatistics() {
        long startTime = System.currentTimeMillis();
        try {
            Map<String, Integer> result = statisticReconcileService.syncCoreCounters();
            log.info("Content statistic reconciliation completed: result={}, duration={}ms",
                    result, System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("Content statistic reconciliation failed: duration={}ms",
                    System.currentTimeMillis() - startTime);
        }
    }
}