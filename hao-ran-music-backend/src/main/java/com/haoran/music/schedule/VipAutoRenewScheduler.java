



package com.haoran.music.schedule;

import com.haoran.music.entity.UserVip;
import com.haoran.music.mapper.UserVipMapper;
import com.haoran.music.service.UserVipService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;




@Slf4j
@Component
public class VipAutoRenewScheduler {

    private final UserVipMapper userVipMapper;
    private final UserVipService userVipService;







    public VipAutoRenewScheduler(UserVipMapper userVipMapper,
                                  UserVipService userVipService) {
        this.userVipMapper = userVipMapper;
        this.userVipService = userVipService;
    }




    @Scheduled(cron = "${schedule.vip.auto-renew-cron}")
    public void processAutoRenew() {
        log.info("event=vip_auto_renew_started trigger=scheduled");
        try {
            userVipService.processAutoRenew();
            log.info("event=vip_auto_renew_completed trigger=scheduled");
        } catch (Exception e) {
            log.error("event=vip_auto_renew_failed trigger=scheduled errorType={}",
                e.getClass().getSimpleName());
        }
    }




    @Scheduled(cron = "${schedule.vip.expiring-reminder-cron}")
    public void sendExpiringReminder() {
        log.info("event=vip_expiration_reminder_scan_started trigger=scheduled");
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime threeDaysLater = now.plusDays(3);

            List<UserVip> expiringVips = userVipMapper.selectExpiringVipsForReminder(now, threeDaysLater);


            log.info("event=vip_expiration_reminder_scan_completed candidateCount={}",
                expiringVips.size());
        } catch (Exception e) {
            log.error("event=vip_expiration_reminder_scan_failed trigger=scheduled errorType={}",
                e.getClass().getSimpleName());
        }
    }




    @Scheduled(cron = "${schedule.vip.expired-process-cron}")
    public void processExpiredVips() {
        log.info("event=vip_expired_status_update_started trigger=scheduled");
        try {
            LocalDateTime now = LocalDateTime.now();

            int expiredCount = userVipMapper.markExpiredVips(now);

            log.info("event=vip_expired_status_update_completed affectedVipCount={}", expiredCount);
        } catch (Exception e) {
            log.error("event=vip_expired_status_update_failed trigger=scheduled errorType={}",
                e.getClass().getSimpleName());
        }
    }
}
