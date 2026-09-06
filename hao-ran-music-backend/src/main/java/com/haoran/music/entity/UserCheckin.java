package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

   
                      
                         
   
@Data
@TableName("user_checkin")
public class UserCheckin extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

       
           
       
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

       
           
       
    private Long userId;

       
                
       
    private String username;

       
           
       
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate checkinDate;

       
             
       
    private Integer continuousDays;

       
            
       
    private Integer rewardPoints;

       
              
       
    private Integer rewardVipDays;

       
                        
       
    private Integer checkinType;

       
             
       
    private String ipAddress;

       
           
       
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime checkinTime;

       
                        
                       
       
    private Integer deleted;
}
