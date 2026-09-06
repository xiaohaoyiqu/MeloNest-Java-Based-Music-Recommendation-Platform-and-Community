package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

   
                      
                     
   
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("comment")
public class Comment extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

       
           
       
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

       
             
       
    private Long userId;

       
                                
       
    private Integer targetType;

       
           
       
    private Long targetId;

       
                  
       
    private Long parentId;

       
              
       
    private Long replyUserId;

       
           
       
    private String content;

       
          
       
    private Long likeCount;

       
          
       
    private Long replyCount;

       
                    
       
    private Integer isPinned;

       
               
       
    private String ip;

       
                              
       
    private Integer status;

       
                        
       
    @TableLogic
    private Integer deleted;

       
                 
       
    private Integer editCount;
}
