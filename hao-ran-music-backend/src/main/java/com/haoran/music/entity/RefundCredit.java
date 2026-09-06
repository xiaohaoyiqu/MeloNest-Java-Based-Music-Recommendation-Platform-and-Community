package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

   
                      
                        
   
@Data
@TableName("refund_credit")
public class RefundCredit extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

       
           
       
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

       
           
       
    private Long userId;

       
                    
       
    private Integer creditScore;

       
            
       
    private Integer totalRefundCount;

       
              
       
    private Integer unreasonableRefundCount;

       
             
       
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastUpdateTime;

       
                       
       
    private Integer deleted;
}
