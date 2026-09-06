   
                      
                     
   

package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

   
         
   
@Data
@TableName("music_emoji_package")
public class EmojiPackage implements Serializable {

    private static final long serialVersionUID = 1L;

       
            
       
    @TableId(type = IdType.ASSIGN_ID)
    @JsonProperty("packageId")
    private Long id;

       
            
       
    @JsonProperty("packageName")
    private String name;

       
            
       
    @JsonProperty("description")
    private String description;

       
            
       
    @TableField(exist = false)
    private String cover;

       
                       
       
    @JsonProperty("iconUrl")
    private String coverUrl;

       
                                     
       
    private Long coverEmojiId;

       
                                   
       
    @JsonProperty("type")
    private String type;

       
                                                       
       
    @JsonProperty("category")
    private String category;

       
                    
       
    private Integer sortOrder;

       
               
       
    private Integer price;

       
                                         
       
    private String purchaseMode;

       
                                    
       
    private BigDecimal cashPrice;

       
           
       
    @JsonProperty("isFree")
    private Integer isFree;

       
                    
       
    @JsonProperty("isEnabled")
    private Integer status;

       
            
       
    private Long creatorId;

       
          
       
    @JsonProperty("downloadCount")
    private Integer downloadCount;

       
                                   
       
    private Integer itemLimit;

       
                        
       
    @TableField(exist = false)
    private Integer itemCount;

       
                         
       
    @TableField(exist = false)
    private Integer remainingCount;

                       
    private String reviewStatus;

    private LocalDateTime submitTime;

    private Long reviewerId;

    private LocalDateTime reviewTime;

    private String reviewReason;

       
           
       
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("createTime")
    private LocalDateTime createTime;

       
           
       
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

       
                        
       
    @TableField("is_deleted")
    @TableLogic
    private Integer deleted;

       
              
       
    public Boolean getIsSystem() {
        return "system".equals(type);
    }

       
                           
       
    @TableField(exist = false)
    @JsonProperty("emojis")
    private List<Emoji> emojis = new ArrayList<>();

       
               
       
    public void setEmojis(List<Emoji> emojis) {
        this.emojis = emojis;
    }
}
