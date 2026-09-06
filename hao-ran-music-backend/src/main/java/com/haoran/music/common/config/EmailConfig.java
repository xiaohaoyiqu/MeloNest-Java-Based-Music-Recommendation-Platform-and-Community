



package com.haoran.music.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;




@Data
@Component
@ConfigurationProperties(prefix = "email")
public class EmailConfig {


    private Boolean enabled = false;


    private String provider = "disabled";


    private String from = "noreply@haoranmusic.com";


    private String fromName = "HaoRanMusic";


    private String subject = "浩然音乐验证码";


    private String disabledMessage = "邮箱验证码服务暂未配置，请稍后再试";


    private Integer codeExpireMinutes = 5;
}
