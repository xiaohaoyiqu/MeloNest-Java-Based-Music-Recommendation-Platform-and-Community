   
                      
                                                 
   

package com.haoran.music.schedule;

import com.haoran.music.common.config.PaymentConfig;
import com.haoran.music.service.PaymentCodeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.Map;

   
            
                        
   
@Slf4j
@Component
public class PaymentCodeScanner {

    private final PaymentCodeService paymentCodeService;
    private final PaymentConfig paymentConfig;

       
                      
      
                                        
                                                
       
    public PaymentCodeScanner(PaymentCodeService paymentCodeService, PaymentConfig paymentConfig) {
        this.paymentCodeService = paymentCodeService;
        this.paymentConfig = paymentConfig;
    }

       
                           
                                     
       
    @Scheduled(cron = "${payment.scan-cron}")
    public void scanPaymentCodes() {
        log.info("event=payment_code_scan_started trigger=scheduled");

        try {
                                                                                
                                                                 
            Map<String, Object> result = paymentCodeService.scanPaymentCodes(paymentConfig.getScanPath());
            log.info("event=payment_code_scan_completed trigger=scheduled resultFieldCount={}", result.size());

        } catch (Exception e) {
            log.error("event=payment_code_scan_failed trigger=scheduled errorType={}",
                    e.getClass().getSimpleName());
        }
    }

       
                            
      
                                                
       
    public java.util.Map<String, Object> manualScan() {
        log.info("event=payment_code_scan_started trigger=manual");
        Map<String, Object> result = paymentCodeService.scanPaymentCodes(paymentConfig.getScanPath());
        log.info("event=payment_code_scan_completed trigger=manual resultFieldCount={}", result.size());
        return result;
    }
}
