package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;





@Data
@TableName("lyric_translation")
public class LyricTranslation implements Serializable {

    private static final long serialVersionUID = 1L;




    @TableId(type = IdType.ASSIGN_ID)
    private Long id;




    private Long songId;




    private Long sourceLyricId;




    private String targetLanguage;




    private Integer lyricType;




    private Integer status;




    private String resultContent;




    private String errorMessage;




    private String errorCode;




    private String modelName;




    private Integer attemptCount;




    private LocalDateTime nextRetryTime;




    private String processingToken;




    private LocalDateTime requestTime;




    private LocalDateTime completeTime;




    private Long creatorId;




    @TableLogic
    private Integer deleted;




    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;




    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
