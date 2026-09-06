


package com.haoran.music.vo.curated;

import lombok.Data;

import java.time.LocalDateTime;




@Data
public class CuratedCarouselItemVO {

    private String id;
    private String scene;
    private String contentType;
    private String title;
    private String description;
    private String badge;
    private String imageUrl;
    private String fallbackImageUrl;
    private String sourceType;
    private String sourceName;
    private String link;
    private String fallbackLink;
    private String targetType;
    private String targetId;
    private Integer priority;
    private Integer sortOrder;
    private Long viewCount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createTime;
}
