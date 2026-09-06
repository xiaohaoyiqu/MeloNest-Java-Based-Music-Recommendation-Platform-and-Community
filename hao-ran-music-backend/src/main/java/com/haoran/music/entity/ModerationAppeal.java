package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;





@Data
@TableName("moderation_appeal")
public class ModerationAppeal implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;




    private Long moderationId;




    private Long userId;




    private String appealReason;




    private String appealContent;




    private String attachments;




    private Integer status;




    private Long reviewerId;




    private String decisionReason;




    private LocalDateTime submitTime;




    private LocalDateTime processTime;




    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
