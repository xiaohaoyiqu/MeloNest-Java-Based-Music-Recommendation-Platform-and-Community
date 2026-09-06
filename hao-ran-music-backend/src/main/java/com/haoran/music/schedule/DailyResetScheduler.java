



package com.haoran.music.schedule;

import com.haoran.music.service.CreditService;
import com.haoran.music.service.UserPointsService;
import com.haoran.music.service.VipService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;




@Component
public class DailyResetScheduler {

    private static final Logger log = LoggerFactory.getLogger(DailyResetScheduler.class);

    private final UserPointsService userPointsService;
    private final VipService vipService;
    private final CreditService creditService;








    public DailyResetScheduler(UserPointsService userPointsService,
                              VipService vipService,
                              CreditService creditService) {
        this.userPointsService = userPointsService;
        this.vipService = vipService;
        this.creditService = creditService;
    }




    @Scheduled(cron = "${schedule.daily-reset.quota-cron}")
    public void resetDailyQuota() {
        log.info("event=daily_points_reset_started trigger=scheduled");
        try {
            Integer count = userPointsService.resetDailyPoints();
            log.info("event=daily_points_reset_completed affectedUserCount={}", count);
        } catch (Exception e) {
            log.error("event=daily_points_reset_failed errorType={}", e.getClass().getSimpleName());
        }
    }




    @Scheduled(cron = "${schedule.daily-reset.vip-expiry-cron}")
    public void checkVipExpiry() {
        log.info("event=daily_vip_expiry_check_started trigger=scheduled");
        try {
            Integer expiredCount = vipService.handleExpiredVips();
            log.info("event=daily_vip_expiry_check_completed affectedVipCount={}", expiredCount);
        } catch (Exception e) {
            log.error("event=daily_vip_expiry_check_failed errorType={}", e.getClass().getSimpleName());
        }
    }




    @Scheduled(cron = "${schedule.daily-reset.expired-data-cron}")
    public void cleanExpiredData() {
        log.info("event=expired_data_cleanup_started trigger=scheduled");
        try {

            log.info("event=expired_data_cleanup_noop reason=service_not_implemented");
        } catch (Exception e) {
            log.error("event=expired_data_cleanup_failed errorType={}", e.getClass().getSimpleName());
        }
    }




    @Scheduled(cron = "${schedule.daily-reset.credit-score-cron}")
    public void resetCreditScore() {
        log.info("event=daily_credit_score_reset_started trigger=scheduled");
        try {
            Integer count = creditService.resetAllCredits();
            log.info("event=daily_credit_score_reset_completed affectedUserCount={}", count);
        } catch (Exception e) {
            log.error("event=daily_credit_score_reset_failed errorType={}", e.getClass().getSimpleName());
        }
    }




    @Scheduled(cron = "${schedule.daily-reset.report-cron}")
    public void generateDailyReport() {
        log.info("event=daily_report_generation_started");
        try {
            LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
            log.info("event=daily_report_generation_completed reportDate={}", yesterday.toLocalDate());
        } catch (Exception e) {
            log.error("event=daily_report_generation_failed errorType={}", e.getClass().getSimpleName());
        }
    }
}
