


package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;




@Data
@TableName("play_event_dead_letter")
public class PlayEventDeadLetter implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventId;
    private String topic;
    private Integer partitionId;
    private Long offsetValue;
    private String messageKey;
    private String payload;
    private Integer retryCount;
    private String errorMessage;
    private String status;
    private Long handlerId;
    private String handleReason;
    private LocalDateTime firstFailedAt;
    private LocalDateTime lastFailedAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
