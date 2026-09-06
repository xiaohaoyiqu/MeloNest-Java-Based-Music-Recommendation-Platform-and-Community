package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;





@Data
@TableName("moderation_record")
public class ModerationRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;




    private String targetType;




    private Long targetId;




    private Long submitterId;




    private String submitterSource;




    private Long assignedModeratorId;




    private LocalDateTime assignedTime;




    private Integer moderatorOnlineStatus;




    private Integer priority;




    private String status;




    private Long reviewerId;




    private LocalDateTime reviewTime;




    private String reviewResult;




    private String reviewReason;




    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;




    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;
}
