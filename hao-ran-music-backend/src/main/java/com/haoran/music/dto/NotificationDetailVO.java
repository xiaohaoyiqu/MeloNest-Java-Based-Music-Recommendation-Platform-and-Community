package com.haoran.music.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

   
                      
                                  
   
@Data
public class NotificationDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

       
           
       
    private Long id;

       
            
       
    private Long senderId;

       
            
       
    private String senderName;

       
            
       
    private String senderAvatar;

       
           
       
    private String title;

       
           
       
    private String content;

       
                   
       
    private BigDecimal amount;

       
           
       
    private String resourceName;

       
           
       
    private String resourceType;

       
           
       
    private Long resourceId;

       
           
       
    private String type;

                
    private Boolean isRead;

                       
    private String link;

       
           
       
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

       
                  
       
    private String metadata;
}
