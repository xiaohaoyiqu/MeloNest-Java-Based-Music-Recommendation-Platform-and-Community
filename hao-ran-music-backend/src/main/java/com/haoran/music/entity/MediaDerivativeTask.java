package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;






@Data
@TableName("media_derivative_task")
public class MediaDerivativeTask implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String MEDIA_TYPE_SONG = "song";
    public static final String MEDIA_TYPE_MV = "mv";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";




    @TableId(type = IdType.AUTO)
    private Long id;




    private String mediaType;




    private Long mediaId;




    private String sourceUrl;




    private Long sourceSize;




    private Integer sourceQuality;




    private String status;




    private Integer retryCount;




    private Integer maxRetryCount;




    private String lastError;




    private LocalDateTime nextRetryTime;




    private LocalDateTime startedAt;




    private LocalDateTime finishedAt;




    private LocalDateTime createdAt;




    private LocalDateTime updatedAt;
}
