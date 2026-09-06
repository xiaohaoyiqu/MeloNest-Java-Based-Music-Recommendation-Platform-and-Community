package com.haoran.music.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;






@Data
@Component
@ConfigurationProperties(prefix = "artist.application")
public class ArtistApplicationConfig {

    private String applyPrefix = "artist:apply:";
    private String applyCountPrefix = "artist:apply:count:";
    private long applyLimitDays = 30;
    private int maxAppliesPerPeriod = 3;
}
