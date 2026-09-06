package com.haoran.music.vo.user;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

   
                      
                           
   
@Data
public class RFMVO {

       
           
       
    private Long userId;

       
          
       
    private String username;

       
                  
       
    private Boolean interactionRestricted;

       
                          
       
    private Boolean publicFlowRestricted;

       
                                   
       
    private Integer recency;

       
                       
       
    private Integer recencyScore;

       
                            
       
    private Integer frequency;

       
                       
       
    private Integer frequencyScore;

       
                            
       
    private BigDecimal monetary;

       
                       
       
    private Integer monetaryScore;

       
            
       
    private Integer totalScore;

       
             
       
    private String segmentType;

       
             
       
    private String segmentDescription;

       
           
       
    private String operationalAdvice;

       
             
       
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastActiveTime;

       
            
       
    private Integer totalPurchaseCount;

       
            
       
    private BigDecimal totalPurchaseAmount;

       
            
       
    private Integer totalActiveDays;

       
           
       
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime calculateTime;

       
             
       
    public enum UserSegment {
           
                          
           
        IMPORTANT_VALUE("important_value", "重要价值用户", "提供VIP专属服务，新品优先推荐，维持忠诚度"),

           
                          
           
        IMPORTANT_RETENTION("important_retention", "重要保持用户", "流失风险，主动联系关怀，提供回归奖励"),

           
                          
           
        IMPORTANT_DEVELOPMENT("important_development", "重要发展用户", "高消费潜力，引导增加消费频率"),

           
                          
           
        IMPORTANT_WIN_BACK("important_win_back", "重要挽留用户", "已流失高价值用户，强力召回活动"),

           
                          
           
        GENERAL_VALUE("general_value", "一般价值用户", "活跃度高但消费少，引导付费转化"),

           
                          
           
        GENERAL_RETENTION("general_retention", "一般保持用户", "定期用户，适当激励维持"),

           
                          
           
        GENERAL_DEVELOPMENT("general_development", "一般发展用户", "新用户，完善引导提升粘性"),

           
                        
           
        CHURNED("churned", "流失用户", "低价值流失用户，低成本维护或放弃");

        private final String code;
        private final String name;
        private final String advice;

        UserSegment(String code, String name, String advice) {
            this.code = code;
            this.name = name;
            this.advice = advice;
        }

        public String getCode() {
            return code;
        }

        public String getName() {
            return name;
        }

        public String getAdvice() {
            return advice;
        }
    }
}
