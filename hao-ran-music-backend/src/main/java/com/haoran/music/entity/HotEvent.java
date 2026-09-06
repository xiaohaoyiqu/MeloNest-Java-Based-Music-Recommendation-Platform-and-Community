   
                      
                      
   

package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

   
         
   
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("hot_event")
public class HotEvent {

       
           
       
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

       
           
       
    private String title;

       
           
       
    private String description;

       
          
       
    private String cover;

       
                                     
       
    private String eventType;

       
           
       
    private LocalDate eventDate;

       
         
       
    private String source;

       
           
       
    private String sourceUrl;

       
             
       
    private String fallbackSourceUrl;

       
                                                   
       
    private String sourceType;

       
                     
       
    private String relatedArtists;

       
                     
       
    private String relatedSongs;

       
          
       
    private Integer viewCount;

       
           
       
    private Boolean isFeatured;

       
         
       
    private Integer sortOrder;

       
           
       
    private Boolean isDeleted;

       
           
       
    private LocalDateTime createTime;

       
                                        
       
    private Integer reviewStatus;

       
          
       
    private Long reviewerId;

       
           
       
    private LocalDateTime reviewTime;

       
           
       
    private String reviewRemark;
}
