package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;





@Data
@TableName("user_points_record")
public class UserPointsRecord extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;




    @TableId(type = IdType.AUTO)
    private Long id;




    @TableField("user_id")
    private Long userId;




    @TableField("change_type")
    private String changeType;




    @TableField("change_amount")
    private Integer points;




    @TableField("before_points")
    private Integer beforePoints;




    @TableField("after_points")
    private Integer afterPoints;




    @TableField("reason")
    private String reason;




    @TableField("related_id")
    private Long businessId;




    @TableField("description")
    private String description;




    @TableField(exist = false)
    private String businessType;
}