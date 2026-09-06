


package com.haoran.music.service.helper;

import com.alibaba.fastjson2.JSON;
import com.haoran.music.common.config.UserGrowthConfig;
import com.haoran.music.entity.UserStatistics;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;




@Component
public class UserRiskScorePolicy {

    private final UserGrowthConfig userGrowthConfig;

    public UserRiskScorePolicy(UserGrowthConfig userGrowthConfig) {
        this.userGrowthConfig = userGrowthConfig;
    }

    public boolean isBotThresholdReached(Integer playCount, Integer playDuration) {
        return safeInt(playCount) >= getBotMaxDailyPlayCount()
                || safeInt(playDuration) >= getBotMaxDailyDurationSeconds();
    }

    public String buildAbnormalReason(Integer playCount, Integer playDuration) {
        UserStatistics stats = new UserStatistics();
        stats.setPlayCount(safeInt(playCount));
        stats.setPlayDuration(safeInt(playDuration));
        return buildAbnormalReason(stats);
    }

    public String buildAbnormalReason(UserStatistics stats) {
        Map<String, Object> reason = new HashMap<>();
        if (stats == null) {
            return JSON.toJSONString(reason);
        }
        if (safeInt(stats.getPlayCount()) >= getBotMaxDailyPlayCount()) {
            reason.put("highPlayCount", "play_count >= " + getBotMaxDailyPlayCount());
        }
        if (safeInt(stats.getPlayDuration()) >= getBotMaxDailyDurationSeconds()) {
            reason.put("highDuration", "play_duration >= " + getBotMaxDailyDurationSeconds());
        }
        if (safeInt(stats.getLoginCount()) >= getRiskLoginCountWarnThreshold()) {
            reason.put("highLoginCount", "login_count >= " + getRiskLoginCountWarnThreshold());
        }
        if (safeInt(stats.getSearchCount()) >= getRiskSearchCountWarnThreshold()) {
            reason.put("highSearchCount", "search_count >= " + getRiskSearchCountWarnThreshold());
        }
        int interactionCount = getInteractionCount(stats);
        if (interactionCount >= getRiskInteractionCountWarnThreshold()) {
            reason.put("highInteractionCount", "interaction_count >= " + getRiskInteractionCountWarnThreshold());
        }
        if (hasLowDiversityPlayback(stats)) {
            reason.put("lowDiversityPlayback", "play_count high but unique_song_count low");
        }
        return JSON.toJSONString(reason);
    }

    public Integer calculateRiskScore(UserStatistics stats) {
        if (stats == null) {
            return 0;
        }

        int score = 0;
        if (safeInt(stats.getPlayCount()) >= getBotMaxDailyPlayCount()) {
            score += 50;
        } else if (safeInt(stats.getPlayCount()) >= getRiskPlayCountWarnThreshold()) {
            score += 30;
        }

        if (safeInt(stats.getPlayDuration()) >= getBotMaxDailyDurationSeconds()) {
            score += 50;
        } else if (safeInt(stats.getPlayDuration()) >= getRiskDurationWarnSeconds()) {
            score += 30;
        }

        if (safeInt(stats.getLoginCount()) >= getRiskLoginCountWarnThreshold()) {
            score += 15;
        }
        if (safeInt(stats.getSearchCount()) >= getRiskSearchCountWarnThreshold()) {
            score += 20;
        }
        if (getInteractionCount(stats) >= getRiskInteractionCountWarnThreshold()) {
            score += 20;
        }
        if (hasLowDiversityPlayback(stats)) {
            score += 20;
        }

        return Math.min(score, 100);
    }

    public boolean shouldMarkAbnormal(UserStatistics stats) {
        if (stats == null) {
            return false;
        }
        return isBotThresholdReached(stats.getPlayCount(), stats.getPlayDuration())
                || safeInt(stats.getRiskScore()) >= getRiskAbnormalScoreThreshold();
    }

    public int getBotMaxDailyPlayCount() {
        return positiveOrDefault(userGrowthConfig.getStatistics().getBotMaxDailyPlayCount(), 800);
    }

    public int getBotMaxDailyDurationSeconds() {
        return positiveOrDefault(userGrowthConfig.getStatistics().getBotMaxDailyDurationSeconds(), 14 * 3600);
    }

    private boolean hasLowDiversityPlayback(UserStatistics stats) {
        return safeInt(stats.getPlayCount()) >= getRiskLowDiversityPlayCountThreshold()
                && safeInt(stats.getUniqueSongCount()) > 0
                && safeInt(stats.getUniqueSongCount()) <= getRiskLowDiversityUniqueSongThreshold();
    }

    private int getInteractionCount(UserStatistics stats) {
        return safeInt(stats.getLikeCount())
                + safeInt(stats.getFavoriteCount())
                + safeInt(stats.getUnfavoriteCount())
                + safeInt(stats.getCommentCount())
                + safeInt(stats.getShareCount())
                + safeInt(stats.getDownloadCount())
                + safeInt(stats.getCreatePlaylistCount())
                + safeInt(stats.getFollowArtistCount());
    }

    private int getRiskPlayCountWarnThreshold() {
        return positiveOrDefault(userGrowthConfig.getStatistics().getRiskPlayCountWarnThreshold(), 600);
    }

    private int getRiskDurationWarnSeconds() {
        return positiveOrDefault(userGrowthConfig.getStatistics().getRiskDurationWarnSeconds(), 10 * 3600);
    }

    private int getRiskLoginCountWarnThreshold() {
        return positiveOrDefault(userGrowthConfig.getStatistics().getRiskLoginCountWarnThreshold(), 20);
    }

    private int getRiskSearchCountWarnThreshold() {
        return positiveOrDefault(userGrowthConfig.getStatistics().getRiskSearchCountWarnThreshold(), 200);
    }

    private int getRiskInteractionCountWarnThreshold() {
        return positiveOrDefault(userGrowthConfig.getStatistics().getRiskInteractionCountWarnThreshold(), 300);
    }

    private int getRiskLowDiversityPlayCountThreshold() {
        return positiveOrDefault(userGrowthConfig.getStatistics().getRiskLowDiversityPlayCountThreshold(), 100);
    }

    private int getRiskLowDiversityUniqueSongThreshold() {
        return positiveOrDefault(userGrowthConfig.getStatistics().getRiskLowDiversityUniqueSongThreshold(), 3);
    }

    private int getRiskAbnormalScoreThreshold() {
        return positiveOrDefault(userGrowthConfig.getStatistics().getRiskAbnormalScoreThreshold(), 70);
    }

    private int positiveOrDefault(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}