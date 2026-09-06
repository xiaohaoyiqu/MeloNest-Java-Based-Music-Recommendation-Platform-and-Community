package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;






@Data
@TableName("song_credit")
public class SongCredit {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long songId;
    private Long artistId;
    private String roleCode;
    private Integer creditVersion;
    private String displayNameSnapshot;
    private Integer sortOrder;
    private String sourceType;
    private Long sourceId;
    private String acceptanceStatus;
    private String rightsScope;
    private String revenueShareReference;
    private Long reviewedBy;
    private String changeReason;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
