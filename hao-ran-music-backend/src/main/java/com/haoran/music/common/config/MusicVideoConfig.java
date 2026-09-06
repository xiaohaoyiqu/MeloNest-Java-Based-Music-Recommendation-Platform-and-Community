package com.haoran.music.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

   
                                                  
  
                      
   
@Data
@Component
@ConfigurationProperties(prefix = "music.video")
public class MusicVideoConfig {

    private String uploadPath;
    private String tempPath;
    private long maxSize = 104857600L;
    private int maxDuration = 300;
    private int minDuration = 3;
    private String nginxUrl;
    private int tempRetentionHours = 1;
    private List<String> allowedFormats = new ArrayList<>(Arrays.asList("mp4", "mov", "avi", "mkv"));

    public boolean isAllowedFormat(String extension) {
        if (extension == null || allowedFormats == null || allowedFormats.isEmpty()) {
            return false;
        }
        for (String format : allowedFormats) {
            if (extension.equalsIgnoreCase(format)) {
                return true;
            }
        }
        return false;
    }
}
