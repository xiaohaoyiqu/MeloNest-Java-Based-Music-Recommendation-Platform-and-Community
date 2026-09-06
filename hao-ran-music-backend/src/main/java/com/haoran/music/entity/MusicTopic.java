   
                      
                      
   

package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

   
         
   
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("topic")
public class MusicTopic {

       
           
       
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

       
           
       
    private String name;

       
           
       
    private String description;

       
           
       
    private String cover;

       
         
       
    private String category;

       
          
       
    private Integer postCount;

       
          
       
    private Integer followerCount;

       
           
       
    private Boolean isHot;

       
         
       
    private Integer sortOrder;

       
           
       
    private Boolean isDeleted;

       
           
       
    private LocalDateTime createTime;

       
           
       
    private LocalDateTime updateTime;
}
