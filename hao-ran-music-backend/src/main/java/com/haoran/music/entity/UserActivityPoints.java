package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;





@Data
@TableName("user_points_record")
public class UserActivityPoints extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;




    @TableId(type = IdType.ASSIGN_ID)
    private Long id;




    @TableField("user_id")
    private Long userId;




    @TableField("change_amount")
    private Integer points;




    @TableField("before_points")
    private Integer beforePoints;




    @TableField("after_points")
    private Integer balance;







    @TableField("change_type")
    private String type;




    @TableField("description")
    private String description;




    @TableField("related_id")
    private Long relatedId;




    @TableField(exist = false)
    private String relatedType;




    @TableField(exist = false)
    private Integer isExpired;



    @TableField(exist = false)
    private Integer activityScore;




    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(exist = false)
    private LocalDateTime expireTime;




    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("create_time")
    private LocalDateTime createTime;




    @TableField(exist = false)
    private Integer deleted;
}
