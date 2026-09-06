package com.haoran.music.vo.user;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;





@Data
public class ChurnPredictionVO {




    private Long userId;




    private String username;




    private String riskLevel;




    private String riskDescription;




    private Integer churnProbability;




    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime predictedChurnTime;




    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastActiveTime;




    private Integer inactiveDays;




    private List<RiskFactor> riskFactors;




    private List<String> recallActions;




    private String valueLevel;




    private String suggestedBudget;




    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime predictTime;




    public enum RiskLevel {



        NO_RISK("no_risk", "无风险", "活跃用户，无需干预", 0),




        LOW_RISK("low_risk", "低风险", "7-14天未活跃，轻度流失风险", 20),




        MEDIUM_RISK("medium_risk", "中风险", "14-30天未活跃，中度流失风险", 50),




        HIGH_RISK("high_risk", "高风险", "30-60天未活跃，高度流失风险", 80),




        CHURNED("churned", "已流失", "60天以上未活跃，已流失", 100);

        private final String code;
        private final String name;
        private final String description;
        private final Integer baseProbability;

        RiskLevel(String code, String name, String description, Integer baseProbability) {
            this.code = code;
            this.name = name;
            this.description = description;
            this.baseProbability = baseProbability;
        }

        public String getCode() {
            return code;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public Integer getBaseProbability() {
            return baseProbability;
        }
    }




    @Data
    public static class RiskFactor {



        private String factorName;




        private String factorDescription;




        private Integer impact;




        private String currentValue;




        private String normalRange;
    }




    public enum UserValueLevel {
        HIGH("high", "高价值", "重要用户，优先召回"),
        MEDIUM("medium", "中价值", "普通用户，适度召回"),
        LOW("low", "低价值", "一般用户，低成本维护");

        private final String code;
        private final String name;
        private final String strategy;

        UserValueLevel(String code, String name, String strategy) {
            this.code = code;
            this.name = name;
            this.strategy = strategy;
        }

        public String getCode() {
            return code;
        }

        public String getName() {
            return name;
        }

        public String getStrategy() {
            return strategy;
        }
    }
}
