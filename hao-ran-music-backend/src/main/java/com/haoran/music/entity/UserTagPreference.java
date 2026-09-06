package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;





@Data
@TableName("user_tag_preference")
public class UserTagPreference extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;




    @TableId(type = IdType.ASSIGN_ID)
    private Long id;




    private Long userId;




    private Long tagId;




    private BigDecimal score;




    private Integer playCount;




    private LocalDateTime lastPlayTime;




    @TableField(exist = false)
    private String tagName;




    @TableLogic
    private Integer deleted;
}
