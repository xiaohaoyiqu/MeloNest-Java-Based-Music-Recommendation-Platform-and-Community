package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

   
                      
                      
   
@Data
@TableName("creator")
public class Creator implements Serializable {

    private static final long serialVersionUID = 1L;

       
         
       
    @TableId(type = IdType.AUTO)
    private Long id;

       
           
       
    private Long userId;

       
            
       
    private String creatorType;

       
         
       
    private String status;

       
          
       
    private Long fansCount;

       
           
       
    private String creatorNote;

       
           
       
    private LocalDateTime creatorApplyTime;

       
           
                              
       
    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

       
                        
                                     
                             
       
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
