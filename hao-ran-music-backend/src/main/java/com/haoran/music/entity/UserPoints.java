package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;





@Data
@TableName("user_points")
public class UserPoints extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;




    @TableId(type = IdType.AUTO)
    private Long id;




    @TableField("user_id")
    private Long userId;




    @TableField("available_points")
    private Integer currentPoints;




    @TableField("total_points")
    private Integer totalPoints;




    @TableField("frozen_points")
    private Integer frozenPoints;




    @TableField(exist = false)
    private Integer todayPoints;




    @TableField(exist = false)
    private Integer monthPoints;




    @JsonFormat(pattern = "yyyy-MM-dd")
    @TableField(exist = false)
    private LocalDate lastResetDate;




    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(exist = false)
    private LocalDateTime lastUpdateTime;
}