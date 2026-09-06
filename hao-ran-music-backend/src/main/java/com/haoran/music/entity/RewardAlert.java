   
                      
                         
   

package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

   
            
   
@Data
@TableName("reward_alert")
public class RewardAlert implements Serializable {

    private static final long serialVersionUID = 1L;

       
           
       
    @TableId(type = IdType.AUTO)
    private Long id;

       
           
       
    private Long userId;

       
            
       
    private Long creatorId;

       
                                                       
       
    private String alertType;

       
                                         
       
    private String alertLevel;

       
           
       
    private Integer alertDays;

       
           
       
    private BigDecimal alertAmount;

       
           
       
    private String alertDetail;

       
                    
       
    private Integer isHandled;

       
           
       
    private LocalDateTime handleTime;

       
          
       
    private String handleBy;

       
           
       
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
