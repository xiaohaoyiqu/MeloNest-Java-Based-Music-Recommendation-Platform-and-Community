package com.haoran.music.vo.user;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;





@Data
public class UserActivityVO implements Serializable {

    private static final long serialVersionUID = 1L;




    private Long id;




    private String activityType;




    private String activityTypeName;




    private String targetType;




    private String targetTypeName;




    private Long targetId;




    private Map<String, Object> target;




    private String content;




    private LocalDateTime createdTime;




    private String timeDescription;




    private Long relatedUserId;




    private String relatedUserName;




    private String relatedUserAvatar;
}
