package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;





@Data
@TableName("user_level")
public class UserLevel extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;




    @TableId(type = IdType.ASSIGN_ID)
    private Long id;




    private Long userId;




    private Integer level;




    @TableField("exp")
    private Integer currentExp;




    @TableField("exp_to_next")
    private Integer nextLevelExp;




    @TableField(exist = false)
    private Integer totalExp;




    @TableField(exist = false)
    private String levelTitle;
}
