


package com.haoran.music.service.impl;

import com.haoran.music.common.constant.PublicStatsSql;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;




@Slf4j
@Service
@RequiredArgsConstructor
public class ContentStatisticReconcileService {

    private final JdbcTemplate jdbcTemplate;

    public Map<String, Integer> syncCommentCounts() {
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("commentLikeCount", update("commentLikeCount",
                "UPDATE comment c " +
                        "LEFT JOIN (" +
                        "  SELECT cl.comment_id, COUNT(*) AS like_count " +
                        "  FROM comment_like cl " +
                        joinPublicUser("cl.user_id") +
                        "  WHERE cl.is_like = 1 AND cl.deleted = 0 " +
                        "  GROUP BY cl.comment_id" +
                        ") stats ON stats.comment_id = c.id " +
                        "SET c.like_count = COALESCE(stats.like_count, 0), c.update_time = NOW() " +
                        "WHERE c.deleted = 0 " +
                        "AND COALESCE(c.like_count, 0) <> COALESCE(stats.like_count, 0)"));
        result.put("commentReplyCount", update("commentReplyCount",
                "UPDATE comment c " +
                        "LEFT JOIN (" +
                        "  SELECT c.parent_id, COUNT(*) AS reply_count " +
                        "  FROM comment c " +
                        joinPublicUser("c.user_id") +
                        "  WHERE c.status = 1 AND c.deleted = 0 AND c.parent_id IS NOT NULL AND c.parent_id <> 0 " +
                        "  GROUP BY c.parent_id" +
                        ") stats ON stats.parent_id = c.id " +
                        "SET c.reply_count = COALESCE(stats.reply_count, 0), c.update_time = NOW() " +
                        "WHERE c.deleted = 0 " +
                        "AND COALESCE(c.reply_count, 0) <> COALESCE(stats.reply_count, 0)"));
        result.put("songCommentCount", updateTargetCommentCount("song", 1));
        result.put("albumCommentCount", updateTargetCommentCount("album", 2));
        result.put("mvCommentCount", updateTargetCommentCount("mv", 4));
        result.put("artistCommentCount", updateTargetCommentCount("artist", 5));
        return result;
    }

