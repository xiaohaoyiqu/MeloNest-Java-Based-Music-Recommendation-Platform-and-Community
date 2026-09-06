package com.haoran.music.vo.experiment;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Map;





@Data
public class ABTestVO {




    private Long experimentId;




    private String experimentName;




    private String description;




    private String status;




    private String statusDescription;




    private String experimentType;




    private Map<String, Integer> trafficAllocation;




    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;




    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;




    private ExperimentMetrics metrics;




    private Map<String, VariantData> variantData;




    private StatisticalSignificance significance;




    private String conclusion;




    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;




    public enum ExperimentStatus {



        DRAFT("draft", "设计中"),




        RUNNING("running", "运行中"),




        COMPLETED("completed", "已完成"),




        TERMINATED("terminated", "已终止");

        private final String code;
        private final String description;

        ExperimentStatus(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }
    }




    public enum ExperimentType {



        RECOMMENDATION("recommendation", "推荐算法测试"),




        UI_TEST("ui_test", "界面UI测试"),




        FEATURE_TEST("feature_test", "功能测试"),




        MARKETING("marketing", "营销活动测试"),




        PRICING("pricing", "定价测试");

        private final String code;
        private final String description;

        ExperimentType(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }
    }




    @Data
    public static class ExperimentMetrics {



        private String primaryMetric;




        private String primaryMetricDescription;




        private java.util.List<String> secondaryMetrics;




        private BigDecimal minimumDetectableEffect;




        private BigDecimal statisticalPower;




        private BigDecimal significanceLevel;
    }




    @Data
    public static class VariantData {



        private String variantName;




        private Integer trafficPercentage;




        private Long sampleSize;




        private Long conversions;




        private BigDecimal conversionRate;




        private BigDecimal avgSessionDuration;




        private BigDecimal avgRevenuePerUser;




        private BigDecimal clickThroughRate;




        private BigDecimal bounceRate;

        public BigDecimal getConversionRate() {
            if (sampleSize == null || sampleSize == 0 || conversions == null) {
                return BigDecimal.ZERO;
            }
            return new BigDecimal(conversions)
                    .divide(new BigDecimal(sampleSize), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }
    }




    @Data
    public static class StatisticalSignificance {



        private Boolean isSignificant;




        private BigDecimal pValue;




        private BigDecimal confidence;




        private BigDecimal effectSize;




        private BigDecimal relativeLift;




        private BigDecimal absoluteDifference;




        private String conclusion;
    }
}
