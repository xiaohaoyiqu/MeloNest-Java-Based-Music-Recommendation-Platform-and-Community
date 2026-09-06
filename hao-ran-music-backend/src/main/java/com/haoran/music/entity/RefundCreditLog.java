package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

   
                      
                                      
   
@Data
@TableName("refund_credit_log")
public class RefundCreditLog extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

       
           
       
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

       
           
       
    private Long userId;

       
           
       
    private Long refundId;

       
                                
       
    private String changeType;

       
                           
       
    private Integer score;

       
           
       
    private String reason;

       
            
       
    private Integer afterScore;

       
                             
       
    private String creditPeriod;

       
             
       
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime periodStartTime;

       
             
       
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime periodEndTime;

       
                       
       
    @TableLogic
    private Integer deleted;
}
