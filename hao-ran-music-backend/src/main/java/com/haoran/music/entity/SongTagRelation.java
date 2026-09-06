package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

   
                      
                         
   
@Data
@TableName("song_tag_relation")
public class SongTagRelation extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

       
           
       
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

       
           
       
    private Long songId;

       
           
       
    private Long tagId;

       
                      
       
    private Long userId;

       
                        
       
    private String source;

       
           
       
    @TableLogic
    private Integer deleted;
}
