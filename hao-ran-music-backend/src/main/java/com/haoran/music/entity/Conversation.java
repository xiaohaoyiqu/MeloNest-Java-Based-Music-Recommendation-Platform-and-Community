   
                      
                     
   

package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

   
        
                  
   
@Data
@TableName("conversation")
public class Conversation implements Serializable {

    private static final long serialVersionUID = 1L;

       
           
       
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

       
             
       
    @TableField("user_a_id")
    private Long userAId;

       
             
       
    @TableField("user_b_id")
    private Long userBId;

       
               
       
    @TableField("last_message")
    private String lastMessage;

       
               
       
    @TableField("last_message_type")
    private String lastMessageType;

       
               
       
    @TableField("last_message_time")
    private LocalDateTime lastMessageTime;

       
                
       
    @TableField("user_a_unread_count")
    private Integer userAUnreadCount;

       
                
       
    @TableField("user_b_unread_count")
    private Integer userBUnreadCount;

       
                           
       
    @TableField("user_a_pinned")
    private Integer userAPinned;

       
                           
       
    @TableField("user_b_pinned")
    private Integer userBPinned;

       
                          
       
    @TableField("user_a_blocked")
    private Integer userABlocked;

       
                          
       
    @TableField("user_b_blocked")
    private Integer userBBlocked;

       
                                    
       
    @TableField("status")
    private String status;

       
           
       
    @TableField("is_deleted")
    @TableLogic
    private Integer isDeleted;

       
           
       
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

       
           
       
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
