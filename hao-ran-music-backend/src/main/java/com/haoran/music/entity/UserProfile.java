package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;





@Data
@TableName("music_user_profile")
public class UserProfile extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;




    @TableId(type = IdType.ASSIGN_ID)
    private Long id;




    private Long userId;






    private String preferredGenres;




    private String preferredArtists;




    private String preferredLanguages;




    private String preferredMoods;






    private Integer avgDailyDuration;




    private Integer peakActiveHour;






    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate registerDate;




    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate firstActiveDate;




    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate lastActiveDate;




    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate churnDate;




    private Integer totalActiveDays;




    private Integer totalPlayDuration;




    private Integer totalPlayCount;






    private String userSegment;




    private String lifecycleStage;




    private Integer ltvScore;




    private Integer churnProbability;




    @TableLogic
    private Integer deleted;
}
