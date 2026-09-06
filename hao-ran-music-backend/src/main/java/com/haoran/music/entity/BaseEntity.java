package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;





@Data
public abstract class BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;




    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    protected LocalDateTime createTime;




    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    protected LocalDateTime updateTime;




    @TableField(fill = FieldFill.INSERT, exist = false)
    protected String createBy;




    @TableField(fill = FieldFill.INSERT_UPDATE, exist = false)
    protected String updateBy;
}
