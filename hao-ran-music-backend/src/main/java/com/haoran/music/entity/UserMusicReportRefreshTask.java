


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
@TableName("user_music_report_refresh_task")
public class UserMusicReportRefreshTask {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String taskId;
    private String businessKey;
    private Long userId;
    private Long operatorId;
    private String reportType;
    private String periodKey;
    private String status;
    private Integer attemptCount;
    private Integer maxAttempts;
    private String errorMessage;
    private String resultJson;
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
