


package com.haoran.music.common.config;

import com.haoran.music.service.impl.ContentStatisticReconcileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;




@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "haoran.content-statistic.startup-sync", name = "enabled", havingValue = "true")
public class CommentCountSyncInitializer implements ApplicationRunner {

    private final ContentStatisticReconcileService statisticReconcileService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            log.info("Comment count reconciliation started");
            Map<String, Integer> result = statisticReconcileService.syncCommentCounts();
            log.info("Comment count reconciliation completed: {}", result);
        } catch (Exception e) {
            log.error("Comment count reconciliation failed");
        }
    }
}
