package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;





@Data
@TableName("music_tag")
public class MusicTag extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;




    @TableId(type = IdType.ASSIGN_ID)
    private Long id;




    private String name;




    private String category;




    private String icon;




    private String color;




    private String description;




    private Integer useCount;




    private Integer sortOrder;




    private Integer isHot;




    @TableLogic
    private Integer deleted;
}
