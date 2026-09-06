package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;





@Data
@TableName("song_resource_request")
public class SongResourceRequest implements Serializable {

    private static final long serialVersionUID = 1L;




    @TableId(type = IdType.AUTO)
    private Long id;




    private Long userId;




    private String songName;




    private String artistName;




    private String albumName;




    private String versionInfo;




    private String fileUrl;






    private Integer detectedQuality;




    private Long fileSize;




    private Integer duration;




    private Integer bitrate;




    private Integer sampleRate;




    private String format;






    private String sourceDescription;




    private String remark;




    private String status;




    private Long handlerId;




    private LocalDateTime handleTime;




    private String handleResult;




    private Long matchedSongId;




    private Long autoSongId;




    private Integer notified;




    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;




    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;




    @TableLogic
    private Integer deleted;
}
