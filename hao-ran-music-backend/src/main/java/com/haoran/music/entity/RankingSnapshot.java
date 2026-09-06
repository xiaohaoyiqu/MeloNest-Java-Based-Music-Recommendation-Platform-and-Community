package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;






@Data
@TableName("ranking_snapshot")
public class RankingSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String snapshotId;
    private String rankingType;
    private String partitionKey;
    private String ruleVersion;
    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;
    private String status;
    private Boolean isActive;
    private Long sourceFactCount;
    private Integer itemCount;
    private String contentChecksum;
    private String errorCategory;
    private LocalDateTime createTime;
    private LocalDateTime completedAt;
}
