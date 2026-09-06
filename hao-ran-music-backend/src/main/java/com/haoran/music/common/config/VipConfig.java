   
                      
                       
   

package com.haoran.music.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import com.haoran.music.common.enums.VipLevel;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.PostConstruct;

   
           
                       
   
@Component
@ConfigurationProperties(prefix = "vip")
@Data
public class VipConfig {

       
                                                        
                                                      
       
    @Deprecated
    private Integer dailyDownloadQuota = 50;

       
                 
       
    private Integer normalDownloadQuota = 10;

       
                    
       
    private Map<String, VipPrice> prices = new HashMap<>();

       
              
       
    private VipPrivilege privileges = new VipPrivilege();

       
               
       
    @Data
    public static class VipPrice {
           
                
           
        private Integer price;

           
             
           
        private Integer days;

           
             
           
        private String name;

           
                            
           
        private Integer discount = 100;

           
                 
           
        public Integer getActualPrice() {
            return price * discount / 100;
        }
    }

       
               
       
    @Data
    public static class VipPrivilege {
           
                         
           
        private Boolean losslessQuality = true;

           
                          
           
        private Boolean highBitrateMv = true;

           
                           
           
        private Boolean unlimitedDownload = true;

           
                         
           
        private Boolean adFree = true;

           
                         
           
        private Boolean prioritySupport = true;

           
                         
           
        private Boolean exclusiveBadge = true;

           
                         
           
        private Boolean exclusivePlaylist = true;

           
                         
           
        private Boolean offlineDownload = true;
    }

       
              
      
                                        
                   
       
       
                                                                       
      
  
    @PostConstruct
    public void afterPropertiesSet() {
        initDefaultPrices();
    }

    public VipPrice getPrice(String type) {
        return prices.get(type);
    }

       
                 
      
                     
                    
       
    public BigDecimal getPriceInYuan(String type) {
        VipPrice price = getPrice(type);
        if (price == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(price.getActualPrice())
                .divide(new BigDecimal("100"), 2, BigDecimal.ROUND_HALF_UP);
    }

       
                 
      
                            
                   
       
    public boolean hasPrivilege(String privilege) {
        try {
            java.lang.reflect.Field field = privileges.getClass().getDeclaredField(privilege);
            field.setAccessible(true);
            return Boolean.TRUE.equals(field.get(privileges));
        } catch (Exception e) {
            return false;
        }
    }

       
              
       
    public void initDefaultPrices() {
        if (prices.isEmpty()) {
                              
            VipPrice monthPrice = new VipPrice();
            monthPrice.setPrice(1000);               
            monthPrice.setDays(30);
            monthPrice.setName("月度VIP");
            prices.put("month", monthPrice);

                              
            VipPrice quarterPrice = new VipPrice();
            quarterPrice.setPrice(2500);               
            quarterPrice.setDays(90);
            quarterPrice.setName("季度VIP");
            prices.put("quarter", quarterPrice);

                                
            VipPrice yearPrice = new VipPrice();
            yearPrice.setPrice(10000);                 
            yearPrice.setDays(365);
            yearPrice.setName("年度VIP");
            prices.put("year", yearPrice);
        }
    }

       
                      
                            
                     
       
    public int getDailyDownloadQuota(Integer vipLevel) {
        return VipLevel.getDownloadQuotaByLevel(Integer.valueOf(vipLevel));
    }
}
