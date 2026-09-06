package com.haoran.music.vo.recommend;

import com.haoran.music.common.util.MediaPlaybackUrlUtil;
import lombok.Data;

import java.io.Serializable;
import java.util.List;





@Data

public class RecommendVO implements Serializable {

    private static final long serialVersionUID = 1L;


    private String type;


    private String reason;


    private String source;


    private String sourceName;



    private String modelVersion;



    private Boolean modelBacked;



    private String fallbackReason;


    private List<SongSimpleVO> songs;





    private String scenario;




    private Double mood;




    private String bpmRange;




    private Long baseSongId;




    @Data

    public static class SongSimpleVO implements Serializable {

        private static final long serialVersionUID = 1L;


        private Long id;


        private String name;


        private String artistNames;


        private Long artistId;


        private String albumName;


        private Long albumId;


        private Integer duration;


        private String mainType;


        private String cover;


        private Double score;


        private String reason;


        private String urlStandard;


        private String urlHigh;


        private String urlLossless;


        private Integer favoriteCount;


        private Long playCount;


        private Integer isNew;


        private Integer isHot;





        private Boolean isFavorite;




        private Long sizeStandard;




        private Long sizeHigh;




        private Long sizeLossless;




        private String language;




        private String versionType;




        private String versionName;

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
