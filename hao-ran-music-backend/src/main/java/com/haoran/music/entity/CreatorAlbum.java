package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

   
                      
                            
   
@Data
@TableName("creator_album")
public class CreatorAlbum implements Serializable {

    private static final long serialVersionUID = 1L;

       
            
       
    @TableId(type = IdType.AUTO)
    private Long id;

       
                
       
    private Long userId;

       
             
       
    private String albumName;

       
                                         
       
    private Integer albumType;

       
                
       
    private String coverUrl;

       
             
       
    private String description;

       
                    
       
    private String tags;

       
                                           
       
    private Integer language;

       
             
       
    private LocalDate releaseDate;

       
                                
       
    private Integer status;

       
                   
       
    private Integer isPublishDateSet;

       
                      
       
    private Integer autoCreateSong;

       
                
       
    private Integer songCount;

       
                
       
    private Integer totalDuration;

       
             
       
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

       
             
       
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

       
             
       
    @TableLogic
    private Integer deleted;
}
