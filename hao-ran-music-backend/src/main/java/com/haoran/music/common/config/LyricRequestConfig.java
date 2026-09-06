package com.haoran.music.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

   
                                                  
  
                      
   
@Data
@Component
@ConfigurationProperties(prefix = "lyric.request")
public class LyricRequestConfig {

    private String submitPrefix = "lyric:submit:";
    private long submitLimitPerSong = 5;
    private long submitLimitPeriodHours = 24;
}
