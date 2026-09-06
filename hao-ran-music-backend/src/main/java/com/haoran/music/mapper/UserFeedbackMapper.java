   
                      
                            
   

package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.UserFeedback;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

   
               
   
@Mapper
public interface UserFeedbackMapper extends BaseMapper<UserFeedback> {

    @Select("SELECT * FROM user_feedback WHERE id = #{feedbackId} AND deleted = 0 FOR UPDATE")
    UserFeedback selectByIdForUpdate(@Param("feedbackId") Long feedbackId);

    @Update("UPDATE user_feedback SET status = #{targetStatus}, handler_id = #{handlerId}, handle_time = #{handleTime}, handle_result = #{handleResult}, update_time = NOW() WHERE id = #{feedbackId} AND status = #{expectedStatus} AND deleted = 0")
    int transitionStatus(@Param("feedbackId") Long feedbackId,
                         @Param("expectedStatus") String expectedStatus,
                         @Param("targetStatus") String targetStatus,
                         @Param("handlerId") Long handlerId,
                         @Param("handleTime") LocalDateTime handleTime,
                         @Param("handleResult") String handleResult);

    @Select("SELECT COUNT(*) AS totalCount, "
            + "COALESCE(SUM(CASE WHEN status = 'pending' THEN 1 ELSE 0 END), 0) AS pendingCount, "
            + "COALESCE(SUM(CASE WHEN status = 'resolved' THEN 1 ELSE 0 END), 0) AS resolvedCount "
            + "FROM user_feedback WHERE deleted = 0 AND user_id = #{userId}")
    Map<String, Object> selectUserFeedbackStats(@Param("userId") Long userId);

    @Select("<script>"
            + "SELECT COUNT(*) AS totalCount, "
            + "COALESCE(SUM(CASE WHEN handler_id IS NOT NULL THEN 1 ELSE 0 END), 0) AS processedCount "
            + "FROM user_feedback WHERE deleted = 0 "
            + "<if test='handlerId != null'>AND handler_id = #{handlerId} </if>"
            + "<if test='startTime != null'>AND create_time &gt;= #{startTime} </if>"
            + "<if test='endTime != null'>AND create_time &lt;= #{endTime} </if>"
            + "</script>")
    Map<String, Object> selectAdminFeedbackStats(@Param("handlerId") Long handlerId,
                                                 @Param("startTime") LocalDateTime startTime,
                                                 @Param("endTime") LocalDateTime endTime);

    @Select("SELECT feedback_type AS feedbackType, "
            + "COALESCE(NULLIF(title, ''), '未分类') AS title, "
            + "COUNT(*) AS count "
            + "FROM user_feedback "
            + "WHERE deleted = 0 AND status = 'pending' "
            + "AND create_time >= DATE_SUB(NOW(), INTERVAL 30 DAY) "
            + "GROUP BY feedback_type, COALESCE(NULLIF(title, ''), '未分类') "
            + "ORDER BY count DESC, MAX(create_time) DESC "
            + "LIMIT #{limit}")
    List<Map<String, Object>> selectHotFeedbackIssues(@Param("limit") int limit);
}
