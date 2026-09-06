package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

   
                      
                        
   
@Data
@TableName("creator_earnings")
public class CreatorEarnings extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

       
           
       
    @TableId(type = IdType.AUTO)
    private Long id;

       
            
       
    private Long userId;

       
           
       
    private Long workId;

       
                                               
       
    private String workType;

       
                                                    
       
    private String earningsType;

       
                 
       
    private Long earningsAmount;

       
           
       
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

       
                        
       
    @TableLogic
    private Integer deleted;
}
