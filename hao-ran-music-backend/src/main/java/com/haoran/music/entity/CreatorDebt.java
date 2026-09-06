package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

   
                      
                                    
   
@Data
@TableName("creator_debt")
public class CreatorDebt extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

       
           
       
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

       
                                              
       
    @TableField("user_id")
    private Long creatorId;

       
             
       
    private Long refundId;

       
               
       
    private Long withdrawId;

       
           
       
    private BigDecimal debtAmount;

       
           
       
    private BigDecimal paidAmount;

       
           
       
    private BigDecimal remainingAmount;

       
                                              
       
    private String status;

       
           
       
    private String reason;

       
           
       
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime clearedTime;

       
                       
       
    @TableLogic
    private Integer deleted;
}

