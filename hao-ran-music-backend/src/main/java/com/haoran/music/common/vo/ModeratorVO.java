package com.haoran.music.common.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

   
                      
                       
   
@Data
public class ModeratorVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String username;

    private String nickname;

    private String avatar;

    private String moderatorStatus;

    private String moderatorNote;

    private Integer isOnline;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastOnlineTime;

    private Integer todayReviewCount;

    private Integer totalReviewCount;

    private Integer dailyQuota;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastReviewTime;

    private Integer currentTaskCount;

    private Boolean hasQuota;

    private String onlineStatus;

    private Double todayCompletionRate;
}
