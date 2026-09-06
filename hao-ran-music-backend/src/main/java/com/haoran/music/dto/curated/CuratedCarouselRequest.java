   
                      
   
package com.haoran.music.dto.curated;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.time.LocalDateTime;

   
              
   
@Data
public class CuratedCarouselRequest {

    @NotBlank(message = "展示场景不能为空")
    private String scene;

    @NotBlank(message = "内容类型不能为空")
    private String contentType;

    @NotBlank(message = "标题不能为空")
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
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
