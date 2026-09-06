package com.haoran.music.vo.tag;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;





@Data
public class MusicTagVO implements Serializable {

    private static final long serialVersionUID = 1L;


    private Long id;


    private String name;


    private String category;


    private String categoryName;


    private String icon;


    private String color;


    private String description;


    private Integer useCount;


    private Boolean isHot;


    private Integer sortOrder;


    private LocalDateTime createdTime;
}
