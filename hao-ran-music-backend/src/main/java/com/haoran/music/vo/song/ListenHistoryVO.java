package com.haoran.music.vo.song;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.haoran.music.common.util.MediaPlaybackUrlUtil;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

   
                      
                              
   
@Data
public class ListenHistoryVO implements Serializable {

    private static final long serialVersionUID = 1L;

       
             
       
    private Long id;

       
           
       
    private Long songId;

       
           
       
    private String songName;

       
           
       
    private String artistNames;

       
           
       
    private String albumName;

       
         
       
    private String cover;
                         private Long playCount;

       
            
       
    private Integer duration;

       
              
       
    private Boolean isLocal;

       
              
       
    private String urlStandard;

       
               
       
    private String urlHigh;

       
              
       
    private String urlLossless;

       
              
       
    private Integer progress;

       
           
       
    private String quality;

       
           
       
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime listenTime;

    public String getUrlStandard() {
        return Boolean.TRUE.equals(isLocal) ? urlStandard : MediaPlaybackUrlUtil.songUrl(songId, "standard", urlStandard);
    }

    public String getUrlHigh() {
        return Boolean.TRUE.equals(isLocal) ? urlHigh : MediaPlaybackUrlUtil.songUrl(songId, "high", urlHigh);
    }

    public String getUrlLossless() {
        return Boolean.TRUE.equals(isLocal) ? urlLossless : MediaPlaybackUrlUtil.songUrl(songId, "lossless", urlLossless);
    }
}
