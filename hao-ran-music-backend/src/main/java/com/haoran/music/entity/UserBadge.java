package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

   
                      
                       
   
@Data
@TableName("user_badge")
public class UserBadge extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

       
           
       
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

       
           
       
    private Long userId;

       
                                         
       
    private String badgeType;

    private Integer badgeLevel;

       
           
       
    private String badgeName;

    private String badgeDescription;

       
              
       
    private String badgeIcon;

       
           
       
    private String badgeColor;

       
                                          
       
    private String position;

    private Integer isEquipped;

    private LocalDateTime obtainTime;

       
                   
       
    private Long ruleId;

       
               
       
    private Integer ruleVersion;

       
                        
       
    private String grantEventId;

       
                    
       
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;
}
