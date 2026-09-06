package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

   
                      
                       
   
@Data
@TableName("push_notification")
public class PushNotification implements Serializable {

    private static final long serialVersionUID = 1L;

       
           
       
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

       
                     
       
    private String pushId;

       
                                                                       
       
    private String type;

       
         
       
    private String title;

       
         
       
    private String description;

       
                     
       
    private String badge;

       
             
       
    private String coverUrl;

       
           
       
    private String link;

       
             
       
    private String fallbackLink;

       
                   
       
    private Integer priority;

       
           
       
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

       
           
       
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

       
                    
       
    private Integer status;

       
           
       
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

       
           
       
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

       
                        
       
    @TableLogic
    private Integer deleted;

       
                                        
       
    private Integer reviewStatus;

       
          
       
    private Long reviewerId;

       
           
       
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reviewTime;

       
           
       
    private String reviewRemark;
}
