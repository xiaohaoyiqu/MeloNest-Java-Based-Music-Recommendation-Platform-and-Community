



package com.haoran.music.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;




@Data
@Component
@ConfigurationProperties(prefix = "user.verification")
public class UserVerificationConfig {




    private Integer codeLength = 6;




    private Integer codeExpireMinutes = 5;




    private Integer resendIntervalSeconds = 60;




    private Integer maxSendCountPerHour = 5;




    private Integer maxFailCount = 5;




    private Integer defaultQueryLimit = 10;
}
