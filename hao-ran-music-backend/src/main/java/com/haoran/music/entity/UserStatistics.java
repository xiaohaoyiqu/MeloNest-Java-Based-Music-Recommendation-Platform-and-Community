package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;





@Data
@TableName("user_statistics")
public class UserStatistics extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;




    @TableId(type = IdType.ASSIGN_ID)
    private Long id;




    private Long userId;




    private String username;




    private LocalDate statDate;






    private Integer playCount;




    private Integer playDuration;




    private Integer uniqueSongCount;




    private Integer completePlayCount;






    private Integer likeCount;




    private Integer favoriteCount;




    private Integer unfavoriteCount;




    private Integer commentCount;




    private Integer shareCount;




    private Integer downloadCount;






    private Integer activeDuration;




    private Integer loginCount;




    private Integer searchCount;




    private Integer createPlaylistCount;




    private Integer followArtistCount;






    private Integer isAbnormal;









    private String abnormalReason;




    private Integer riskScore;






    private String ipAddress;





    private Integer deleted;
}
