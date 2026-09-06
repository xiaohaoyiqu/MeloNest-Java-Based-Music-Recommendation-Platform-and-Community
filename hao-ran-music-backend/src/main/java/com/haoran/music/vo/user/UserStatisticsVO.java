package com.haoran.music.vo.user;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;





@Data
public class UserStatisticsVO {

    private Long userId;

    private String username;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate statDate;

    private Integer playCount;

    private Integer playDuration;

    private String playDurationFormatted;

    private Integer uniqueSongCount;

    private Integer likeCount;

    private Integer favoriteCount;

    private Integer commentCount;

    private Integer activeDuration;

    private Boolean isAbnormal;

    private String abnormalReason;
}
