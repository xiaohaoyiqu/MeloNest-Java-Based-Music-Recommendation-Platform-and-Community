package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

   
                                     
  
                      
   
@Data
@TableName("search_index_sync_outbox")
public class SearchIndexSyncOutboxEvent {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String eventId;
    private String resourceType;
    private Long resourceId;
    private String status;
    private Integer attemptCount;
    private Integer maxAttempts;
    private String workerId;
    private LocalDateTime leaseUntil;
    private String errorCategory;
    private LocalDateTime nextRetryTime;
    private LocalDateTime completedAt;
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
