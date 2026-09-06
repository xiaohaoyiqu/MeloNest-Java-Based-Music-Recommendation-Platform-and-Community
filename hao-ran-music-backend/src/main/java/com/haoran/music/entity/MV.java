package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

   
                      
                     
   
@Data
@TableName("mv")
public class MV implements Serializable {

    private static final long serialVersionUID = 1L;

       
            
       
    @TableId(type = IdType.AUTO)
    private Long id;

       
           
       
    private String name;

       
         
       
    private String cover;

       
         
       
    private String description;

       
           
       
    private Long playCount;

       
           
       
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

       
           
       
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

       
           
       
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate publishDate;

       
            
       
    private Integer duration;

       
             
       
    private Long songId;

       
                
       
    private Long artistId;

       
                        
       
    private String artistIds;

       
                     
       
    private String artistNames;

       
                    
       
    private Integer status;

       
                        
       
    @TableLogic
    private Integer deleted;

       
          
       
    private Long commentCount;

       
          
       
    private Long likeCount;

       
          
       
    private Long favoriteCount;

       
          
       
    private Long shareCount;

       
               
       
    private String tags;

       
                   
       
    @TableField("size_360p")
    private Long size360p;

       
                 
       
    @TableField("size_720p")
    private Long size720p;

       
                 
       
    @TableField("size_1080p")
    private Long size1080p;

       
                 
       
    @TableField("size_2160p")
    private Long size2160p;

       
                
       
    @TableField("url_360p")
    private String url360p;

       
                    
       
    @TableField("url_720p")
    private String url720p;

       
                     
       
    @TableField("url_1080p")
    private String url1080p;

       
                     
       
    @TableField("url_2160p")
    private String url2160p;

       
                            
       
    public Long getSize() {
        return size360p;
    }

       
                             
       
    public String getUrl() {
        return url360p;
    }
}
