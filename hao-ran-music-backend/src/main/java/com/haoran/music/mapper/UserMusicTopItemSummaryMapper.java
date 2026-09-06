


package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.common.constant.UserAccountPolicyConstants;
import com.haoran.music.common.enums.UserType;
import com.haoran.music.entity.UserMusicTopItemSummary;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface UserMusicTopItemSummaryMapper extends BaseMapper<UserMusicTopItemSummary> {

    @Update("UPDATE user_music_top_item_summary SET deleted = 1, update_time = NOW() WHERE period_type = #{periodType} AND period_key = #{periodKey} AND deleted = 0")
    int markPeriodDeleted(@Param("periodType") String periodType,
                          @Param("periodKey") LocalDate periodKey);

    @Insert("INSERT INTO user_music_top_item_summary (" +
            "user_id, period_type, period_key, item_type, item_id, item_name, artist_names, item_cover, play_count, " +
            "public_stats_eligible, calculation_version, data_until, deleted, create_time, update_time) " +
            "SELECT lh.user_id, #{periodType}, #{periodKey}, 'song', lh.song_id, MAX(s.name), MAX(s.artist_names), " +
            "MAX(s.cover), COUNT(*), " +
            "CASE WHEN u.id IS NOT NULL AND u.deleted = 0 AND u.status = 1 " +
            "AND (u.is_banned IS NULL OR u.is_banned <> 1) " +
            "AND (u.user_type IS NULL OR u.user_type NOT IN (" + UserType.RESTRICTED_CODE_SQL + ")) " +
            "AND (u.risk_score IS NULL OR u.risk_score < " + UserAccountPolicyConstants.HIGH_RISK_SCORE_THRESHOLD + ") " +
            "AND (u.credit_score IS NULL OR u.credit_score >= " + UserAccountPolicyConstants.PUBLIC_FLOW_CREDIT_MIN_SCORE + ") " +
            "THEN 1 ELSE 0 END, #{calculationVersion}, #{endTime}, 0, NOW(), NOW() " +
            "FROM listen_history lh LEFT JOIN song s ON s.id = lh.song_id " +
            "LEFT JOIN `user` u ON u.id = lh.user_id " +
            "WHERE IFNULL(lh.deleted, 0) = 0 AND lh.song_id IS NOT NULL " +
            "AND lh.create_time >= #{startTime} AND lh.create_time < #{endTime} " +
            "GROUP BY lh.user_id, lh.song_id, u.id, u.deleted, u.status, u.is_banned, u.user_type, u.risk_score, u.credit_score " +
            "ON DUPLICATE KEY UPDATE item_name = VALUES(item_name), artist_names = VALUES(artist_names), " +
            "item_cover = VALUES(item_cover), play_count = VALUES(play_count), " +
            "public_stats_eligible = VALUES(public_stats_eligible), calculation_version = VALUES(calculation_version), " +
            "data_until = VALUES(data_until), deleted = 0, update_time = NOW()")
    int refreshSongSummary(@Param("periodType") String periodType,
                           @Param("periodKey") LocalDate periodKey,
                           @Param("startTime") LocalDateTime startTime,
                           @Param("endTime") LocalDateTime endTime,
                           @Param("calculationVersion") String calculationVersion);

    @Insert("INSERT INTO user_music_top_item_summary (" +
            "user_id, period_type, period_key, item_type, item_id, item_name, artist_names, item_cover, play_count, " +
            "public_stats_eligible, calculation_version, data_until, deleted, create_time, update_time) " +
            "SELECT lh.user_id, #{periodType}, #{periodKey}, 'artist', sa.artist_id, MAX(a.name), NULL, MAX(a.avatar), " +
            "COUNT(DISTINCT lh.id), " +
            "CASE WHEN u.id IS NOT NULL AND u.deleted = 0 AND u.status = 1 " +
            "AND (u.is_banned IS NULL OR u.is_banned <> 1) " +
            "AND (u.user_type IS NULL OR u.user_type NOT IN (" + UserType.RESTRICTED_CODE_SQL + ")) " +
            "AND (u.risk_score IS NULL OR u.risk_score < " + UserAccountPolicyConstants.HIGH_RISK_SCORE_THRESHOLD + ") " +
            "AND (u.credit_score IS NULL OR u.credit_score >= " + UserAccountPolicyConstants.PUBLIC_FLOW_CREDIT_MIN_SCORE + ") " +
            "THEN 1 ELSE 0 END, #{calculationVersion}, #{endTime}, 0, NOW(), NOW() " +
            "FROM listen_history lh INNER JOIN song_artist sa ON sa.song_id = lh.song_id " +
            "LEFT JOIN artist a ON a.id = sa.artist_id " +
            "LEFT JOIN `user` u ON u.id = lh.user_id " +
            "WHERE IFNULL(lh.deleted, 0) = 0 AND sa.artist_id IS NOT NULL " +
            "AND lh.create_time >= #{startTime} AND lh.create_time < #{endTime} " +
            "GROUP BY lh.user_id, sa.artist_id, u.id, u.deleted, u.status, u.is_banned, u.user_type, u.risk_score, u.credit_score " +
            "ON DUPLICATE KEY UPDATE item_name = VALUES(item_name), item_cover = VALUES(item_cover), " +
            "play_count = VALUES(play_count), public_stats_eligible = VALUES(public_stats_eligible), " +
            "calculation_version = VALUES(calculation_version), data_until = VALUES(data_until), deleted = 0, update_time = NOW()")
    int refreshArtistSummary(@Param("periodType") String periodType,
                             @Param("periodKey") LocalDate periodKey,
                             @Param("startTime") LocalDateTime startTime,
                             @Param("endTime") LocalDateTime endTime,
                             @Param("calculationVersion") String calculationVersion);

    @Select("SELECT COUNT(DISTINCT period_key) FROM user_music_top_item_summary " +
            "WHERE user_id = #{userId} AND period_type = #{periodType} AND period_key BETWEEN #{startKey} AND #{endKey} " +
            "AND deleted = 0")
    int countCoveredPeriods(@Param("userId") Long userId,
                            @Param("periodType") String periodType,
                            @Param("startKey") LocalDate startKey,
                            @Param("endKey") LocalDate endKey);

    @Select("SELECT item_id AS itemId, MAX(item_name) AS itemName, MAX(artist_names) AS artistNames, " +
            "MAX(item_cover) AS itemCover, SUM(play_count) AS playCount " +
            "FROM user_music_top_item_summary " +
            "WHERE user_id = #{userId} AND period_type = #{periodType} " +
            "AND period_key BETWEEN #{startKey} AND #{endKey} AND item_type = #{itemType} " +
            "AND deleted = 0 GROUP BY item_id ORDER BY playCount DESC, itemId ASC LIMIT #{limit}")
    List<Map<String, Object>> selectAggregatedTopItems(@Param("userId") Long userId,
                                                        @Param("periodType") String periodType,
                                                        @Param("startKey") LocalDate startKey,
                                                        @Param("endKey") LocalDate endKey,
                                                        @Param("itemType") String itemType,
                                                        @Param("limit") int limit);

    @Select("SELECT period_type AS periodType, MIN(period_key) AS firstPeriodKey, MAX(period_key) AS lastPeriodKey, " +
            "COUNT(*) AS rowCount, COUNT(DISTINCT user_id) AS userCount, MAX(update_time) AS lastUpdatedAt " +
            "FROM user_music_top_item_summary WHERE deleted = 0 GROUP BY period_type ORDER BY period_type")
    List<Map<String, Object>> selectSummaryStatus();
}
