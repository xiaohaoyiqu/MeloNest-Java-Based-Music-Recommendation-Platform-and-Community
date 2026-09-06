package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

   
                      
                       
   
@Data
@TableName("notification")
public class Notification implements Serializable {

    private static final long serialVersionUID = 1L;

       
           
       
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

       
             
       
    private Long userId;

       
                     
       
    private Long senderId;

       
                         
       
    private String senderName;

       
                  
       
    private String senderAvatar;

       
                          
                                             
       
    private String groupId;

       
                        
       
    private Integer groupCount;

       
                       
       
    private BigDecimal groupAmount;

       
                         
                     
       
    private String metadata;

       
                                                                                 
       
    private String businessKey;

       
           
                    
                     
                  
                    
                
                     
                                 
                                 
                                 
                                 
                    
                       
                     
                                     
       
    private String type;

       
           
       
    private String title;

       
           
       
    private String content;

       
           
       
    private String link;

       
                        
       
    private Long relatedId;

       
                      
       
    private Integer isRead;

       
           
       
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

       
           
       
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

       
                        
                      
       
    private Integer deleted;
}
