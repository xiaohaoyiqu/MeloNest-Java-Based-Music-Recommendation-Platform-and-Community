package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;





@Data
@TableName("user_follow")
public class UserFollow implements Serializable {

    private static final long serialVersionUID = 1L;




    @TableId(type = IdType.ASSIGN_ID)
    private Long id;




    private Long followerId;




    private Long followeeId;




    @TableLogic
    private Integer deleted;




    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;




    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
