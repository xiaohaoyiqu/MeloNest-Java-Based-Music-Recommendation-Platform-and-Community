package com.haoran.music.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;






@Data
@Component
@ConfigurationProperties(prefix = "hot-score")
public class HotScoreConfig {

    private int threshold = 100;
    private int newSongDays = 7;
    private int calculationPeriodDays = 3;
    private int favoriteWeight = 5;
    private int commentWeight = 3;
    private int commentLikeWeight = 2;
    private int commentReplyWeight = 1;
    private int playCountDivisor = 100;
    private int mvLikeWeight = 3;
    private int mvFavoriteWeight = 5;
    private int mvCommentWeight = 3;
    private int mvShareWeight = 2;
}
