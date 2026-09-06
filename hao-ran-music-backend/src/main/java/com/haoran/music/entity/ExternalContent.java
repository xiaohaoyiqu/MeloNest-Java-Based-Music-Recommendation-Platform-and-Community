package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;





@Data
@TableName("external_content")
public class ExternalContent {

    @TableId(type = IdType.AUTO)
    private Long id;




    private String contentType;




    private String externalId;




    private String title;




    private String description;




    private String thumbnail;




    private String images;




    private String externalUrl;




    private String category;




    private String platform;




    private String releaseDate;




    private Double rating;




    private Integer status;




    private Integer priority;




    private Long createdBy;




    private LocalDateTime createTime;




    private LocalDateTime updateTime;
}
