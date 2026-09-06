   
                      
   
package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.common.constant.UserAccountPolicyConstants;
import com.haoran.music.common.enums.UserType;
import com.haoran.music.entity.UserMusicDailySummary;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface UserMusicDailySummaryMapper extends BaseMapper<UserMusicDailySummary> {

    @Select("SELECT * FROM user_music_daily_summary " +
            "WHERE user_id = #{userId} AND stat_date BETWEEN #{startDate} AND #{endDate} " +
            "AND deleted = 0 ORDER BY stat_date ASC")
    List<UserMusicDailySummary> selectByUserAndDateRange(@Param("userId") Long userId,
                                                          @Param("startDate") LocalDate startDate,
                                                          @Param("endDate") LocalDate endDate);

    @Select("SELECT MIN(stat_date) AS firstStatDate, MAX(stat_date) AS lastStatDate, " +
            "COUNT(*) AS rowCount, COUNT(DISTINCT stat_date) AS coveredDays, MAX(update_time) AS lastUpdatedAt " +
            "FROM user_music_daily_summary WHERE deleted = 0")
    Map<String, Object> selectSummaryStatus();

    @Insert("INSERT INTO user_music_daily_summary (" +
            "user_id, stat_date, play_count, valid_play_count, play_seconds, unique_song_count, " +
            "avg_valence, avg_energy, active_minutes, top_song_id, top_song_name, top_song_artist_names, " +
            "public_stats_eligible, feature_coverage, calculation_version, data_until, deleted, create_time, update_time) " +
            "SELECT lh.user_id, #{statDate}, COUNT(*) AS play_count, " +
            "SUM(CASE WHEN IFNULL(lh.is_completed, 0) = 1 OR IFNULL(lh.progress, 0) >= 60 THEN 1 ELSE 0 END) AS valid_play_count, " +
            "IFNULL(SUM(IFNULL(NULLIF(lh.duration, 0), IFNULL(s.duration, 0))), 0) AS play_seconds, " +
            "COUNT(DISTINCT lh.song_id) AS unique_song_count, " +
            "AVG(s.valence) AS avg_valence, AVG(s.energy) AS avg_energy, " +
            "COUNT(DISTINCT CONCAT(DATE_FORMAT(lh.create_time, '%Y-%m-%d %H:'), LPAD(FLOOR(MINUTE(lh.create_time) / 10) * 10, 2, '0'))) * 10 AS active_minutes, " +
            "top_song.top_song_id, top_song.top_song_name, top_song.top_song_artist_names, " +
            "CASE WHEN u.id IS NOT NULL AND u.deleted = 0 AND u.status = 1 " +
            "AND (u.is_banned IS NULL OR u.is_banned <> 1) " +
            "AND (u.user_type IS NULL OR u.user_type NOT IN (" + UserType.RESTRICTED_CODE_SQL + ")) " +
            "AND (u.risk_score IS NULL OR u.risk_score < " + UserAccountPolicyConstants.HIGH_RISK_SCORE_THRESHOLD + ") " +
            "AND (u.credit_score IS NULL OR u.credit_score >= " + UserAccountPolicyConstants.PUBLIC_FLOW_CREDIT_MIN_SCORE + ") " +
            "THEN 1 ELSE 0 END AS public_stats_eligible, " +
            "CAST(SUM(CASE WHEN s.valence IS NOT NULL OR s.energy IS NOT NULL THEN 1 ELSE 0 END) / COUNT(*) AS DECIMAL(6,4)) AS feature_coverage, " +
            "#{calculationVersion}, #{endTime}, 0, NOW(), NOW() " +
            "FROM listen_history lh " +
            "LEFT JOIN song s ON s.id = lh.song_id AND IFNULL(s.deleted, 0) = 0 " +
            "LEFT JOIN `user` u ON u.id = lh.user_id " +
            "LEFT JOIN (" +
            "  SELECT ranked.user_id, ranked.song_id AS top_song_id, ts.name AS top_song_name, ts.artist_names AS top_song_artist_names " +
            "  FROM (" +
            "    SELECT song_counts.user_id, SUBSTRING_INDEX(GROUP_CONCAT(song_counts.song_id ORDER BY song_counts.play_count DESC, song_counts.song_id ASC), ',', 1) AS song_id " +
            "    FROM (" +
            "      SELECT lh2.user_id, lh2.song_id, COUNT(*) AS play_count " +
            "      FROM listen_history lh2 " +
            "      WHERE IFNULL(lh2.deleted, 0) = 0 AND lh2.create_time >= #{startTime} AND lh2.create_time < #{endTime} AND lh2.song_id IS NOT NULL " +
            "      GROUP BY lh2.user_id, lh2.song_id" +
            "    ) song_counts GROUP BY song_counts.user_id" +
            "  ) ranked LEFT JOIN song ts ON ts.id = ranked.song_id" +
            ") top_song ON top_song.user_id = lh.user_id " +
            "WHERE IFNULL(lh.deleted, 0) = 0 AND lh.create_time >= #{startTime} AND lh.create_time < #{endTime} " +
            "GROUP BY lh.user_id, top_song.top_song_id, top_song.top_song_name, top_song.top_song_artist_names, " +
            "u.id, u.deleted, u.status, u.is_banned, u.user_type, u.risk_score, u.credit_score " +
            "ON DUPLICATE KEY UPDATE play_count = VALUES(play_count), valid_play_count = VALUES(valid_play_count), " +
            "play_seconds = VALUES(play_seconds), unique_song_count = VALUES(unique_song_count), " +
            "avg_valence = VALUES(avg_valence), avg_energy = VALUES(avg_energy), active_minutes = VALUES(active_minutes), " +
            "top_song_id = VALUES(top_song_id), top_song_name = VALUES(top_song_name), top_song_artist_names = VALUES(top_song_artist_names), " +
            "public_stats_eligible = VALUES(public_stats_eligible), feature_coverage = VALUES(feature_coverage), " +
            "calculation_version = VALUES(calculation_version), data_until = VALUES(data_until), deleted = 0, update_time = NOW()")
    int refreshDailySummary(@Param("statDate") LocalDate statDate,
                            @Param("startTime") LocalDateTime startTime,
                            @Param("endTime") LocalDateTime endTime,
                            @Param("calculationVersion") String calculationVersion);
}
