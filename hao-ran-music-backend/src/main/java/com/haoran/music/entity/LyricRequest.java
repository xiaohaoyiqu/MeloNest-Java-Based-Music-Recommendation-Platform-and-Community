package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

   
                      
                         
   
@Data
@TableName("lyric_request")
public class LyricRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

       
           
       
    private Long userId;

       
          
       
    private String userName;

       
           
       
    private Long songId;

       
           
       
    private String songName;

       
           
       
    private String originalLyric;

       
            
       
    private String correctedLyric;

       
           
       
    private String changeDescription;

       
                                         
       
    private Integer changeType;

       
                             
       
    private Integer status;

       
            
       
    private Long reviewerId;

       
           
       
    private LocalDateTime reviewTime;

       
           
       
    private String reviewReason;

       
            
       
    private Integer isApplied;

       
           
       
    private LocalDateTime appliedTime;

       
           
       
    private LocalDateTime createTime;

       
           
       
    private LocalDateTime updateTime;

       
           
       
    @TableLogic
    private Integer deleted;
}
