package com.haoran.music.vo.user;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;





@Data
public class UserProfileVO {

    private Long userId;

    private Integer userType;

    private String userTypeName;

    private String userSegment;

    private String lifecycleStage;

    private Integer ltvScore;

    private Integer churnProbability;



    private List<String> preferredGenres;

    private List<String> preferredArtists;

    private List<String> preferredLanguages;



    private Double avgDailyDurationHours;

    private Integer peakActiveHour;

    private Integer totalActiveDays;

    private Double totalPlayDurationHours;

    private Integer totalPlayCount;



    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate registerDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate firstActiveDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate lastActiveDate;

    private Integer memberDays;
}
