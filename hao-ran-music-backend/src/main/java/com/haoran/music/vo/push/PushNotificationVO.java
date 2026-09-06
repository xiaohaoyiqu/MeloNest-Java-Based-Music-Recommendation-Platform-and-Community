package com.haoran.music.vo.push;

import lombok.Data;

import java.time.LocalDateTime;

   
                      
                      
   
@Data
public class PushNotificationVO {

       
           
       
    private String id;

       
                      
       
    private String pushId;

       
                                                                       
       
    private String type;

       
         
       
    private String title;

       
         
       
    private String description;

       
                     
       
    private String badge;

       
             
       
    private String coverUrl;

       
           
       
    private String link;

       
             
       
    private String fallbackLink;

       
                   
       
    private Integer priority;

       
           
       
    private LocalDateTime startTime;

       
           
       
    private LocalDateTime endTime;

       
           
       
    private LocalDateTime createTime;
}
