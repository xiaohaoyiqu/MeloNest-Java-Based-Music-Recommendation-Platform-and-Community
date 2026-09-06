package com.haoran.music.vo.playlist;

import com.haoran.music.common.util.MediaPlaybackUrlUtil;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

   
                      
                      
   
@Data

public class PlaylistVO implements Serializable {

    private static final long serialVersionUID = 1L;

       
                                                       
       
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String name;

    private String description;

    private String cover;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String creatorName;

    private String creatorAvatar;

    private Integer type;

    private Integer songCount;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long playCount;

    private Integer favoriteCount;

    private Integer visitCount;

    private Integer isPublic;

    private Boolean isFavorite;

    private List<SongSimpleVO> songs;

       
                                 
       
    private String orderVersion;

    private List<String> tags;

                                                                                     
    private String primaryLanguage;

                                                                             
    private List<String> contentLanguages;

                                                    
    private Map<String, Integer> languageCounts;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer currentPage;

    private Integer pageSize;

    private Integer totalPages;

    private Integer filteredSongCount;

                                                                          
    private Boolean contentAccessible;

                                                       

       
                      
       
    private Integer allowDownload;

       
                      
       
    private Integer allowComment;

       
                      
       
    private Integer allowShare;

                                                     

       
             
       
    private String intro;

       
           
       
    private String category;

       
            
       
    private Long creatorId;

       
             
       
    @Data
    public static class SongSimpleVO implements Serializable {

        private static final long serialVersionUID = 1L;

        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;

        private String name;

        private String artistNames;

        private String cover;

        @JsonSerialize(using = ToStringSerializer.class)
        private Long albumId;

        private String albumName;

        private Integer duration;

        private String urlStandard;

        private String urlHigh;

        private String urlLossless;

        private String versionType;

        private String versionName;

        private String mainType;

        private Integer sortOrder;

        @JsonSerialize(using = ToStringSerializer.class)
        private Long playCount;

        private String language;

        private Double avgRating;

        private Boolean isFavorite;

        public String getUrlStandard() {
            return MediaPlaybackUrlUtil.songUrl(id, "standard", urlStandard);
        }

        public String getUrlHigh() {
            return MediaPlaybackUrlUtil.songUrl(id, "high", urlHigh);
        }

        public String getUrlLossless() {
            return MediaPlaybackUrlUtil.songUrl(id, "lossless", urlLossless);
        }
    }
}
