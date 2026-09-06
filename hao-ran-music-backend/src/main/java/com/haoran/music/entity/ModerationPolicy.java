package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;





@Data
@TableName("moderation_policy")
public class ModerationPolicy implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;




    private String policyCode;




    private String policyName;




    private String policyContent;




    private String affectScope;




    private Integer reauditRequired;




    private LocalDateTime effectiveTime;




    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;




    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;




    @TableLogic
    private Integer deleted;
}
