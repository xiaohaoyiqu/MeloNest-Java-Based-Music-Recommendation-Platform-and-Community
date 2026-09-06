


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
@TableName("user_music_report_snapshot")
public class UserMusicReportSnapshot {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String reportType;
    private String periodKey;
    private String contentJson;
    private LocalDateTime generatedAt;
    private LocalDateTime dataUntil;
    private String calculationVersion;
    private LocalDateTime expireTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
