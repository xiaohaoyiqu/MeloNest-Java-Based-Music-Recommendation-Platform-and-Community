package com.haoran.music.vo.song;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;





@Data
public class LocalMusicVO implements Serializable {

    private static final long serialVersionUID = 1L;




    private Long id;




    private String name;




    private String artistName;




    private String albumName;




    private Integer duration;




    private Long fileSize;




    private String fileFormat;




    private Integer quality;




    private String playUrl;




    private String coverUrl;




    private String lyricUrl;




    private Integer playCount;




    private LocalDateTime createTime;




    private Boolean added;




    private Boolean isFavorite;




    private Long songId;




    private Integer resourceType;




    private String filePath;




    private Boolean fileExists;






    private String versionType;




    private String versionName;




    private String lyric;
}
