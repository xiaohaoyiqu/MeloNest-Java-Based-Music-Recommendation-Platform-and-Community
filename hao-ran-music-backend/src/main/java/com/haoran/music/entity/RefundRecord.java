   
                      
                       
   

package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

   
          
   
@Data
@TableName("refund_record")
public class RefundRecord extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

       
           
       
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

       
           
       
    private String refundNo;

       
           
       
    private Long userId;

       
            
       
    private Long orderId;

                                                                                                 
    private String originalOrderStatus;

       
                                              
       
    private String orderType;

       
           
       
    private BigDecimal amount;

       
           
       
    private String reason;

       
           
       
    private String description;

       
                                                                                      
       
    private String status;

       
            
       
    private Long reviewerId;

       
           
       
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reviewTime;

       
           
       
    private String reviewReason;

       
           
       
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completedTime;

       
             
       
    private Long feedbackId;

       
                         
                                
       
    private Integer isUnreasonable;

       
              
                             
       
    private String unreasonableReason;
}
