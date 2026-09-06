package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;






@Data
@TableName("badge_grant_event")
public class BadgeGrantEvent {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventId;
    private String businessKey;
    private Long userId;
    private String badgeType;
    private Integer ruleVersion;
    private String action;
    private String evidenceType;
    private String evidenceId;
    private String evidenceSummary;
    private Long operatorId;
    private String reason;
    private LocalDateTime createTime;
}
