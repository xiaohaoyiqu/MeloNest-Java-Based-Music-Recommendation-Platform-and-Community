package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;





@Data
@TableName("comment_like")
public class CommentLike implements Serializable {

    private static final long serialVersionUID = 1L;




    @TableId(type = IdType.ASSIGN_ID)
    private Long id;




    private Long userId;




    private Long commentId;




    private Integer isLike;




    @TableLogic
    private Integer deleted;




    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;




    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
