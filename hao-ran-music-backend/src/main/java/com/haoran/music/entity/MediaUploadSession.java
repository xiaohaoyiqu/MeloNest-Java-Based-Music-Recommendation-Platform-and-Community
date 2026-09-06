package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

   
            
  
                      
   
@Data
@TableName("media_upload_session")
public class MediaUploadSession implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_EXPIRED = "EXPIRED";

       
            
       
    @TableId(type = IdType.AUTO)
    private Long id;

       
                   
       
    private String sessionToken;

       
              
       
    private Long ownerId;

       
            
       
    private String purpose;

       
            
       
    private String status;

       
             
       
    private Integer maxFiles;

       
                
       
    private Integer uploadedCount;

       
                    
       
    private String targetType;

       
                    
       
    private Long targetId;

       
              
       
    private LocalDateTime expiresAt;

       
            
       
    private LocalDateTime createTime;

       
            
       
    private LocalDateTime updateTime;
}
