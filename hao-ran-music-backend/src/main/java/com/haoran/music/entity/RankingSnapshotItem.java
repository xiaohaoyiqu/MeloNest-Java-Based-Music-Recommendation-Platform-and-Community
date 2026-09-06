package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;






@Data
@TableName("ranking_snapshot_item")
public class RankingSnapshotItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String snapshotId;
    private Integer rankPosition;
    private String itemType;
    private Long itemId;
    private BigDecimal score;
    private Long validFactCount;
    private String tieBreaker;
    private LocalDateTime createTime;
}
