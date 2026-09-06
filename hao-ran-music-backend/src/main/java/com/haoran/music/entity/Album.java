package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

   
                      
                     
   
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("album")
public class Album extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String name;
    private String nameEn;
    private String originalName;
    private Long artistId;
    private String artistIds;
    private String artistNames;
    private String cover;
    private String description;
    private LocalDate releaseDate;
    private String country;
    private String province;
    private String region;
    private String company;
    private String type;
    private String genres;
    private String language;
    private Integer priority;
    private Integer isPaid;
    private Long paidResourceId;
    private BigDecimal price;

       
          
       
    private Long songCount;

       
           
       
    private Long playCount;

       
           
       
    private Long favoriteCount;

       
           
       
    private Long commentCount;

    private Integer status;

                                                       

       
                      
       
    private Integer allowDownload;

       
                      
       
    private Integer allowComment;

       
                      
       
    private Integer allowShare;

    @TableLogic
    private Integer deleted;
}
