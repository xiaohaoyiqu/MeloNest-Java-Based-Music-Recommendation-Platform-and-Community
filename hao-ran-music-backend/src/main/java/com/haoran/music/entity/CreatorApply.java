package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;

   
                      
                          
   
@Data
@TableName("creator_apply")
public class CreatorApply extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

       
           
       
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

       
             
       
    private Long userId;

       
          
       
    @TableField(exist = false)
    private String username;

       
                                              
       
    @TableField("artist_name")
    private String artistName;

       
           
       
    private String realName;

       
                      
       
    @TableField("id_card")
    private String idCardNo;

       
                    
       
    private String idCardMasked;

       
               
       
    private String idCardUrl;

       
           
       
    private String phone;

       
         
       
    private String email;

       
                                             
  
    @TableField("artist_type")
    private String creatorType;

       
           
       
    @TableField("description")
    private String applyReason;

       
           
       
    @TableField("works_demo")
    private String worksSample;

       
                                               
       
    private String status;

       
            
       
    private Long reviewerId;

       
           
       
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private java.time.LocalDateTime reviewTime;

       
           
       
    @TableField("review_result")
    private String reviewReason;
}
