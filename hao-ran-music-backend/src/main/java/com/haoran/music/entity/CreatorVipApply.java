package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

   
                      
                             
   
@Data
@TableName("creator_vip_apply")
public class CreatorVipApply extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

       
           
       
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

       
            
       
    private Long creatorId;

       
           
       
    private Integer applyDays;

       
           
       
    private String reason;

       
                                               
       
    private String status;

       
            
       
    private Long reviewerId;

       
           
       
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reviewTime;

       
           
       
    private String reviewReason;

       
                  
       
    private Long vipOrderId;

       
                    
       
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastApplyTime;
}
