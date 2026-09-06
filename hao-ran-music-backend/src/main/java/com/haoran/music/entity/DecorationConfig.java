




package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;





@Data
@TableName("decoration_config")
public class DecorationConfig implements Serializable {

    private static final long serialVersionUID = 1L;




    @TableId(type = IdType.AUTO)
    private Long id;




    private String decorationId;


    private Long creatorId;


    private String sourceType;




    private String decorationType;




    private String decorationName;




    private String description;




    private String iconUrl;




    private String previewUrl;




    private String styleConfig;




    private String rarity;




    private String obtainType;




    private String obtainCondition;




    private Integer pointsCost;




    private Integer cashPrice;




    private Integer isPermanent;




    private Integer durationDays;




    private Integer sortOrder;




    private Integer isEnabled;


    private String reviewStatus;

    private LocalDateTime submitTime;

    private Long reviewerId;

    private LocalDateTime reviewTime;

    private String reviewReason;


    private Integer contentVersion;




    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;




    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;




    @TableLogic
    private Integer deleted;
}
