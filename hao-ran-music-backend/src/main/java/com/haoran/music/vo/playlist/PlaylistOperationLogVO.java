package com.haoran.music.vo.playlist;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;





@Data
public class PlaylistOperationLogVO {




    private Long id;




    private Map<String, Object> user;




    private String operationType;




    private String operationTypeName;




    private Long songId;




    private String songName;




    private String description;




    private LocalDateTime createdTime;




    private String timeDescription;
}
