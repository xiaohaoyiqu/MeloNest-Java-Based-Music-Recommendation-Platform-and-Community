package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

   
                      
                         
   
@Data
@TableName("song_play_record")
public class SongPlayRecord implements Serializable {

    private static final long serialVersionUID = 1L;

       
         
       
    @TableId(type = IdType.AUTO)
    private Long id;

       
           
       
    private Long songId;

       
           
       
    private Long userId;

       
              
       
    private Integer playDuration;

       
           
       
    private LocalDateTime playTime;

       
           
                              
       
    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
