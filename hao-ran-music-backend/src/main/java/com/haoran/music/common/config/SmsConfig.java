



package com.haoran.music.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;




@Data
@Component
@ConfigurationProperties(prefix = "sms")
public class SmsConfig {




    private Boolean enabled = false;




    private String provider = "disabled";




    private String disabledMessage = "短信服务暂未配置，请稍后再试";


    private String accessKeyId;


    private String accessKeySecret;


    private String signName;


    private String templateCode;


    private String codeParamName = "code";


    private String endpoint = "dysmsapi.aliyuncs.com";


    private Integer connectTimeoutMillis = 3000;


    private Integer readTimeoutMillis = 5000;
}
