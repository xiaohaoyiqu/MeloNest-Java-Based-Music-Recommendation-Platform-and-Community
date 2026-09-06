package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

   
                  
  
                      
   
@Data
@TableName("badge_rule")
public class BadgeRule {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String badgeType;
    private Integer ruleVersion;
    private String badgeName;
    private String description;
    private String obtainMethod;
    private String category;
    private String rarity;
    private String iconFallback;
    private String badgeColor;
    private String defaultPosition;
    private String triggerType;
    private Long thresholdValue;
    private String evidenceType;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private String visibility;
    private Long assetId;
    private Long replacementOf;
    private Integer grantDays;
    private String status;
    private Long createdBy;
    private String changeReason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
