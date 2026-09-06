   
                      
                         
   

package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

   
            
   
@Data
@TableName("reward_daily_limit")
public class RewardDailyLimit implements Serializable {

    private static final long serialVersionUID = 1L;

       
           
       
    @TableId(type = IdType.AUTO)
    private Long id;

       
           
       
    private Long userId;

       
           
       
    private LocalDate limitDate;

       
           
       
    private Integer rewardCount;

       
            
       
    private java.math.BigDecimal rewardAmount;

       
           
       
    @TableField(fill = FieldFill.INSERT)
    private java.time.LocalDateTime createTime;

       
           
       
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private java.time.LocalDateTime updateTime;
}
