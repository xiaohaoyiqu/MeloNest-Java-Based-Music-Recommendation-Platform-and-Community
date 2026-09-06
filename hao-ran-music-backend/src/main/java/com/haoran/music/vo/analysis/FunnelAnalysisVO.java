package com.haoran.music.vo.analysis;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;





@Data
public class FunnelAnalysisVO {




    private Long funnelId;




    private String funnelName;




    private String description;




    private String funnelType;




    private List<FunnelStep> steps;




    private BigDecimal overallConversionRate;




    private Long totalUsers;




    private Long completedUsers;




    private Long averageCompletionTime;




    private FunnelStep highestDropOffStep;




    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime analysisTime;




    public enum FunnelType {



        USER_REGISTRATION("user_registration", "用户注册漏斗",

                new String[]{"访问注册页", "输入手机号", "获取验证码", "输入验证码", "设置密码", "注册成功"}),




        PURCHASE_CONVERSION("purchase_conversion", "付费转化漏斗",

                new String[]{"浏览付费内容", "点击购买", "选择支付方式", "完成支付"}),




        VIP_PURCHASE("vip_purchase", "VIP购买漏斗",

                new String[]{"访问VIP页面", "选择VIP套餐", "点击购买", "支付成功"}),




        SONG_PLAY("song_play", "歌曲播放漏斗",

                new String[]{"搜索歌曲", "点击歌曲", "开始播放", "播放完成"}),




        CONTENT_CREATION("content_creation", "内容创作漏斗",

                new String[]{"访问创作页面", "上传文件", "填写信息", "提交审核"}),




        USER_ACTIVATION("user_activation", "用户活跃漏斗",

                new String[]{"注册", "首次登录", "首次播放", "首次收藏", "首次分享"});

        private final String code;
        private final String description;
        private final String[] defaultSteps;

        FunnelType(String code, String description, String[] defaultSteps) {
            this.code = code;
            this.description = description;
            this.defaultSteps = defaultSteps;
        }

        public String getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }

        public String[] getDefaultSteps() {
            return defaultSteps;
        }
    }




    @Data
    public static class FunnelStep {



        private Integer stepNumber;




        private String stepName;




        private String stepDescription;




        private Long userCount;




        private BigDecimal conversionRate;




        private BigDecimal cumulativeConversionRate;




        private Long dropOffCount;




        private BigDecimal dropOffRate;




        private Long avgTimeSpent;




        private Long bounceCount;




        private BigDecimal bounceRate;
    }




    @Data
    public static class FunnelComparison {



        private String period1;




        private String period2;




        private List<StepComparison> stepComparisons;




        private BigDecimal overallChange;
    }




    @Data
    public static class StepComparison {



        private String stepName;




        private BigDecimal conversionRate1;




        private BigDecimal conversionRate2;




        private BigDecimal change;




        private BigDecimal changePercentage;
    }
}
