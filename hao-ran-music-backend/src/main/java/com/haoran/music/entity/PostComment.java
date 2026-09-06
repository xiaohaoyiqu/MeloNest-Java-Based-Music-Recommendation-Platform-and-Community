   
                      
                      
   

package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

   
         
   
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("post_comment")
public class PostComment {

       
           
       
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

       
           
       
    private Long postId;

       
             
       
    private Long userId;

       
                    
       
    private Long parentId;

       
              
       
    private Long replyUserId;

       
           
       
    private String content;

       
          
       
    private Integer likeCount;

       
           
       
    private Boolean isDeleted;

       
           
       
    private LocalDateTime createTime;

       
           
       
    private LocalDateTime updateTime;
}
