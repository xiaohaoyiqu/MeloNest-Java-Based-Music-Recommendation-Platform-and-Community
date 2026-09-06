




package com.haoran.music.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;





@Component
@ConfigurationProperties(prefix = "creator")
@Data
public class CreatorConfig {




    private Integer vipApplyIntervalDays = 180;




    private Integer vipApplyDays = 15;




    private Integer minResourcesForVip = 5;




    private Integer minEarningsForVip = 50;




    private CreatorTypeConfig creatorType = new CreatorTypeConfig();




    private CreatorStatusConfig creatorStatus = new CreatorStatusConfig();




    @Data
    public static class CreatorTypeConfig {



        private String independent = "independent";




        private String signed = "signed";




        private String externalIndependent = "external_independent";




        private String externalSigned = "external_signed";
    }




    @Data
    public static class CreatorStatusConfig {



        private String pending = "pending";




        private String active = "active";




        private String suspended = "suspended";




        private String inactive = "inactive";
    }








    public boolean checkVipApplyCondition(Integer resourceCount, Integer earnings) {
        return resourceCount >= minResourcesForVip && earnings >= minEarningsForVip;
    }







    public boolean checkVipApplyInterval(java.time.LocalDateTime lastApplyTime) {
        if (lastApplyTime == null) {
            return true;
        }
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime minNextApply = lastApplyTime.plusDays(vipApplyIntervalDays);
        return now.isAfter(minNextApply);
    }
}
