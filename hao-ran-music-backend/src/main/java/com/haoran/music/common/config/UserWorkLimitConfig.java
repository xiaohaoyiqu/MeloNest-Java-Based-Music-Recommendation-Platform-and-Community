   
                      
                        
   

package com.haoran.music.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

   
            
                       
   
@Component
@ConfigurationProperties(prefix = "user-work.limit")
@Data
public class UserWorkLimitConfig {

       
               
       
    private Boolean enabled = true;

       
                 
       
    private Integer dailyLimitNormal = 3;

       
                  
       
    private Integer dailyLimitVip = 10;

       
                
       
    private Integer dailyLimitCreator = 20;

       
                 
       
    private Integer monthlyLimitNormal = 30;

       
                  
       
    private Integer monthlyLimitVip = 100;

       
                
       
    private Integer monthlyLimitCreator = 200;

       
              
       
    private Integer minCreditScore = 60;

       
                            
       
    private Integer maxPendingCount = 5;

       
                
       
    private Integer submitIntervalSeconds = 60;

       
                  
      
                               
                               
                   
       
    public Integer getDailyLimit(boolean isVip, boolean isCreator) {
        if (isCreator) {
            return dailyLimitCreator;
        } else if (isVip) {
            return dailyLimitVip;
        } else {
            return dailyLimitNormal;
        }
    }

       
                  
      
                               
                               
                   
       
    public Integer getMonthlyLimit(boolean isVip, boolean isCreator) {
        if (isCreator) {
            return monthlyLimitCreator;
        } else if (isVip) {
            return monthlyLimitVip;
        } else {
            return monthlyLimitNormal;
        }
    }

       
                  
      
                               
                   
       
    public boolean checkCreditScore(Integer creditScore) {
        return creditScore != null && creditScore >= minCreditScore;
    }

       
                 
      
                                   
                   
       
    public boolean checkSubmitInterval(LocalDateTime lastSubmitTime) {
        if (lastSubmitTime == null) {
            return true;
        }
        LocalDateTime minNextSubmit = lastSubmitTime.plusSeconds(submitIntervalSeconds);
        return LocalDateTime.now().isAfter(minNextSubmit);
    }

       
                     
      
                                   
                             
       
    public Long getRemainingSeconds(LocalDateTime lastSubmitTime) {
        if (lastSubmitTime == null) {
            return 0L;
        }
        LocalDateTime minNextSubmit = lastSubmitTime.plusSeconds(submitIntervalSeconds);
        if (LocalDateTime.now().isAfter(minNextSubmit)) {
            return 0L;
        }
        return java.time.Duration.between(LocalDateTime.now(), minNextSubmit).getSeconds();
    }

       
               
       
    @Data
    public static class LimitCheckResult {
           
                 
           
        private boolean allowed;

           
               
           
        private String message;

           
               
           
        private Integer remainingCount;

           
                  
           
        private Long resetSeconds;

           
                    
           
        public static LimitCheckResult allowed(Integer remainingCount) {
            LimitCheckResult result = new LimitCheckResult();
            result.setAllowed(true);
            result.setRemainingCount(remainingCount);
            return result;
        }

           
                  
           
        public static LimitCheckResult denied(String message) {
            LimitCheckResult result = new LimitCheckResult();
            result.setAllowed(false);
            result.setMessage(message);
            return result;
        }

           
                         
           
        public static LimitCheckResult denied(String message, Long resetSeconds) {
            LimitCheckResult result = new LimitCheckResult();
            result.setAllowed(false);
            result.setMessage(message);
            result.setResetSeconds(resetSeconds);
            return result;
        }
    }

       
               
       
    @Data
    public static class UserWorkStats {
           
                 
           
        private Integer todayCount;

           
                 
           
        private Integer monthCount;

           
                 
           
        private Integer pendingCount;

           
                 
           
        private LocalDateTime lastSubmitTime;

           
               
           
        private Integer dailyLimit;

           
               
           
        private Integer monthlyLimit;
    }
}
