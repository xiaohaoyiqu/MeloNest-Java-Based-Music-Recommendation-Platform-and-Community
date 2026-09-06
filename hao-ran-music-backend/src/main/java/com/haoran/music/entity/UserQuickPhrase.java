   
                      
                       
   

package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

   
          
   
@Data
@TableName("user_quick_phrase")
public class UserQuickPhrase implements Serializable {

    private static final long serialVersionUID = 1L;

       
           
       
    @TableId(type = IdType.AUTO)
    private Long id;

       
           
       
    private Long userId;

       
            
       
    private String phrase;

       
                    
       
    private Integer sortOrder;

       
           
       
    private LocalDateTime createTime;

       
           
       
    private LocalDateTime updateTime;
}
