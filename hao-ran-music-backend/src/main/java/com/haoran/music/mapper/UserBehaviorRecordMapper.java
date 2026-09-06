   
                      
                              
   

package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.common.constant.PublicStatsSql;
import com.haoran.music.entity.UserBehaviorRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

   
                 
   
@Mapper
public interface UserBehaviorRecordMapper extends BaseMapper<UserBehaviorRecord> {

    @Select("<script>" +
            "SELECT br.behavior_type AS behaviorType, br.target_type AS targetType, " +
            "br.device_type AS deviceType, COUNT(*) AS behaviorCount, " +
            "COALESCE(SUM(IFNULL(br.duration, 0)), 0) AS totalDuration " +
            "FROM user_behavior_record br " +
            "JOIN `user` u ON u.id = br.user_id " +
            "WHERE br.user_id = #{userId} AND br.deleted = 0" + PublicStatsSql.USER_FILTER_XML +
            "<if test='startTime != null'> AND br.behavior_time &gt;= #{startTime}</if>" +
            "<if test='endTime != null'> AND br.behavior_time &lt; #{endTime}</if>" +
            " GROUP BY br.behavior_type, br.target_type, br.device_type" +
            "</script>")
    List<Map<String, Object>> selectPublicUserBehaviorStats(@Param("userId") Long userId,
                                                              @Param("startTime") LocalDateTime startTime,
                                                              @Param("endTime") LocalDateTime endTime);

    @Select("SELECT COUNT(*) AS totalCount, " +
            "SUM(CASE WHEN br.behavior_type = 'play' THEN 1 ELSE 0 END) AS playCount, " +
            "SUM(CASE WHEN br.behavior_type = 'like' THEN 1 ELSE 0 END) AS likeCount, " +
            "COALESCE(SUM(IFNULL(br.duration, 0)), 0) AS totalDuration, " +
            "COUNT(DISTINCT br.user_id) AS uniqueUsers " +
            "FROM user_behavior_record br " +
            "JOIN `user` u ON u.id = br.user_id " +
            "WHERE br.target_type = #{targetType} AND br.target_id = #{targetId} AND br.deleted = 0" +
            PublicStatsSql.USER_FILTER)
    Map<String, Object> selectPublicTargetBehaviorTotals(@Param("targetType") String targetType,
                                                          @Param("targetId") Long targetId);

    @Select("SELECT br.behavior_type AS behaviorType, COUNT(*) AS behaviorCount " +
            "FROM user_behavior_record br " +
            "JOIN `user` u ON u.id = br.user_id " +
            "WHERE br.target_type = #{targetType} AND br.target_id = #{targetId} AND br.deleted = 0" +
            PublicStatsSql.USER_FILTER + " " +
            "GROUP BY br.behavior_type")
    List<Map<String, Object>> selectPublicTargetBehaviorTypeStats(@Param("targetType") String targetType,
                                                                   @Param("targetId") Long targetId);

    @Select("<script>" +
            "SELECT br.target_id AS targetId, COUNT(*) AS count, " +
            "COUNT(DISTINCT br.user_id) AS uniqueUsers " +
            "FROM user_behavior_record br " +
            "JOIN `user` u ON u.id = br.user_id " +
            "WHERE br.target_type = #{targetType} AND br.deleted = 0" + PublicStatsSql.USER_FILTER_XML +
            "<if test='behaviorType != null and behaviorType != \"\"'> " +
            "AND br.behavior_type = #{behaviorType}" +
            "</if>" +
            " GROUP BY br.target_id " +
            "ORDER BY count DESC, uniqueUsers DESC " +
            "LIMIT #{limit}" +
            "</script>")
    List<Map<String, Object>> selectPublicHotResources(@Param("targetType") String targetType,
                                                        @Param("behaviorType") String behaviorType,
                                                        @Param("limit") Integer limit);
}
