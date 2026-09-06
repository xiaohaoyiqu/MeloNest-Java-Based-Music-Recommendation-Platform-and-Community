package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

   
                      
                     
   
@Data
@TableName("lyric")
public class Lyric implements Serializable {

    private static final long serialVersionUID = 1L;

       
           
       
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

       
           
       
    private Long songId;

       
                           
       
    private Integer lyricType;

       
                            
       
    private String language;

       
                      
       
    private String content;

       
                                  
       
    private String source;

       
            
       
    private String sourceUrl;

       
            
       
    private Long creatorId;

       
                     
       
    private Integer status;

       
           
       
    @TableLogic
    private Integer deleted;

       
           
       
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

       
           
       
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
