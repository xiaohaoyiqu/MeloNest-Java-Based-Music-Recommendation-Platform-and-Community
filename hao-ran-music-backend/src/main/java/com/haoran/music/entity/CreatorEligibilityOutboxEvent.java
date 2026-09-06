


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
@TableName("creator_eligibility_outbox")
public class CreatorEligibilityOutboxEvent {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long creatorId;
    private Long eventVersion;
    private Integer schemaVersion;
    private String eventType;
    private String oldStatus;
    private String newStatus;
    private Integer isCreator;
    private String creatorType;
    private Long operatorId;
    private String reason;
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
