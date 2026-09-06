package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

   
           
                      
   
@Data
@TableName("song_artist")
public class SongArtist {

    @TableId(type = IdType.AUTO)
    private Long id;

       
           
       
    private Long songId;

       
           
       
    private Long artistId;

       
                 
       
    private String artistName;

       
                                   
       
    private Integer type;

       
         
       
    private Integer sortOrder;

       
           
       
    private LocalDateTime createTime;
}
