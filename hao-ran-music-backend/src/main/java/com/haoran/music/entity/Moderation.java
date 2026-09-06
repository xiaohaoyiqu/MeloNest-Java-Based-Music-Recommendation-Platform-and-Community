package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

   
                      
                       
   
@Data
@TableName("moderation")
public class Moderation implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

       
                                                               
       
    private String contentType;

       
           
       
    private Long contentId;

       
           
       
    private String title;

       
              
       
    private String description;

       
            
       
    private Long submitterId;

       
            
       
    private String submitterName;

       
                                      
       
    private Integer status;

       
            
       
    private Long reviewerId;

       
            
       
    private String reviewerName;

       
           
       
    private LocalDateTime reviewTime;

       
                
       
    private String reviewReason;

       
           
       
    private LocalDateTime createTime;

       
           
       
    private LocalDateTime updateTime;

       
           
       
    @TableLogic
    private Integer deleted;

       
             
       
    private Long policyId;

       
             
       
    private Long reauditFromId;

       
              
       
    private LocalDateTime reapplyAvailableTime;

       
                          
       
    private Integer canModify;
}
