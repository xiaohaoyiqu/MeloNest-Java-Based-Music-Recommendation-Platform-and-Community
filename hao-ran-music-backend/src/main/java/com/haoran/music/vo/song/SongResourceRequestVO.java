package com.haoran.music.vo.song;

import lombok.Data;

import java.time.LocalDateTime;

   
                      
                        
   
@Data
public class SongResourceRequestVO {

       
           
       
    private Long id;

       
           
       
    private Long userId;

       
           
       
    private String userNickname;

       
           
       
    private String songName;

       
           
       
    private String artistName;

       
           
       
    private String albumName;

       
           
       
    private String versionInfo;

       
                 
       
    private String fileUrl;

                                                       

       
                                             
       
    private Integer detectedQuality;

       
             
       
    private String qualityName;

       
               
       
    private Long fileSize;

       
              
       
    private Integer duration;

       
                
       
    private Integer bitrate;

       
              
       
    private Integer sampleRate;

       
                         
       
    private String format;

                                                       

       
           
       
    private String sourceDescription;

       
           
       
    private String remark;

       
         
       
    private String status;

       
           
       
    private String statusDesc;

       
            
       
    private Long handlerId;

       
            
       
    private String handlerName;

       
           
       
    private LocalDateTime handleTime;

       
             
       
    private String handleResult;

       
                          
       
    private Long matchedSongId;

       
               
       
    private String matchedSongName;

       
                           
       
    private Long autoSongId;

       
                
       
    private String autoSongName;

       
            
       
    private Boolean notified;

       
           
       
    private LocalDateTime createTime;
}
