package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;











@Data
@TableName("music_square_work")
public class MusicSquareWork implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;




    private Long userId;




    private String userName;






    private Integer workType;




    private String title;




    private String name;




    private String description;




    private String coverUrl;




    private String tags;




    private String artistNames;




    private String copyright;






    private String audioUrl;




    private Integer audioQuality;




    private Long audioSize;




    private Integer audioDuration;




    private Integer audioBitrate;




    private Integer audioSampleRate;




    private String audioFormat;






    private String videoUrl;




    private String videoQuality;




    private Long videoSize;




    private Integer videoDuration;




    private String videoFormat;






    private String lyricContent;




    private String lyricFileUrl;




    private Integer hasTranslation;






    private Integer status;




    private Long reviewerId;




    private LocalDateTime reviewTime;




    private String reviewReason;






    private Long viewCount;




    private Integer likeCount;




    private Integer commentCount;




    private Integer shareCount;






    private Long relatedSongId;




    private Long relatedMvId;






    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;




    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;




    private LocalDateTime publishTime;




    @TableLogic
    private Integer deleted;
}
