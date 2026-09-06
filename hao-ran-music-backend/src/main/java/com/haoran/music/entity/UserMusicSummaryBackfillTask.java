


package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;




@Data
@TableName("user_music_summary_backfill_task")
public class UserMusicSummaryBackfillTask {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String taskId;
    private String businessKey;
    private Long operatorId;
    private String summaryType;
    private String startPeriod;
    private String endPeriod;
    private String cursorPeriod;
    private Integer totalPeriods;
    private Integer completedPeriods;
    private Integer affectedRows;
    private String failedPeriodsJson;
    private String status;
    private Integer attemptCount;
    private Integer maxAttempts;
    private String errorMessage;
    private LocalDateTime nextRetryTime;
    private LocalDateTime startedAt;
    private String workerId;
    private LocalDateTime leaseUntil;
    private LocalDateTime completedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
