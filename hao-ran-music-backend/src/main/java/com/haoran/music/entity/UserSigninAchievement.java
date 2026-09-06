   
                      
                          
   

package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

   
             
              
   
@Data
@TableName("user_signin_achievement")
public class UserSigninAchievement implements Serializable {

    private static final long serialVersionUID = 1L;

       
           
       
    @TableId(type = IdType.AUTO)
    private Long id;

       
           
       
    private Long userId;

       
           
       
    private Long achievementId;

       
           
       
    private Integer days;

       
                          
       
    private Integer isRewarded;

       
           
       
    private LocalDateTime rewardTime;

       
           
       
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

       
           
       
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
