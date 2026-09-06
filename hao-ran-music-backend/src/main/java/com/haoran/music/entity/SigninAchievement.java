




package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;





@Data
@TableName("signin_achievement")
public class SigninAchievement implements Serializable {

    private static final long serialVersionUID = 1L;




    @TableId(type = IdType.AUTO)
    private Long id;




    private Integer days;




    private String achievementName;




    private String achievementDesc;




    private Integer rewardPoints;




    private Long rewardDecorationId;




    private Long rewardBadgeId;




    private Integer rewardVipDays;




    private String iconUrl;




    private String badgeUrl;




    private Integer sortOrder;




    private Integer status;




    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;




    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;





    private Integer deleted;
}
