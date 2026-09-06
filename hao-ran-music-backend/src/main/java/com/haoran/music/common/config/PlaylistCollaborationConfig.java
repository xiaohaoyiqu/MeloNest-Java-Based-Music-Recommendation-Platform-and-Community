package com.haoran.music.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

   
                                             
  
                      
   
@Data
@Component
@ConfigurationProperties(prefix = "playlist.collaboration")
public class PlaylistCollaborationConfig {

    private int maxCollaborators = 10;
}
