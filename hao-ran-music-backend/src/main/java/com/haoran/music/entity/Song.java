package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;





@Data
@EqualsAndHashCode(callSuper = true)
@TableName("song")
public class Song extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;




    @TableId(type = IdType.ASSIGN_ID)
    private Long id;




    private String name;




    private String originalName;




    private String nameEn;




    private String cover;




    private Long artistId;




    private String artistIds;




    private String artistNames;




    private Long uploaderId;




    private Long albumId;




    private Long mvId;




    private String albumName;




    private Integer albumTrackNo;




    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate releaseDate;




    private Integer duration;




    private String mainGenre;




    private String mainType;




    private String subTypes;




    private String subGenres;




    private String tags;




    private String language;




    private String lyricLanguage;




    private String description;




    private String lyrics;




    private String lyricsFile;




    private Integer hasLyric;




    private String versionType;




    private String versionName;




    private String urlStandard;




    private String urlHigh;




    private String urlLossless;




    private Long sizeStandard;




    private Long sizeHigh;




    private Long sizeLossless;




    private Long playCount;




    private Long favoriteCount;




    private Long commentCount;




    private Long likeCount;




    private Long replyCount;




    private Long shareCount;




    private Long downloadCount;




    private BigDecimal avgRating;




    private Integer priority;




    private Integer isVipOnly;




    private Integer isPaid;




    private Integer price;




    private Integer previewDuration;




    private Integer isSingle;




    private Integer isNew;




    private Integer isHot;




    @TableField("hot_score")
    private Integer hotScore;




    private Integer status;




    private String copyrightInfo;




    private String publishStatus;




    private String reviewStatus;




    private String reviewComment;




    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reviewTime;




    private Long reviewerId;




    @TableLogic
    private Integer deleted;





    private BigDecimal danceability;




    private BigDecimal energy;




    private BigDecimal valence;




    private BigDecimal tempo;




    private BigDecimal acousticness;




    private BigDecimal instrumentalness;




    private BigDecimal speechiness;




    private BigDecimal liveness;




    private Integer audioKey;




    private BigDecimal loudness;




    private Integer mode;




    private Integer timeSignature;




    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime audioFeaturesUpdated;
}
