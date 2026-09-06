package com.haoran.music.vo.album;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.haoran.music.common.util.MediaPlaybackUrlUtil;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

   
                      
                      
   
@Data

public class AlbumVO implements Serializable {

    private static final long serialVersionUID = 1L;


    private Long id;


    private String name;


    private LocalDate releaseDate;


    private String country;


    private String province;


    private String region;


    private String company;


    private String artistIds;

       
                              
       
    private Long artistId;


    private String artistNames;

       
             
       
    private String artistAvatar;


    private String genres;


    private String language;


    private String type;


    private String cover;


    private String description;


    private Integer songCount;


    private Long playCount;


    private Integer favoriteCount;


    private Integer commentCount;


    private Boolean isFavorite;


    private List<SongSimpleVO> songs;


    private LocalDateTime createTime;

       
             
       
    @Data

    public static class SongSimpleVO implements Serializable {

        private static final long serialVersionUID = 1L;


        @JsonFormat(shape = JsonFormat.Shape.NUMBER)
        private Long id;


        private String name;


        private Integer duration;


        private String mainType;


        private String cover;


        private String urlStandard;


        private String urlHigh;


        private String urlLossless;


        private Long sizeStandard;


        private Long sizeHigh;


        private Long sizeLossless;


        private String albumName;


        @JsonFormat(shape = JsonFormat.Shape.NUMBER)
        private Long albumId;

                      
        private Integer albumTrackNo;

        private Integer isSingle;

           
                       
           
        private String artistIds;

           
                     
           
        private String artistNames;

           
                                                                                                        
           
        private String versionType;

           
                                
           
        private String versionName;

           
             
           
        private String language;

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

                                                       

       
                      
       
    private Integer allowDownload;

       
                      
       
    private Integer allowComment;

       
                      
       
    private Integer allowShare;

       
                        
       
    @Data

    public static class AlbumSimpleVO implements Serializable {

        private static final long serialVersionUID = 1L;


        @JsonFormat(shape = JsonFormat.Shape.NUMBER)
        private Long id;


        private String name;


        private String cover;


        private LocalDate releaseDate;
    }
}
