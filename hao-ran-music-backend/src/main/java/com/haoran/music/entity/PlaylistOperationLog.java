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
@TableName("playlist_operation_log")
public class PlaylistOperationLog implements Serializable {

    private static final long serialVersionUID = 1L;

       
         
       
    @TableId(type = IdType.AUTO)
    private Long id;

       
           
       
    private Long playlistId;

       
             
       
    private Long userId;

       
                           
       
    private String operationType;

       
           
       
    private Long songId;

       
               
       
    private String songName;

       
           
       
    private String description;

       
           
                              
       
    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
