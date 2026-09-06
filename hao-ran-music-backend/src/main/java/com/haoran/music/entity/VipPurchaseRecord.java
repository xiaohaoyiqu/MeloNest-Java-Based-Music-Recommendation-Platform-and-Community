   
                      
                          
   

package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

   
            
   
@Data
@TableName("order_vip_record")
public class VipPurchaseRecord implements Serializable {

    private static final long serialVersionUID = 1L;

       
           
       
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

       
           
       
    private Long userId;

       
             
       
    private Long orderId;

       
            
       
    private Integer vipDays;

       
              
       
    private LocalDateTime startTime;

       
              
       
    private LocalDateTime endTime;

       
           
       
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

}
