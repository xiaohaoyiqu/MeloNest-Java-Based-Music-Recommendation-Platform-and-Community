   
                      
                        
   

package com.haoran.music.schedule;

import com.haoran.music.common.config.PaymentConfig;
import com.haoran.music.service.PaymentOrderService;
import com.haoran.music.service.PaymentCompletionRecoverySummary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

   
                           
   
@Slf4j
@Component
public class PaymentOrderScheduler {

    private final PaymentOrderService paymentOrderService;
    private final PaymentConfig paymentConfig;
    private final String completionWorkerId = "payment-completion-" + UUID.randomUUID();

       
                       
      
                                        
                                
       
    public PaymentOrderScheduler(PaymentOrderService paymentOrderService, PaymentConfig paymentConfig) {
        this.paymentOrderService = paymentOrderService;
        this.paymentConfig = paymentConfig;
    }

       
                       
       
    @Scheduled(cron = "${payment.order-timeout-cron}")
    public void handleExpiredOrders() {
        log.info("event=payment_expired_order_cleanup_started expireHours={}",
            paymentConfig.getOrderExpireHours());

        try {
            Integer count = paymentOrderService.handleExpiredOrders();
            log.info("event=payment_expired_order_cleanup_completed affectedOrderCount={}", count);

        } catch (Exception e) {
            log.error("event=payment_expired_order_cleanup_failed errorType={}",
                e.getClass().getSimpleName());
        }
    }

       
                         
       
    @Scheduled(cron = "${payment.order-cleanup-cron}")
    public void cleanOldCancelledOrders() {
        log.info("event=payment_cancelled_order_cleanup_started retentionDays={}",
            paymentConfig.getOrderRetentionDays());

        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(paymentConfig.getOrderRetentionDays());
            Integer count = paymentOrderService.cleanOldCancelledOrders(cutoffDate);
            log.info("event=payment_cancelled_order_cleanup_completed affectedOrderCount={}", count);

        } catch (Exception e) {
            log.error("event=payment_cancelled_order_cleanup_failed errorType={}",
                e.getClass().getSimpleName());
        }
    }

       
                                                     
       
    @Scheduled(cron = "${payment.completion-recovery.cron:0 */1 * * * ?}")
    public void recoverPendingCompletions() {
        PaymentConfig.CompletionRecovery recovery = paymentConfig.getCompletionRecovery();
        if (recovery == null || !Boolean.TRUE.equals(recovery.getEnabled())) {
            return;
        }
        try {
            PaymentCompletionRecoverySummary summary =
                    paymentOrderService.recoverPendingCompletions(completionWorkerId);
            log.info("event=payment_completion_recovery_completed candidates={} claimed={} completed={} deferred={} deadLettered={} lostClaims={}",
                    summary.getCandidates(), summary.getClaimed(), summary.getCompleted(),
                    summary.getDeferred(), summary.getDeadLettered(), summary.getLostClaims());
        } catch (Exception exception) {
            log.error("event=payment_completion_recovery_failed errorType={}",
                    exception.getClass().getSimpleName());
        }
    }

       
                       
       
    @Scheduled(cron = "${payment.order-statistics-cron}")
    public void dailyOrderStatistics() {
        log.info("event=payment_daily_statistics_started");

        try {
            LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
                               
                       
                       
                       
            log.info("event=payment_daily_statistics_completed statisticsDate={}",
                yesterday.toLocalDate());

        } catch (Exception e) {
            log.error("event=payment_daily_statistics_failed errorType={}",
                e.getClass().getSimpleName());
        }
    }
}
