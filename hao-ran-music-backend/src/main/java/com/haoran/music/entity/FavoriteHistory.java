package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;





@Data
@TableName("favorite_history")
public class FavoriteHistory implements Serializable {

    private static final long serialVersionUID = 1L;




    @TableId(type = IdType.ASSIGN_ID)
    private Long id;




    private Long userId;




    private Long songId;




    private Integer actionType;




    private LocalDateTime actionTime;




    private String songName;




    private String artistNames;




    private String cover;




    @TableLogic
    private Integer deleted;




    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
