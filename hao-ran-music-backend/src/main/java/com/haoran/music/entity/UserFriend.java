package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

   
                      
                         
   
@Data
@TableName("user_friend")
public class UserFriend extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

       
           
       
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

       
           
       
    private Long userId;

       
             
       
    private Long friendId;

       
             
       
    private String friendGroup;

       
                     
       
    @TableField(exist = false)
    private String groupName;

       
           
       
    private String remark;

       
                                                
       
    private String status;

       
                                   
       
    private String specialMark;

       
             
       
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime friendSince;

       
                        
       
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
