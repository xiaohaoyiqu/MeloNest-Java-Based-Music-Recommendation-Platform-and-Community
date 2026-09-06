   
                      
   
package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

   
               
   
@Data
@TableName("notification_broadcast_task")
public class NotificationBroadcastTask {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String taskId;
    private Long operatorId;
    private String title;
    private String content;
    private String link;
    private String coverUrl;
    private Long cursorUserId;
    private Integer totalUsers;
    private Integer successCount;
    private Integer failCount;
    private Integer batchCount;
    private String status;
    private Integer attemptCount;
    private Integer maxAttempts;
    private String errorMessage;
    private LocalDateTime nextRetryTime;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
