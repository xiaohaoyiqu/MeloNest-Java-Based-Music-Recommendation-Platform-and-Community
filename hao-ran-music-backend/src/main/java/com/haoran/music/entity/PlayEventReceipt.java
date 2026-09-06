   
                      
   
package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

   
            
  
                                             
                 
   
@Data
@TableName("play_event_receipt")
public class PlayEventReceipt implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventId;
    private Long userId;
    private String songId;
    private Integer isLocal;
    private String status;
    private LocalDateTime processedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
