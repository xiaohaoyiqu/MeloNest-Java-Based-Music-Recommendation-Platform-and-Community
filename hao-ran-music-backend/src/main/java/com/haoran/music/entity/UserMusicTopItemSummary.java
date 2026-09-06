   
                      
   
package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

   
                 
   
@Data
@TableName("user_music_top_item_summary")
public class UserMusicTopItemSummary {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String periodType;
    private LocalDate periodKey;
    private String itemType;
    private Long itemId;
    private String itemName;
    private String artistNames;
    private String itemCover;
    private Long playCount;
    private Integer publicStatsEligible;
    private String calculationVersion;
    private LocalDateTime dataUntil;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
