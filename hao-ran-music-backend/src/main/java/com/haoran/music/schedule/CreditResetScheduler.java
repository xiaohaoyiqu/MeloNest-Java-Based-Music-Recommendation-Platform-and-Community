



package com.haoran.music.schedule;

import com.haoran.music.service.CreditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;




@Component
public class CreditResetScheduler {

    private static final Logger log = LoggerFactory.getLogger(CreditResetScheduler.class);

    private final CreditService creditService;






    public CreditResetScheduler(CreditService creditService) {
        this.creditService = creditService;
    }




    @Scheduled(cron = "${schedule.credit.monthly-reset-cron}")
    public void resetMonthlyCredits() {
        log.info("event=monthly_credit_score_reset_started trigger=scheduled");
        try {
            Integer count = creditService.resetAllCredits();
            log.info("event=monthly_credit_score_reset_completed affectedUserCount={}", count);
        } catch (Exception e) {
            log.error("event=monthly_credit_score_reset_failed errorType={}", e.getClass().getSimpleName());
        }
    }
}
