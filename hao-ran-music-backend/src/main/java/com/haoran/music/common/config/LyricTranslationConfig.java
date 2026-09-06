package com.haoran.music.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;






@Data
@Component
@ConfigurationProperties(prefix = "lyric.translation")
public class LyricTranslationConfig {

    private String quotaPrefix = "lyric:translation:quota:";
    private String lockPrefix = "lyric:translation:lock:";
    private long limitPerUser = 10;
    private long limitPeriodHours = 24;
    private long lockMinutes = 20;
    private int recoveryBatchSize = 20;
    private long staleProcessingMinutes = 20;
    private int maxAttempts = 3;
    private long retryDelaySeconds = 60;
    private long maxRetryDelaySeconds = 900;
}
