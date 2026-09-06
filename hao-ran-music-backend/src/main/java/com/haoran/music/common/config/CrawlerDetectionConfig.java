



package com.haoran.music.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;




@Data
@Component
@ConfigurationProperties(prefix = "crawler.detection")
public class CrawlerDetectionConfig {

    private List<String> allowedReferrers = new ArrayList<>();

    private Integer missingUserAgentRisk = 40;
    private Integer spiderUserAgentRisk = 50;
    private Integer missingRefererRisk = 20;
    private Integer externalRefererRisk = 10;
    private Integer suspiciousAccessRisk = 20;
    private Integer readOnlyRisk = 20;
    private Integer crawlerRiskThreshold = 60;
    private Integer accessWindowSeconds = 60;
    private Integer accessBurstThreshold = 30;
    private Integer accessNoInteractionThreshold = 20;
    private Integer readOnlyViewThreshold = 50;
    private Integer behaviorExpireSeconds = 3600;
}
