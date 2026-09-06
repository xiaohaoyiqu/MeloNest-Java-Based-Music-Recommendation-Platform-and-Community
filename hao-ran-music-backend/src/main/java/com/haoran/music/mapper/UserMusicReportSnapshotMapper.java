


package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.UserMusicReportSnapshot;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface UserMusicReportSnapshotMapper extends BaseMapper<UserMusicReportSnapshot> {

    @Select("SELECT * FROM user_music_report_snapshot " +
            "WHERE user_id = #{userId} AND report_type = #{reportType} AND period_key = #{periodKey} " +
            "AND deleted = 0 ORDER BY generated_at DESC LIMIT 1")
    UserMusicReportSnapshot selectLatestSnapshot(@Param("userId") Long userId,
                                                  @Param("reportType") String reportType,
                                                  @Param("periodKey") String periodKey);

    @Insert("INSERT INTO user_music_report_snapshot (" +
            "user_id, report_type, period_key, content_json, generated_at, data_until, calculation_version, " +
            "expire_time, deleted, create_time, update_time) VALUES (" +
            "#{userId}, #{reportType}, #{periodKey}, #{contentJson}, #{generatedAt}, #{dataUntil}, #{calculationVersion}, " +
            "#{expireTime}, 0, NOW(), NOW()) " +
            "ON DUPLICATE KEY UPDATE content_json = VALUES(content_json), generated_at = VALUES(generated_at), " +
            "data_until = VALUES(data_until), calculation_version = VALUES(calculation_version), " +
            "expire_time = VALUES(expire_time), deleted = 0, update_time = NOW()")
    int upsertSnapshot(UserMusicReportSnapshot snapshot);

    @Update("UPDATE user_music_report_snapshot SET deleted = 1, update_time = NOW() WHERE user_id = #{userId} AND report_type = #{reportType} AND period_key = #{periodKey} AND deleted = 0")
    int markSnapshotDeleted(@Param("userId") Long userId,
                            @Param("reportType") String reportType,
                            @Param("periodKey") String periodKey);

    @Select("SELECT report_type AS reportType, calculation_version AS calculationVersion, COUNT(*) AS rowCount, " +
            "SUM(CASE WHEN expire_time IS NOT NULL AND expire_time <= NOW() THEN 1 ELSE 0 END) AS expiredCount, " +
            "MIN(generated_at) AS firstGeneratedAt, MAX(generated_at) AS lastGeneratedAt, " +
            "MAX(data_until) AS latestDataUntil " +
            "FROM user_music_report_snapshot WHERE deleted = 0 " +
            "GROUP BY report_type, calculation_version ORDER BY report_type, calculation_version")
    List<Map<String, Object>> selectSnapshotStatus();
}
