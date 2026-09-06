package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

   
                      
                       
   
@Data
@TableName("appeal_record")
public class AppealRecord implements Serializable {

    private static final long serialVersionUID = 1L;

       
           
       
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

       
           
       
    private Long appealId;

       
            
       
    private Long operatorId;

       
                              
       
    private String operatorType;

       
                                                    
       
    private String action;

       
           
       
    private String actionRemark;

       
           
       
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
