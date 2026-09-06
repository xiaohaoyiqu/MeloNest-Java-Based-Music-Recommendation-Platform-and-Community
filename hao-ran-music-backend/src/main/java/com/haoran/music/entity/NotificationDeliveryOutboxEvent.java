package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

   
                                                  
  
                      
   
@Data
@TableName("notification_delivery_outbox")
public class NotificationDeliveryOutboxEvent {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String eventId;
    private Long notificationId;
    private Long recipientId;
    private String eventType;
    private Integer unreadDelta;
    private String status;
    private Integer attemptCount;
    private Integer maxAttempts;
    private String workerId;
    private LocalDateTime leaseUntil;
    private String errorCategory;
    private String errorMessage;
    private LocalDateTime nextRetryTime;
    private LocalDateTime deliveredAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
