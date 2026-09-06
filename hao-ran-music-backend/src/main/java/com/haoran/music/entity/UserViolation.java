package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;





@Data
@TableName("user_violation")
public class UserViolation implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;




    private Long userId;




    private String violationType;




    private Integer violationLevel;




    private String contentType;




    private Long contentId;




    private String description;




    private Long handlerId;




    private String penaltyType;




    private String penaltyValue;




    private Integer isResolved;




    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
