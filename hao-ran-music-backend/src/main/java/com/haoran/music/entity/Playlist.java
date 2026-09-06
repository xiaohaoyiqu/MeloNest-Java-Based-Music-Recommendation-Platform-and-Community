package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;





@Data
@EqualsAndHashCode(callSuper = true)
@TableName("playlist")
public class Playlist extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String name;
    private String cover;
    private String description;
    private Long userId;
    private Integer type;
    private Integer isPublic;




    private Long songCount;




    private Long playCount;




    private Long favoriteCount;




    private Long visitCount;

    private Integer status;




    private Integer isFeatured;

    private Integer deleted;
    private String tags;
    private String language;
    private Integer isPaid;
    private BigDecimal price;
    private Integer subscribePeriod;






    private Integer allowDownload;




    private Integer allowComment;




    private Integer allowShare;






    private String intro;




    private String category;




    private Long creatorId;
}
