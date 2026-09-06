


package com.haoran.music.dto.user;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;




@Data
public class RfmAggregateDTO {

    private Long userId;

    private String username;

    private Integer userStatus;

    private Integer userBanned;

    private Integer userType;

    private Integer riskScore;

    private Integer creditScore;

    private String creatorStatus;

    private LocalDateTime lastVipTime;

    private Integer vipCount;

    private BigDecimal vipAmount;

    private LocalDateTime lastCheckinTime;

    private Integer checkinCount;
}
