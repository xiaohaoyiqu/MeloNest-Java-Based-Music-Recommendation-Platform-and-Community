package com.haoran.music.task;

import com.haoran.music.service.PaymentProofLifecycleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;






@Slf4j
@Component
public class PaymentProofCleanupRetryTask {

    private final PaymentProofLifecycleService lifecycleService;

    @Value("${payment.proof-cleanup.batch-size:20}")
    private int batchSize;

    public PaymentProofCleanupRetryTask(PaymentProofLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    @Scheduled(fixedDelayString = "${payment.proof-cleanup.retry-delay-ms:30000}")
    public void retryDueCleanup() {
        try {
            int completed = lifecycleService.retryDueCleanup(batchSize);
            if (completed > 0) {
                log.info("event=payment_proof_cleanup_batch_completed completedCount={}", completed);
            }
        } catch (Exception exception) {
            log.error("event=payment_proof_cleanup_batch_failed errorType={}",
                    exception.getClass().getSimpleName());
        }
    }
}