    public Map<String, Integer> syncCoreCounters() {
        Map<String, Integer> result = new LinkedHashMap<>(syncCommentCounts());
        result.put("songLikeFavoriteCount", update("songLikeFavoriteCount",
                "UPDATE song s " +
                        "LEFT JOIN (" +
                        "  SELECT sl.song_id, " +
                        "         SUM(CASE WHEN sl.is_favorite = 1 THEN 1 ELSE 0 END) AS favorite_count, " +
                        "         SUM(CASE WHEN sl.is_like = 1 THEN 1 ELSE 0 END) AS like_count " +
                        "  FROM song_like sl " +
                        joinPublicUser("sl.user_id") +
                        "  WHERE sl.deleted = 0 " +
                        "  GROUP BY sl.song_id" +
                        ") stats ON stats.song_id = s.id " +
                        "SET s.favorite_count = COALESCE(stats.favorite_count, 0), " +
                        "    s.like_count = COALESCE(stats.like_count, 0), " +
                        "    s.update_time = NOW() " +
                        "WHERE s.deleted = 0 " +
                        "AND (COALESCE(s.favorite_count, 0) <> COALESCE(stats.favorite_count, 0) " +
                        "     OR COALESCE(s.like_count, 0) <> COALESCE(stats.like_count, 0))"));
        result.put("songAvgRating", update("songAvgRating",
                "UPDATE song s " +
                        "LEFT JOIN (" +
                        "  SELECT sr.song_id, COUNT(*) AS rating_count, ROUND(AVG(sr.rating), 1) AS avg_rating " +
                        "  FROM song_rating sr " +
                        joinPublicUser("sr.user_id") +
                        "  WHERE sr.deleted = 0 " +
                        "  GROUP BY sr.song_id" +
                        ") stats ON stats.song_id = s.id " +
                        "SET s.avg_rating = COALESCE(stats.avg_rating, 0), " +
                        "    s.rating_count = COALESCE(stats.rating_count, 0), " +
                        "    s.update_time = NOW() " +
                        "WHERE s.deleted = 0 " +
                        "AND (COALESCE(s.avg_rating, 0) <> COALESCE(stats.avg_rating, 0) " +
                        "     OR COALESCE(s.rating_count, 0) <> COALESCE(stats.rating_count, 0))"));
        result.put("playlistSongCount", update("playlistSongCount",
                "UPDATE playlist p " +
                        "LEFT JOIN (" +
                        "  SELECT playlist_id, COUNT(*) AS song_count " +
                        "  FROM playlist_song ps " +
                        "  INNER JOIN song s ON s.id = ps.song_id AND s.status = 1 AND s.deleted = 0 " +
                        "  WHERE ps.deleted = 0 " +
                        "  GROUP BY playlist_id" +
                        ") stats ON stats.playlist_id = p.id " +
                        "SET p.song_count = COALESCE(stats.song_count, 0), p.update_time = NOW() " +
                        "WHERE p.deleted = 0 " +
                        "AND COALESCE(p.song_count, 0) <> COALESCE(stats.song_count, 0)"));
        result.put("albumSongCount", update("albumSongCount",
                "UPDATE album a " +
                        "LEFT JOIN (" +
                        "  SELECT album_id, COUNT(*) AS song_count " +
                        "  FROM song " +
                        "  WHERE status = 1 AND deleted = 0 AND album_id IS NOT NULL " +
                        "  GROUP BY album_id" +
                        ") stats ON stats.album_id = a.id " +
                        "SET a.song_count = COALESCE(stats.song_count, 0), a.update_time = NOW() " +
                        "WHERE a.deleted = 0 " +
                        "AND COALESCE(a.song_count, 0) <> COALESCE(stats.song_count, 0)"));
        result.put("artistSongAlbumCount", update("artistSongAlbumCount",
                "UPDATE artist ar " +
                        "LEFT JOIN (" +
                        "  SELECT sa.artist_id, COUNT(DISTINCT s.id) AS song_count, COUNT(DISTINCT s.album_id) AS album_count " +
                        "  FROM song_artist sa " +
                        "  INNER JOIN song s ON s.id = sa.song_id AND s.status = 1 AND s.deleted = 0 " +
                        "  GROUP BY sa.artist_id" +
                        ") stats ON stats.artist_id = ar.id " +
                        "SET ar.song_count = COALESCE(stats.song_count, 0), " +
                        "    ar.album_count = COALESCE(stats.album_count, 0), " +
                        "    ar.update_time = NOW() " +
                        "WHERE ar.deleted = 0 " +
                        "AND (COALESCE(ar.song_count, 0) <> COALESCE(stats.song_count, 0) " +
                        "     OR COALESCE(ar.album_count, 0) <> COALESCE(stats.album_count, 0))"));
        return result;
    }

    private int updateTargetCommentCount(String tableName, int targetType) {
        return update(tableName + "CommentCount",
                "UPDATE " + tableName + " t " +
                        "LEFT JOIN (" +
                        "  SELECT c.target_id, COUNT(*) AS comment_count " +
                        "  FROM comment c " +
                        joinPublicUser("c.user_id") +
                        "  WHERE c.target_type = " + targetType +
                        "    AND c.status = 1 AND c.deleted = 0 " +
                        "    AND (c.parent_id IS NULL OR c.parent_id = 0) " +
                        "  GROUP BY c.target_id" +
                        ") stats ON stats.target_id = t.id " +
                        "SET t.comment_count = COALESCE(stats.comment_count, 0), t.update_time = NOW() " +
                        "WHERE t.deleted = 0 " +
                        "AND COALESCE(t.comment_count, 0) <> COALESCE(stats.comment_count, 0)");
    }

    private int update(String name, String sql) {
        long startTime = System.currentTimeMillis();
        int updated = jdbcTemplate.update(sql);
        log.debug("Statistic reconciliation SQL completed: name={}, updated={}, duration={}ms",
                name, updated, System.currentTimeMillis() - startTime);
        return updated;
    }

    private String joinPublicUser(String userIdExpression) {
        return "  " + PublicStatsSql.USER_JOIN + userIdExpression + PublicStatsSql.USER_FILTER + " ";
    }
}
