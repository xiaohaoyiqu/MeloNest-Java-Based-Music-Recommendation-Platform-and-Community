package com.haoran.music.common.config;

import com.haoran.music.common.util.CommonUtil;
import com.haoran.music.common.util.ObjectUtils;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;






@Data
@Component
@ConfigurationProperties(prefix = "music.storage")
public class MusicStorageConfig {




    private List<UrlMapping> urlMappings = new ArrayList<>();




    private List<String> localProxyPackagePaths = new ArrayList<>();

    @PostConstruct
    public void registerUrlMappings() {
        Map<String, String> mappings = new LinkedHashMap<>();
        for (UrlMapping mapping : urlMappings) {
            if (ObjectUtils.isNotEmpty(mapping.getUrlPrefix()) && ObjectUtils.isNotEmpty(mapping.getLocalPath())) {
                mappings.put(mapping.getUrlPrefix(), mapping.getLocalPath());
            }
        }
        CommonUtil.setUrlMappings(mappings);
    }

    @Data
    public static class UrlMapping {
        private String urlPrefix;
        private String localPath;
    }
}
