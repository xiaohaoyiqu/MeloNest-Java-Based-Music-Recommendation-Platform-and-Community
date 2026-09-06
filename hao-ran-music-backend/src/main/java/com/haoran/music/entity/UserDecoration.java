   
                      
                      
   

package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

   
         
                
   
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_decoration")
public class UserDecoration extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

       
           
       
    @TableId(type = IdType.AUTO)
    private Long id;

       
           
       
    private Long userId;

       
                                                                   
       
    private String decorationType;

       
                 
       
    private String decorationId;

       
           
       
    private String decorationName;

       
                        
       
    private Integer isEquipped;

       
           
       
    private LocalDateTime obtainTime;

       
                     
       
    private LocalDateTime expireTime;

       
                                                       
       
    private String source;

       
           
       
    private String sourceDescription;

       
                                      
       
    private String rarity;

       
           
       
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

       
           
       
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

       
           
       
    @TableLogic
    private Integer deleted;
}
