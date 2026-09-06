package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

   
                      
                       
   
@Data
@TableName("user_favorite")
public class UserFavorite implements Serializable {

    private static final long serialVersionUID = 1L;

       
         
       
    @TableId(type = IdType.AUTO)
    private Long id;

       
           
       
    private Long userId;

       
                                         
       
    private String targetType;

       
           
       
    private Long targetId;

       
           
                              
       
    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
