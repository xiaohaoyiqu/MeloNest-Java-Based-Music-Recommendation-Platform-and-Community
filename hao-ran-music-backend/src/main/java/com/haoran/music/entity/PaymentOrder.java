   
                      
                       
   

package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

   
         
                         
   
@Data
@TableName("payment_order")
public class PaymentOrder implements Serializable {

    private static final long serialVersionUID = 1L;

       
           
       
    @TableId(type = IdType.AUTO)
    private Long id;

       
          
       
    private String orderNo;

       
             
       
    private Long userId;

       
                     
       
    private Long payeeId;

       
                           
       
    private BigDecimal amount;

       
                            
       
    private String currency;

       
                          
       
    private String paymentType;

       
                                                                             
                  
                       
                      
                        
       
    private String businessType;

       
             
       
    private Long businessId;

       
           
       
    private String orderTitle;

       
           
       
    private String orderDesc;

       
                                                                       
                                       
                                
                                
                                                   
                                  
                        
                         
                      
       
    private String status;

       
                                                                      
                                    
       
    private String completionStatus;

       
               
       
    private LocalDateTime completionTime;

       
                   
       
    private String completionError;

       
                                         
       
    private Integer completionAttemptCount;

       
                              
       
    private LocalDateTime completionLastAttemptTime;

       
                       
       
    private LocalDateTime completionNextRetryTime;

       
                                 
       
    private String completionLeaseOwner;

       
                  
       
    private LocalDateTime completionLeaseUntil;

       
                            
       
    private LocalDateTime completionDeadLetterTime;

       
                    
       
    private String paymentProof;

       
              
       
    private String userRemark;

       
             
       
    private String verifyCode;

       
            
       
    private Long reviewerId;

       
           
       
    private LocalDateTime reviewTime;

       
           
       
    private String reviewReason;

       
               
       
    private String transactionId;

       
                    
       
    private String qrCodeUrl;

       
                  
       
    private LocalDateTime expireTime;
       
                   
       
    private LocalDateTime notifyTime;

       
                   
       
    private String notifyContent;

       
                   
       
    private String productInfo;

       
                               
       
    private String idempotencyKey;

       
                                
       
    private String requestHash;


       
           
       
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

       
           
       
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

       
           
                      
       
    private Integer deleted;
}
