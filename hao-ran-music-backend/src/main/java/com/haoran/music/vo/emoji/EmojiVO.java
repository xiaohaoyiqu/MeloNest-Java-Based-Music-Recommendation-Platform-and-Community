   
                      
                     
   

package com.haoran.music.vo.emoji;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

   
       
   
@Data
public class EmojiVO implements Serializable {

    private static final long serialVersionUID = 1L;

       
           
       
    private Long id;

       
                 
       
    private String emojiId;

       
           
       
    private String emojiCode;

       
           
       
    private String emojiName;

       
            
       
    private Long packageId;

       
         
       
    private String category;

       
               
       
    private String gifUrl;

       
              
       
    private String staticUrl;

       
             
       
    private Boolean isSystem;

       
           
       
    private Long usageCount;

       
           
       
    private Boolean isEnabled;

       
           
       
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

       
               
       
    public static EmojiVO fromEntity(com.haoran.music.entity.Emoji emoji) {
        if (emoji == null) {
            return null;
        }
        EmojiVO vo = new EmojiVO();
        vo.setId(emoji.getId());
        vo.setEmojiId(emoji.getCode());                 
        vo.setEmojiCode(emoji.getCode());
        vo.setEmojiName(emoji.getName());
        vo.setPackageId(emoji.getPackageId());
        vo.setCategory(emoji.getCategory());
        vo.setStaticUrl(emoji.getImageUrl());                       
        vo.setGifUrl(emoji.getImageUrl());                      
        vo.setUsageCount(emoji.getUsageCount() != null ? emoji.getUsageCount() : 0L);
        vo.setIsEnabled(emoji.getEnabled() != null && emoji.getEnabled() == 1);
        vo.setCreateTime(emoji.getCreateTime());
        return vo;
    }
}
