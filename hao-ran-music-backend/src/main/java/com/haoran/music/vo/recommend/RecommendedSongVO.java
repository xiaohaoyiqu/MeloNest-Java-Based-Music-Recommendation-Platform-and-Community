package com.haoran.music.vo.recommend;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;





@Data
@EqualsAndHashCode(callSuper = true)
public class RecommendedSongVO extends RecommendVO.SongSimpleVO implements Serializable {

    private static final long serialVersionUID = 1L;




    private String source;




    private String sourceName;




    private Integer confidence;




    private RelatedSongInfo relatedSong;




    private List<String> tags;





    private Double danceability;




    private Double energy;




    private Double valence;




    private Double tempo;




    private Double acousticness;




    private Double instrumentalness;




    private Double speechiness;




    private Double liveness;




    private Double similarityScore;




    @Data
    public static class RelatedSongInfo implements Serializable {
        private static final long serialVersionUID = 1L;


        private Long id;


        private String name;


        private String cover;


        private String artistName;
    }
}
