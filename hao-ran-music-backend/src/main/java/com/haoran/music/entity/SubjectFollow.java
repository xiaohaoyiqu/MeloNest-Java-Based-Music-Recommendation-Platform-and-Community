package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;






@Data
@TableName("subject_follow")
public class SubjectFollow {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long followerUserId;
    private String targetType;
    private Long targetId;
    private String status;
    private String sourceType;
    private Long sourceId;
    private Boolean contributesPublicStats;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
