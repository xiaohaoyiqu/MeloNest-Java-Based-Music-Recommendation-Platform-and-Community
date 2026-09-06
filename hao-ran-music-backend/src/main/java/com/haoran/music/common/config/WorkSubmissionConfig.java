package com.haoran.music.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

   
                                                                        
  
                      
   
@Data
@Component
@ConfigurationProperties(prefix = "work.submission")
public class WorkSubmissionConfig {

    private String creatorWorkPrefix = "creator:work:submit:";
    private long creatorWorkDailyLimit = 5;
    private String musicSquarePrefix = "music_square:submit:";
    private long musicSquareDailyLimit = 10;
    private long submitWindowDays = 1;
    private long likeLockDays = 7;
}
