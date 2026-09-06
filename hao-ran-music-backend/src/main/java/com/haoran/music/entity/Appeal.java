package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

   
                      
                     
   
@Data
@TableName("appeal")
public class Appeal implements Serializable {

    private static final long serialVersionUID = 1L;

       
           
       
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

       
             
       
    private Long userId;

       
                                                     
       
    private String appealType;

       
           
       
    private String appealReason;

       
                                               
       
    private String appealStatus;

       
                                
       
    private Long relatedId;

       
                       
       
    private String evidenceUrls;

       
            
       
    private Long reviewerId;

       
           
       
    private String reviewResult;

       
           
       
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reviewTime;

       
           
       
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

       
           
       
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
