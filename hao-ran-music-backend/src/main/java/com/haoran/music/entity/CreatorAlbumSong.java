package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

   
                      
                                  
   
@Data
@TableName("creator_album_song")
public class CreatorAlbumSong implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

       
            
       
    private Long albumId;

       
                                 
       
    private Long songId;

       
                              
       
    private String songName;

       
                  
       
    private Integer position;

       
                                      
       
    private Integer isSingle;

       
                              
       
    private String audioUrl;

       
                                               
       
    private Integer audioQuality;

       
                   
       
    private Long fileSize;

       
               
       
    private Integer duration;

       
                   
       
    private Integer bitrate;

       
                 
       
    private Integer sampleRate;

       
                            
       
    private String format;

       
                                   
       
    private Integer uploadType;

       
                        
       
    private String fileUrls;

       
                 
       
    private String zipFileUrl;

       
                                               
       
    private String source;

       
             
       
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

       
             
       
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

       
             
       
    @TableLogic
    private Integer deleted;
}
