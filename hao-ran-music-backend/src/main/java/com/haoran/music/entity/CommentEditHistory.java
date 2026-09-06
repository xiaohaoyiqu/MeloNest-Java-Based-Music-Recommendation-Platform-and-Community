package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

   
                      
                         
   
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("comment_edit_history")
public class CommentEditHistory extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

       
             
       
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

       
           
       
    private Long commentId;

       
           
       
    private String originalContent;

       
            
       
    private String editedContent;

       
             
       
    private Long editorId;

       
             
       
    private String editorNickname;

       
                 
       
    private String editorIp;

       
           
       
    private LocalDateTime editTime;

       
                        
       
    @TableLogic
    private Integer deleted;
}
