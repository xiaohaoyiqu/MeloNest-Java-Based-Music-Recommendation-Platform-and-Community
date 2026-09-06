   
                      
                       
   

package com.haoran.music.common.config;

import com.haoran.music.common.constant.UserAccountPolicyConstants;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

   
           
   
@Component
@ConfigurationProperties(prefix = "credit")
@Data
public class CreditConfig {

       
            
       
    private Integer initialScore = UserAccountPolicyConstants.CREDIT_SCORE_MAX;

       
                         
       
    private Integer thresholdScore = UserAccountPolicyConstants.PUBLIC_FLOW_CREDIT_MIN_SCORE;

       
              
       
    private Integer resetPeriodMonths = 3;

       
             
       
    private Integer reportRewardPoints = 30;

       
              
       
    private Integer unreasonableRefundScore = 10;

       
                      
       
    private Integer unreasonableRefundThreshold = 3;

       
                  
       
    private Integer unreasonableRefundExtraScore = 20;

       
             
       
    private Integer maliciousReportScore = 20;

       
                 
       
    private Integer refundLimitPerMonth = 3;

       
                 
       
    private Integer refundTimeLimitHours = 24;

       
             
       
    private FeedbackRewardConfig feedbackReward = new FeedbackRewardConfig();

       
              
       
    private CreditLevelConfig levels = new CreditLevelConfig();

       
              
       
    @Data
    public static class FeedbackRewardConfig {
           
                   
           
        private Integer normalPoints = 10;

           
                    
           
        private Integer normalVipDays = 0;

           
                   
           
        private Integer importantPoints = 30;

           
                    
           
        private Integer importantVipDays = 3;

           
                   
           
        private Integer criticalPoints = 50;

           
                    
           
        private Integer criticalVipDays = 7;

           
                    
           
        private Integer criticalCreditScore = 5;
    }

       
              
       
    @Data
    public static class CreditLevelConfig {
           
                     
           
        private String excellent = "excellent";

           
                    
           
        private String good = "good";

           
                    
           
        private String fair = "fair";

           
                    
           
        private String poor = "poor";

           
                 
           
        private String bad = "bad";
    }

       
             
      
                       
                 
       
    public String getCreditLevel(Integer score) {
        if (score >= UserAccountPolicyConstants.CREDIT_SCORE_EXCELLENT_MIN) {
            return levels.getExcellent();
        } else if (score >= UserAccountPolicyConstants.CREDIT_SCORE_GOOD_MIN) {
            return levels.getGood();
        } else if (score >= UserAccountPolicyConstants.CREDIT_SCORE_NORMAL_MIN) {
            return levels.getFair();
        } else if (score >= UserAccountPolicyConstants.CREDIT_SCORE_OBSERVE_MIN) {
            return levels.getPoor();
        } else {
            return levels.getBad();
        }
    }

       
               
      
                       
                     
       
    public boolean isBelowThreshold(Integer score) {
        return score < thresholdScore;
    }

       
               
      
                                             
                                       
                     
       
    public boolean canRefund(Integer currentMonthRefundCount, Integer hoursAfterPurchase) {
        return currentMonthRefundCount < refundLimitPerMonth
                && hoursAfterPurchase <= refundTimeLimitHours;
    }

       
               
      
                           
       
    public String getCurrentPeriod() {
        java.time.YearMonth now = java.time.YearMonth.now();
                      
        int currentMonth = now.getMonthValue();
        int periodMonth = ((currentMonth - 1) / resetPeriodMonths) * resetPeriodMonths + 1;
        return String.format("%d%02d", now.getYear(), periodMonth);
    }

       
                
      
                   
       
    public java.time.LocalDateTime getNextResetTime() {
        java.time.LocalDate now = java.time.LocalDate.now();
        int currentMonth = now.getMonthValue();
        int periodMonth = ((currentMonth - 1) / resetPeriodMonths) * resetPeriodMonths + 1;
        int nextPeriodMonth = periodMonth + resetPeriodMonths;
        int nextYear = now.getYear();

        if (nextPeriodMonth > 12) {
            nextPeriodMonth = 1;
            nextYear++;
        }

        return java.time.LocalDateTime.of(nextYear, nextPeriodMonth, 1, 0, 0);
    }

       
                   
      
                                                    
                                 
       
    public Map<String, Integer> getFeedbackRewardByLevel(String level) {
        Map<String, Integer> reward = new HashMap<>();
        switch (level) {
            case "normal":
                reward.put("points", feedbackReward.getNormalPoints());
                reward.put("vipDays", feedbackReward.getNormalVipDays());
                reward.put("credit", 0);
                break;
            case "important":
                reward.put("points", feedbackReward.getImportantPoints());
                reward.put("vipDays", feedbackReward.getImportantVipDays());
                reward.put("credit", 0);
                break;
            case "critical":
                reward.put("points", feedbackReward.getCriticalPoints());
                reward.put("vipDays", feedbackReward.getCriticalVipDays());
                reward.put("credit", feedbackReward.getCriticalCreditScore());
                break;
            default:
                reward.put("points", 0);
                reward.put("vipDays", 0);
                reward.put("credit", 0);
        }
        return reward;
    }
}
