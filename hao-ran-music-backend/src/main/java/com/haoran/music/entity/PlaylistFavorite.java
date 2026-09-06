   
                      
                        
   
package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

   
           
                
   
@Data
@EqualsAndHashCode
@TableName("playlist_favorite")
public class PlaylistFavorite implements Serializable {

    private static final long serialVersionUID = 1L;

       
           
       
    @TableId(type = IdType.AUTO)
    private Long id;

       
           
       
    private Long userId;

       
              
       
    private Long playlistId;

       
                        
                            
       
    @TableLogic
    private Integer deleted;

       
           
       
    private LocalDateTime createTime;

       
                        
       
    private LocalDateTime updateTime;
}
