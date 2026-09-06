   
                      
                                                  
   
package com.haoran.music.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

   
                                                               
   
@Data
@Component
@ConfigurationProperties(prefix = "reward.rules")
public class RewardRulesConfig {

       
                                                  
       
    private BigDecimal minAmount = new BigDecimal("1");

       
                                                  
       
    private BigDecimal maxAmount = new BigDecimal("200");

       
                                             
       
    private Integer dailyMaxCount = 10;

       
                                                            
       
    private BigDecimal dailyMaxAmount = new BigDecimal("200");

       
                                                                 
       
    private BigDecimal alertAmountThreshold = new BigDecimal("2000");

       
                                                       
       
    private Integer dailyLimitCacheDays = 2;
}
