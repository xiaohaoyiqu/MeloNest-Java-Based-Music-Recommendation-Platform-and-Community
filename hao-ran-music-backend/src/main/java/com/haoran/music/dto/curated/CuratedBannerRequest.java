   
                      
   
package com.haoran.music.dto.curated;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.time.LocalDate;

   
                  
   
@Data
public class CuratedBannerRequest {

    @NotBlank(message = "轮播标题不能为空")
    private String title;

    private String description;
    private String cover;
    private String eventType;
    private LocalDate eventDate;
    private String source;
    private String sourceUrl;
    private String fallbackSourceUrl;
    private String sourceType;
    private String relatedArtists;
    private String relatedSongs;
    private Integer sortOrder;
}
