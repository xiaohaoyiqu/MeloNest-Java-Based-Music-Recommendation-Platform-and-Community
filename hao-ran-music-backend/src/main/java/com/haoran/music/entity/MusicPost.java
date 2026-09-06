   
                      
                      
   

package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

   
         
   
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("post")
public class MusicPost {

       
           
       
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

       
             
       
    private Long userId;

       
           
       
    private String content;

       
                 
       
    private String images;

       
                     
       
    private String videoInfo;

       
                                     
       
    private String resourceType;

       
             
       
    private Long resourceId;

       
                   
       
    private String topics;

       
                                                      
       
    private String postType;

       
                                    
       
    private String visibility;

       
             
       
    private Boolean isListenDiary;

       
                 
       
    private String listenData;

       
          
       
    private Integer likeCount;

       
          
       
    private Integer commentCount;

       
                            
       
    private Integer allowComment;

       
                 
       
    private Boolean officialCommentClosed;

       
          
       
    private Integer shareCount;

       
           
       
    private Boolean isDeleted;

       
           
       
    private LocalDateTime createTime;

       
           
       
    private LocalDateTime updateTime;
}
