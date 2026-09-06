



package com.haoran.music.vo.badge;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;




@Data
public class UserBadgeVO implements Serializable {

    private static final long serialVersionUID = 1L;




    private Long id;




    private Long userId;




    private String badgeType;




    private String badgeName;




    private String badgeIcon;




    private String badgeColor;




    private String position;




    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;




    private Integer remainingDays;




    private Boolean isEquipped;




    private String description;




    private String obtainMethod;




    private String rarity;




    private String category;




    private Integer rarityOrder;




    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
