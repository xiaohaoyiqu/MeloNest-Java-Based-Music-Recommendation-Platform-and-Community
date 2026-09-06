   
                      
   
package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

   
                      
   
@Data
@TableName("curated_carousel_item")
public class CuratedCarouselItem {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

       
                                        
       
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
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;
    private Integer reviewStatus;
    private Long reviewerId;
    private LocalDateTime reviewTime;
    private String reviewRemark;
    private Long operatorId;
    private Long viewCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

       
                            
       
    private String legacySourceKey;
}
