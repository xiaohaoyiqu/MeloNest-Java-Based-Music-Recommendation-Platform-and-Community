package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;





@Data
@TableName("music_local_music")
public class LocalMusic implements Serializable {

    private static final long serialVersionUID = 1L;




    @TableId(type = IdType.ASSIGN_ID)
    private Long id;




    private Long userId;




    private String name;




    private String artistName;




    private String albumName;




    private Integer duration;




    private Long fileSize;




    private String fileFormat;




    private Integer quality;




    private String filePath;




    @TableField(exist = false)
    private String coverPath;




    @TableField(exist = false)
    private String lyricPath;




    private String lyricText;




    private Integer playCount;




    private Long songId;




    private Integer resourceType;




    private String versionType;




    private String versionName;




    @TableLogic
    private Integer deleted;




    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;




    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
