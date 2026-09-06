package com.haoran.music.task;

import com.haoran.music.service.OfficialMediaDerivativeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;






@Slf4j
@Component
public class OfficialMediaDerivativeRetryTask {

    private final OfficialMediaDerivativeService officialMediaDerivativeService;

    public OfficialMediaDerivativeRetryTask(OfficialMediaDerivativeService officialMediaDerivativeService) {
        this.officialMediaDerivativeService = officialMediaDerivativeService;
    }




    @Scheduled(cron = "${schedule.task.media-derivative-retry-cron:0 */5 * * * ?}")
    public void retryDueTasks() {
        int submitted = officialMediaDerivativeService.retryDueDerivativeJobs();
        if (submitted > 0) {
            log.info("[OfficialMedia] submitted {} due derivative tasks for retry", submitted);
        }
    }
}
