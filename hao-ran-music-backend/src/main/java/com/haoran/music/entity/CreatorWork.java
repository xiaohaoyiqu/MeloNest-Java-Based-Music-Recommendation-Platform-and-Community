package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

   
                      
                        
   
@Data
@TableName("creator_work")
public class CreatorWork implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

       
              
       
    private Long userId;

       
           
       
    private String workName;

       
           
       
    private String coverUrl;

       
              
       
    private String fileUrl;

                                                       

       
                                             
       
    private Integer detectedQuality;

       
               
       
    private Long fileSize;

       
              
       
    private Integer duration;

       
                
       
    private Integer bitrate;

       
              
       
    private Integer sampleRate;

       
                         
       
    private String audioFormat;

                                                        

       
                             
       
    private Integer uploadType;

       
                       
       
    private String fileUrls;

       
               
       
    private String zipFileUrl;

                                                       

       
           
       
    private String description;

       
                                       
       
    private String submittedLyric;

       
         
       
    private String tags;

       
           
       
    private Integer language;

       
                             
       
    private Integer status;

       
            
       
    private Long reviewerId;

       
           
       
    private LocalDateTime reviewTime;

       
           
       
    private String reviewReason;

       
           
       
    private LocalDateTime publishTime;

       
           
       
    private Long playCount;

       
          
       
    private Integer likeCount;

       
          
       
    private Integer collectCount;

       
           
       
    private LocalDateTime createTime;

       
           
       
    private LocalDateTime updateTime;

       
           
       
    @TableLogic
    private Integer deleted;

                                                         

       
             
       
    private LocalDate copyrightExpireDate;

       
                                   
       
    private Integer copyrightStatus;

                                                         

       
                      
       
    private Integer isPaid;

       
                
       
    private Long paidResourceId;

       
           
       
    private BigDecimal price;

       
                         
       
    private Integer subscribePeriod;

                                                       

       
                      
       
    private Integer allowDownload;

       
                      
       
    private Integer allowComment;

       
                      
       
    private Integer allowShare;

                                                     

       
                                  
       
    private Integer workType;

       
                           
       
    private Long autoSongId;

       
                                                        
       
    @TableField(exist = false)
    private Long albumId;

       
                                                          
       
    @TableField(exist = false)
    private String albumName;
}
