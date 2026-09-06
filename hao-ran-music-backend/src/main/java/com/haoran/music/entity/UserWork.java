package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;





@Data
@TableName("user_work")
public class UserWork implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;




    private Long userId;




    private String nickname;




    private String avatar;




    private Integer workType;




    private String workName;




    private String coverUrl;




    private String fileUrl;




    private String fileUrls;




    private String zipFileUrl;




    private String lyricFileUrl;




    private Integer uploadType;




    private Integer qualityType;




    private String versionType;




    private String productionType;




    private Long fileSize;




    private Integer duration;




    private String description;




    private String tags;




    private Integer language;




    private String lyric;




    private Boolean showRealName;




    private Integer source;




    private Integer status;




    private Long reviewerId;




    private String reviewerName;




    private LocalDateTime reviewTime;




    private String reviewReason;




    private LocalDateTime publishTime;




    private Long songId;




    @TableField(exist = false)
    private Long albumId;




    @TableField(exist = false)
    private String albumName;




    private Long playCount;




    private Integer likeCount;




    private Integer collectCount;




    private Integer shareCount;




    private Integer downloadCount;




    private Boolean virusScanned;




    private String virusScanResult;




    private Integer rewardPoints;






    private Integer allowDownload;




    private Integer allowComment;




    private Integer allowShare;




    private LocalDateTime createTime;




    private LocalDateTime updateTime;




    @TableLogic
    private Integer deleted;
}
