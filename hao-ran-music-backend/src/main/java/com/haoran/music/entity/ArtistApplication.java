package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

   
                      
                        
   
@Data
@TableName("artist_application")
public class ArtistApplication implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

       
           
       
    private Long userId;

       
          
       
    private String userName;

       
           
       
    private String realName;

       
           
       
    private String phone;

       
         
       
    private String email;

       
           
       
    private String introduction;

       
                   
       
    private String demoWorks;

       
                          
       
    private Integer applicationType;

       
                             
       
    private Integer status;

       
            
       
    private Long reviewerId;

       
           
       
    private LocalDateTime reviewTime;

       
           
       
    private String reviewReason;

       
           
       
    private LocalDateTime createTime;

       
           
       
    private LocalDateTime updateTime;

       
           
       
    @TableLogic
    private Integer deleted;
}
