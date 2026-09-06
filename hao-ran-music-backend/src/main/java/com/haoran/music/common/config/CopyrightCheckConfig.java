package com.haoran.music.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;






@Data
@Component
@ConfigurationProperties(prefix = "copyright.check")
public class CopyrightCheckConfig {

    private int expiryWarningDays = 30;
    private int inactiveCreatorDays = 180;
}
