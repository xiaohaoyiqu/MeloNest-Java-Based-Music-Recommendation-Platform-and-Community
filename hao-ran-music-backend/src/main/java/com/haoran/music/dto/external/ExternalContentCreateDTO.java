package com.haoran.music.dto.external;

import lombok.Data;

import java.util.List;

   
               
  
                      
   
@Data
public class ExternalContentCreateDTO {
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
}
