package com.haoran.music.common.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;





@Data
public class ModerationRecordVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String targetType;

    private String targetTypeName;

    private Long targetId;

    private Long submitterId;

    private String submitterName;

    private String submitterSource;

    private Long assignedModeratorId;

    private String moderatorName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime assignedTime;

    private Integer moderatorOnlineStatus;

    private Integer priority;

    private String status;

    private String statusName;

    private Long reviewerId;

    private String reviewerName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reviewTime;

    private String reviewResult;

    private String reviewReason;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    private Long waitingMinutes;

    private Long processingMinutes;

    private String title;

    private String description;
}
