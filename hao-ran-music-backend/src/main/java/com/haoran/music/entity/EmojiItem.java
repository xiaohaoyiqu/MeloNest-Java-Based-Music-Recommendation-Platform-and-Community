package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

   
                      
                      
   
@Data
@TableName("emoji_item")
public class EmojiItem implements Serializable {

    private static final long serialVersionUID = 1L;

       
           
       
    @TableId(type = IdType.AUTO)
    private Long id;

       
            
       
    private Long emojiPackageId;

       
           
       
    private String itemName;

       
           
       
    private String itemCode;

       
              
       
    private String imageUrl;

       
                 
       
    private String gifUrl;

       
         
       
    private String category;

       
         
       
    private Integer sortOrder;

       
           
       
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

       
           
       
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

       
           
       
    @TableLogic
    private Integer deleted;
}
