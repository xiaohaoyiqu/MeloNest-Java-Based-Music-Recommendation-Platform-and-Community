




package com.haoran.music.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;












@Component
@ConfigurationProperties(prefix = "payment")
@Data
public class PaymentConfig {








    private BigDecimal platformFeeRate = new BigDecimal("0.02");






    private String currency = "CNY";




    private Integer orderExpireHours = 1;




    private Integer orderExpireMinutes = 30;




    private Integer orderRetentionDays = 30;




    private BigDecimal withdrawMinAmount = new BigDecimal("50");




    private String scanPath;




    private String platformPath;




    private String creatorPath;




    private Integer scanIntervalHours = 3;




    private Integer verifyCodeLength = 6;





    private String verificationHmacSecret = "";





    private String previousVerificationHmacSecret = "";




    private String orderNoPrefix = "HR";




    private String scanCron = "0 0 */3 * * ?";




    private String orderTimeoutCron = "0 */10 * * * ?";




    private Boolean schedulerEnabled = true;




    private String proofPath;





    private String urlPrefix;




    private Integer tempFileRetentionDays = 7;




    private Vip vip = new Vip();




    private Alipay alipay = new Alipay();




    private Wechat wechat = new Wechat();




    private Ssh ssh = new Ssh();




    private Code code = new Code();




    private CompletionRecovery completionRecovery = new CompletionRecovery();




    public int getPlatformFeePercent() {
        return platformFeeRate.multiply(new BigDecimal("100")).intValue();
    }



    public BigDecimal getPlatformFeeRate() {
        return platformFeeRate;
    }







    public BigDecimal calculatePlatformFee(BigDecimal amount) {
        return amount.multiply(platformFeeRate)
                .setScale(2, BigDecimal.ROUND_HALF_UP);
    }







    public BigDecimal calculateCreatorEarnings(BigDecimal amount) {
        return amount.subtract(calculatePlatformFee(amount))
                .setScale(2, BigDecimal.ROUND_HALF_UP);
    }







    public boolean checkWithdrawThreshold(BigDecimal amount) {
        return amount.compareTo(withdrawMinAmount) >= 0;
    }




    public Integer getOrderExpireMinutes() {
        return orderExpireMinutes;
    }

    @Data
    public static class Vip {
        private Integer pricePerMonth = 1000;
        private Integer minMonths = 1;
        private Integer maxMonths = 12;
        private Integer daysPerMonth = 30;
    }

    @Data
    public static class Alipay {
        private String appId = "";
        private String privateKey = "";
        private String publicKey = "";
        private String gateway = "https://openapi.alipaydev.com/gateway.do";
    }

    @Data
    public static class Wechat {
        private String appId = "";
        private String mchId = "";
        private String apiKey = "";
        private String notifyUrl = "";
        private String clientIp = "";
    }

    @Data
    public static class Ssh {
        private String host = "";
        private Integer port = 22;
        private String user = "hdfs";
        private String password = "";
        private String privateKeyPath = "/home/hdfs/.ssh/id_rsa";
        private String knownHostsPath = "/home/hdfs/.ssh/known_hosts";
        private Integer connectTimeout = 30000;
        private Integer readTimeout = 60000;
    }

    @Data
    public static class Code {
        private String scanBasePath;
    }




    @Data
    public static class CompletionRecovery {
        private Boolean enabled = false;
        private String cron = "0 */1 * * * ?";
        private Integer batchSize = 20;
        private Integer maxAttempts = 8;
        private Integer leaseSeconds = 300;
        private Integer baseBackoffSeconds = 30;
        private Integer maxBackoffSeconds = 3600;
    }

}
