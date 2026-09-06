package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.common.constant.PublicStatsSql;
import com.haoran.music.entity.UserStatistics;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

   
                      
                              
   
@Mapper
public interface UserStatisticsMapper extends BaseMapper<UserStatistics> {

       
                        
       
    @Select("SELECT * FROM user_statistics WHERE user_id = #{userId} " +
            "AND stat_date BETWEEN #{startDate} AND #{endDate} " +
            "AND deleted = 0 ORDER BY stat_date DESC")
    List<UserStatistics> selectByDateRange(@Param("userId") Long userId,
                                            @Param("startDate") LocalDate startDate,
                                            @Param("endDate") LocalDate endDate);

       
                   
       
    @Select("SELECT * FROM user_statistics WHERE stat_date = #{statDate} " +
            "AND deleted = 0 ORDER BY play_duration DESC")
    List<UserStatistics> selectByStatDate(@Param("statDate") LocalDate statDate);

       
                 
       
    @Select("SELECT user_id, username, " +
            "SUM(play_count) AS total_play_count, " +
            "SUM(play_duration) AS total_play_duration, " +
            "SUM(like_count) AS total_like_count, " +
            "SUM(favorite_count) AS total_favorite_count, " +
            "COUNT(DISTINCT stat_date) AS active_days " +
            "FROM user_statistics " +
            "WHERE user_id = #{userId} AND deleted = 0 " +
            "AND stat_date BETWEEN #{startDate} AND #{endDate} " +
            "GROUP BY user_id, username")
    Map<String, Object> selectUserTotalStats(@Param("userId") Long userId,
                                              @Param("startDate") LocalDate startDate,
                                              @Param("endDate") LocalDate endDate);

       
                  
       
    @Select("SELECT * FROM user_statistics WHERE stat_date = #{statDate} " +
            "AND is_abnormal = 1 AND deleted = 0")
    List<UserStatistics> selectAbnormalUsers(@Param("statDate") LocalDate statDate);

       
                    
       
    @Select("SELECT COUNT(DISTINCT stat_date) FROM user_statistics " +
            "WHERE user_id = #{userId} AND stat_date >= #{startDate} " +
            "AND (play_count > 0 OR login_count > 0) AND deleted = 0")
    Integer selectActiveDaysCount(@Param("userId") Long userId,
                                    @Param("startDate") LocalDate startDate);

       
                      
       
    @Select("SELECT user_id, username, MAX(play_count) AS max_daily_plays, " +
            "MAX(play_duration) AS max_daily_duration, COUNT(DISTINCT stat_date) AS days " +
            "FROM user_statistics " +
            "WHERE stat_date BETWEEN #{startDate} AND #{endDate} AND deleted = 0 " +
            "GROUP BY user_id, username " +
            "HAVING MAX(play_count) >= #{maxPlayCount} OR MAX(play_duration) >= #{maxDuration}")
    List<Map<String, Object>> selectBotUsers(@Param("startDate") LocalDate startDate,
                                              @Param("endDate") LocalDate endDate,
                                              @Param("maxPlayCount") Integer maxPlayCount,
                                              @Param("maxDuration") Integer maxDuration);

       
                                   
      
  
    @Select("SELECT lh.user_id AS user_id, COALESCE(NULLIF(u.username, ''), u.nickname) AS username, " +
            "COUNT(*) AS play_count, IFNULL(SUM(IFNULL(lh.duration, 0)), 0) AS play_duration, " +
            "COUNT(DISTINCT lh.song_id) AS unique_song_count, " +
            "SUM(CASE WHEN IFNULL(lh.is_completed, 0) = 1 THEN 1 ELSE 0 END) AS complete_play_count " +
            "FROM listen_history lh" + PublicStatsSql.USER_JOIN + "lh.user_id" + PublicStatsSql.USER_FILTER + " " +
            "WHERE IFNULL(lh.deleted, 0) = 0 AND lh.create_time >= #{startTime} AND lh.create_time < #{endTime} " +
            "GROUP BY lh.user_id, u.username, u.nickname")
    List<Map<String, Object>> selectDailyListenStats(@Param("startTime") LocalDateTime startTime,
                                                      @Param("endTime") LocalDateTime endTime);

    @Select("SELECT COUNT(*) AS recordCount, COUNT(DISTINCT user_id) AS userCount, "
            + "MAX(stat_date) AS latestStatDate, "
            + "SUM(CASE WHEN IFNULL(is_abnormal, 0) = 1 THEN 1 ELSE 0 END) AS abnormalRecordCount, "
            + "IFNULL(SUM(IFNULL(play_count, 0)), 0) AS playCount, "
            + "IFNULL(SUM(IFNULL(play_duration, 0)), 0) AS playDuration "
            + "FROM user_statistics WHERE deleted = 0 AND stat_date >= #{startDate}")
    Map<String, Object> selectRecentStatus(@Param("startDate") LocalDate startDate);
}
