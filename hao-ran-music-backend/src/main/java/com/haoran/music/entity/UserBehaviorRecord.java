package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

   
                      
                                   
   
@Data
@TableName("user_behavior_record")
public class UserBehaviorRecord extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

       
           
       
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

       
           
       
    private Long userId;

       
                                                                                                          
       
    private String behaviorType;

       
                                                          
       
    private String targetType;

       
             
       
    private Long targetId;

       
                      
       
    private Integer duration;

       
                                         
       
    private String deviceType;

       
               
       
    private String clientType;

       
           
       
    private String ipAddress;

       
           
       
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime behaviorTime;
}
