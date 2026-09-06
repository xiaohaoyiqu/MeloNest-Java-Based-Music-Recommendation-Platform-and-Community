   
                      
   
package com.haoran.music.task;

import com.haoran.music.common.config.HotScoreConfig;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.constant.PublicStatsSql;
import com.haoran.music.service.MusicIntelligenceCacheService;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

   
                              
   
@Slf4j
@Component
public class HotScoreCalculationTask {

    private static final int COMMENT_TARGET_SONG = 1;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private HotScoreConfig hotScoreConfig;

    @Resource
    private MusicIntelligenceCacheService musicIntelligenceCacheService;

       
                                            
       
    @Scheduled(cron = "${schedule.task.hot-score.calculation-cron}")
    public void calculateHotScore() {
        log.info("========== Hot score calculation started ==========");
        long startTime = System.currentTimeMillis();

        try {
            int updated = calculateSongHotScore();
            if (updated > 0) {
                bumpRankingCacheVersion(updated);
                bumpRecommendCacheVersion(updated);
            }
            log.info("Song hot score calculation completed, updated: {}", updated);
            log.info("========== Hot score calculation completed, time: {}ms ==========",
                    System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("event=hot_score_calculation_failed errorType={}", e.getClass().getSimpleName());
        }
    }

    private void bumpRankingCacheVersion(int updated) {
        try {
            musicIntelligenceCacheService.bumpRankingCacheVersion("hot score recalculated:" + updated, null);
        } catch (Exception e) {
            log.warn("event=hot_score_ranking_cache_version_update_failed errorType={}",
                    e.getClass().getSimpleName());
        }
    }

    private void bumpRecommendCacheVersion(int updated) {
        try {
            musicIntelligenceCacheService.bumpRecommendCacheVersion("hot score recalculated:" + updated, null);
        } catch (Exception e) {
            log.warn("event=hot_score_recommendation_cache_version_update_failed errorType={}",
                    e.getClass().getSimpleName());
        }
    }

    private int calculateSongHotScore() {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(hotScoreConfig.getCalculationPeriodDays());
        LocalDateTime startTime = startDate.atStartOfDay();
        LocalDateTime endTime = endDate.atTime(23, 59, 59);
        LocalDate newSongCutoff = endDate.minusDays(hotScoreConfig.getNewSongDays());

        String scoreExpression = "(COALESCE(s.favorite_count, 0) * ? " +
                "+ COALESCE(stats.comment_count, 0) * ? " +
                "+ COALESCE(stats.comment_like_count, 0) * ? " +
                "+ COALESCE(stats.comment_reply_count, 0) * ?)";

        String sql = "UPDATE song s " +
                "LEFT JOIN (" +
                "  SELECT c.target_id, " +
                "         COUNT(*) AS comment_count, " +
                "         COALESCE(SUM(COALESCE(c.like_count, 0)), 0) AS comment_like_count, " +
                "         SUM(CASE WHEN COALESCE(c.parent_id, 0) <> 0 THEN 1 ELSE 0 END) AS comment_reply_count " +
                "  FROM comment c " +
                joinPublicUser("c.user_id") +
                "  WHERE c.target_type = ? AND c.deleted = ? " +
                "    AND c.create_time >= ? AND c.create_time <= ? " +
                "  GROUP BY c.target_id" +
                ") stats ON stats.target_id = s.id " +
                "SET s.hot_score = " + scoreExpression + ", " +
                "    s.is_new = CASE WHEN s.release_date IS NOT NULL AND s.release_date >= ? THEN 1 ELSE 0 END, " +
                "    s.is_hot = CASE WHEN " + scoreExpression + " >= ? THEN 1 ELSE 0 END, " +
                "    s.update_time = NOW() " +
                "WHERE s.status = ? AND s.deleted = ?";

        return jdbcTemplate.update(sql,
                COMMENT_TARGET_SONG,
                CommonConstants.NOT_DELETED,
                startTime,
                endTime,
                hotScoreConfig.getFavoriteWeight(),
                hotScoreConfig.getCommentWeight(),
                hotScoreConfig.getCommentLikeWeight(),
                hotScoreConfig.getCommentReplyWeight(),
                newSongCutoff,
                hotScoreConfig.getFavoriteWeight(),
                hotScoreConfig.getCommentWeight(),
                hotScoreConfig.getCommentLikeWeight(),
                hotScoreConfig.getCommentReplyWeight(),
                hotScoreConfig.getThreshold(),
                CommonConstants.STATUS_NORMAL,
                CommonConstants.NOT_DELETED);
    }

    private String joinPublicUser(String userIdExpression) {
        return "  " + PublicStatsSql.USER_JOIN + userIdExpression + PublicStatsSql.USER_FILTER + " ";
    }
}
