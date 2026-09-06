   
                      
                      
   

package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

   
         
   
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("marketplace_item")
public class MarketplaceItem {

       
           
       
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

       
           
       
    private Long sellerId;

       
           
       
    private String title;

       
                                  
       
    private String category;

       
                                       
       
    @TableField("`condition`")
    private String conditionInfo;

       
         
       
    private BigDecimal price;

       
         
       
    private BigDecimal originalPrice;

       
           
       
    private String description;

       
                 
       
    private String images;

       
             
       
    private String resourceType;

       
             
       
    private Long resourceId;

       
             
       
    private String resourceName;

       
             
       
    private String resourceCover;

       
                                          
       
    private String status;

       
          
       
    private String location;

       
                                 
       
    private String deliveryMethod;

       
          
       
    private Integer viewCount;

       
          
       
    private Integer favoriteCount;

       
           
       
    private Boolean isDeleted;

       
           
       
    private LocalDateTime createTime;

       
           
       
    private LocalDateTime soldTime;
}
