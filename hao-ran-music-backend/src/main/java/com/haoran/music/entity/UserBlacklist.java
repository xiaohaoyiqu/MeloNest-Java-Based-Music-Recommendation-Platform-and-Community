package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

   
                      
                        
   
@Data
@TableName("user_blacklist")
public class UserBlacklist extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

       
           
       
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

       
                
       
    private Long userId;

       
              
                              
       
    @TableField("blocked_user_id")
    private Long blacklistedUserId;

       
           
       
    private String reason;

       
                     
       
    private Integer isMutual;

       
                    
       
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;
}
