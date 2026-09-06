package com.haoran.music.vo.recommend;

import lombok.Data;

import java.util.List;

   
                      
                      
   
@Data
public class RecommendReasonVO {

       
             
       
    private Long itemId;

       
                                          
       
    private String itemType;

       
             
       
    private String reasonType;

       
               
       
    private String reasonTypeDescription;

       
             
       
    private String reasonText;

       
             
       
    private String reasonTemplate;

       
             
       
    private List<RelatedEntity> relatedEntities;

       
                  
       
    private Integer confidence;

       
           
       
    private Double weight;

       
            
       
    private Boolean explainable;

       
           
       
    private String recommendSource;

       
               
       
    public enum ReasonType {
           
                 
           
        HISTORY_BASED("history_based", "基于历史播放", "因为您最近常听《%s》"),

           
                 
           
        FAVORITE_BASED("favorite_based", "基于收藏偏好", "根据您收藏的《%s》推荐"),

           
                 
           
        SOCIAL_BASED("social_based", "基于社交关系", "您的好友%s也喜欢这首歌"),

           
                 
           
        TAG_BASED("tag_based", "基于标签偏好", "根据您的%s风格偏好"),

           
                 
           
        PORTRAIT_BASED("portrait_based", "基于用户画像", "根据您的听歌习惯推荐"),

           
               
           
        HOT_BASED("hot_based", "热门推荐", "本周热门推荐"),

           
               
           
        NEW_SONG("new_song", "新歌推荐", "%s发布的新歌"),

           
               
           
        COLLABORATIVE("collaborative", "相似用户喜欢", "和您口味相似的用户也喜欢"),

           
               
           
        REGION_BASED("region_based", "地区热门", "您所在地区的热门歌曲"),

           
               
           
        TIME_BASED("time_based", "时段推荐", "适合%s听的歌");

        private final String code;
        private final String description;
        private final String template;

        ReasonType(String code, String description, String template) {
            this.code = code;
            this.description = description;
            this.template = template;
        }

        public String getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }

        public String getTemplate() {
            return template;
        }
    }

       
           
       
    @Data
    public static class RelatedEntity {
           
               
           
        private Long entityId;

           
               
           
        private String entityType;

           
               
           
        private String entityName;

           
                 
           
        private String relationDescription;
    }
}
