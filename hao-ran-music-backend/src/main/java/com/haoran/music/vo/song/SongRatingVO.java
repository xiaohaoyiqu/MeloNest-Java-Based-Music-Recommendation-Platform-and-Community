package com.haoran.music.vo.song;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;





@Data
public class SongRatingVO {




    private Long songId;




    private Double avgRating;




    private Integer ratingCount;





    @JsonInclude(JsonInclude.Include.ALWAYS)
    private Integer userRating;




    private RatingDistribution distribution;

    @Data
    public static class RatingDistribution {
        private Integer fiveStar;
        private Integer fourStar;
        private Integer threeStar;
        private Integer twoStar;
        private Integer oneStar;
    }
}
