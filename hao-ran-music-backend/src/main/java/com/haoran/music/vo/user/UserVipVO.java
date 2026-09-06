package com.haoran.music.vo.user;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

   
                      
                         
   
@Data
public class UserVipVO {

    private String userId;

    private String vipLevel;

    private String vipLevelName;

    private String vipStatus;

    private String vipStatusName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime vipStartTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime vipExpireTime;

    private String remainingDays;

    private String vip;

    private String lifetimeVip;

    private String autoRenew;

    private String totalVipDays;

    private String totalSpending;

    private java.util.List<String> privileges;
}
