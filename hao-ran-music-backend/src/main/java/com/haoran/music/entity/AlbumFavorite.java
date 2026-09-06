   
                      
                      
   
package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

   
         
                         
  
         
                                  
                            
                                         
   
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("album_favorite")
public class AlbumFavorite implements Serializable {

    private static final long serialVersionUID = 1L;

       
           
       
    @TableId(type = IdType.AUTO)
    private Long id;

       
           
       
    private Long userId;

       
           
       
    private Long albumId;

       
           
       
    private LocalDateTime createTime;

       
                        
                            
       
    @TableLogic
    private Integer deleted;

       
                        
       
    private LocalDateTime updateTime;
}
