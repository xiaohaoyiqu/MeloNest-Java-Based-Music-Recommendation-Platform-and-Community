package com.haoran.music.vo.external;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

   
                            
  
                      
   
@Data
public class ExternalContentPublicVO {
    private Long id;
    private String contentType;
    private String externalId;
    private String title;
    private String description;
    private String thumbnail;
    private List<String> images;
    private String externalUrl;
    private String category;
    private String platform;
    private String releaseDate;
    private Double rating;
    private LocalDateTime createTime;
}
