   
                      
   
package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

   
                                 
   
@Data
@TableName("user_music_monthly_summary")
public class UserMusicMonthlySummary {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;
    private LocalDate statMonth;
    private Integer playCount;
    private Integer validPlayCount;
    private Long playSeconds;
    private Integer uniqueSongCount;
    private Integer activeDays;
    private BigDecimal avgValence;
    private BigDecimal avgEnergy;
    private Integer activeMinutes;
    private Long topSongId;
    private String topSongName;
    private String topSongArtistNames;
    private Integer publicStatsEligible;
    private BigDecimal featureCoverage;
    private String calculationVersion;
    private LocalDateTime dataUntil;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
