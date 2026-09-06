package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.ModerationRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

   
                      
                          
   
@Mapper
public interface ModerationRecordMapper extends BaseMapper<ModerationRecord> {

    @Select("SELECT * FROM moderation_record WHERE target_type = #{targetType} " +
            "AND target_id = #{targetId} AND status IN ('pending', 'in_progress') " +
            "ORDER BY id DESC LIMIT 1")
    ModerationRecord selectLatestActiveTarget(@Param("targetType") String targetType,
                                               @Param("targetId") Long targetId);

    @Update("UPDATE moderation_record SET assigned_moderator_id = #{moderatorId}, assigned_time = #{assignedTime}, " +
            "moderator_online_status = #{onlineStatus}, update_time = #{assignedTime} " +
            "WHERE id = #{recordId} AND status = 'pending' AND assigned_moderator_id IS NULL")
    int claimPendingAssignment(@Param("recordId") Long recordId,
                               @Param("moderatorId") Long moderatorId,
                               @Param("assignedTime") LocalDateTime assignedTime,
                               @Param("onlineStatus") Integer onlineStatus);

    @Update("UPDATE moderation_record SET assigned_moderator_id = #{newModeratorId}, status = 'pending', " +
            "assigned_time = #{assignedTime}, moderator_online_status = #{onlineStatus}, update_time = #{assignedTime} " +
            "WHERE id = #{recordId} AND assigned_moderator_id = #{oldModeratorId} " +
            "AND status IN ('pending', 'in_progress')")
    int reassignActiveAssignment(@Param("recordId") Long recordId,
                                 @Param("oldModeratorId") Long oldModeratorId,
                                 @Param("newModeratorId") Long newModeratorId,
                                 @Param("assignedTime") LocalDateTime assignedTime,
                                 @Param("onlineStatus") Integer onlineStatus);

    @Select("SELECT COUNT(*) FROM moderation_record WHERE assigned_moderator_id = #{moderatorId} " +
            "AND status IN ('pending', 'in_progress')")
    int countActiveAssignments(@Param("moderatorId") Long moderatorId);

    @Update("UPDATE moderation_record SET status = #{reviewResult}, review_result = #{reviewResult}, reviewer_id = #{reviewerId}, assigned_moderator_id = COALESCE(assigned_moderator_id, #{reviewerId}), assigned_time = COALESCE(assigned_time, #{reviewTime}), review_time = #{reviewTime}, review_reason = #{reviewReason}, update_time = #{reviewTime} WHERE id = #{recordId} AND status = #{expectedStatus} AND (assigned_moderator_id IS NULL OR assigned_moderator_id = #{reviewerId})")
    int completeActiveTarget(@Param("recordId") Long recordId,
                             @Param("expectedStatus") String expectedStatus,
                             @Param("reviewerId") Long reviewerId,
                             @Param("reviewResult") String reviewResult,
                             @Param("reviewReason") String reviewReason,
                             @Param("reviewTime") LocalDateTime reviewTime);

    @Update("UPDATE moderation_record SET status = 'pending', assigned_moderator_id = NULL, " +
            "assigned_time = NULL, moderator_online_status = NULL, review_reason = #{skipReason}, " +
            "update_time = #{skipTime} WHERE id = #{recordId} AND assigned_moderator_id = #{reviewerId} " +
            "AND status IN ('pending', 'in_progress')")
    int releaseActiveAssignment(@Param("recordId") Long recordId,
                                @Param("reviewerId") Long reviewerId,
                                @Param("skipReason") String skipReason,
                                @Param("skipTime") LocalDateTime skipTime);

    @Select("SELECT COUNT(*) FROM moderation_record WHERE reviewer_id = #{moderatorId} " +
            "AND status IN ('approved', 'rejected') " +
            "AND review_time >= #{todayStart} AND review_time < #{tomorrowStart}")
    int countCompletedReviews(@Param("moderatorId") Long moderatorId,
                              @Param("todayStart") LocalDateTime todayStart,
                              @Param("tomorrowStart") LocalDateTime tomorrowStart);

    @Select("SELECT " +
            "COALESCE(SUM(CASE WHEN reviewer_id = #{moderatorId} " +
            "  AND status IN ('approved', 'rejected') " +
            "  AND review_time >= #{todayStart} AND review_time < #{tomorrowStart} THEN 1 ELSE 0 END), 0) AS todayCompleted, " +
            "COALESCE(SUM(CASE WHEN assigned_moderator_id = #{moderatorId} AND status = 'in_progress' THEN 1 ELSE 0 END), 0) AS inProgressCount, " +
            "COALESCE(SUM(CASE WHEN assigned_moderator_id = #{moderatorId} AND status = 'pending' THEN 1 ELSE 0 END), 0) AS pendingCount, " +
            "COALESCE(SUM(CASE WHEN reviewer_id = #{moderatorId} AND status = 'approved' THEN 1 ELSE 0 END), 0) AS approvedCount, " +
            "COALESCE(SUM(CASE WHEN reviewer_id = #{moderatorId} AND status = 'rejected' THEN 1 ELSE 0 END), 0) AS rejectedCount, " +
            "COALESCE(SUM(CASE WHEN reviewer_id = #{moderatorId} AND status = 'skipped' THEN 1 ELSE 0 END), 0) AS historicalSkippedCount, " +
            "COUNT(*) AS reviewedOrAssignedTasks, " +
            "COALESCE(AVG(CASE WHEN reviewer_id = #{moderatorId} " +
            "  AND status IN ('approved', 'rejected') " +
            "  AND assigned_time IS NOT NULL AND review_time IS NOT NULL " +
            "  THEN TIMESTAMPDIFF(MICROSECOND, assigned_time, review_time) / 1000 ELSE NULL END), 0) AS avgProcessTime " +
            "FROM moderation_record " +
            "WHERE assigned_moderator_id = #{moderatorId} OR reviewer_id = #{moderatorId}")
    Map<String, Object> selectModeratorStats(@Param("moderatorId") Long moderatorId,
                                             @Param("todayStart") LocalDateTime todayStart,
                                             @Param("tomorrowStart") LocalDateTime tomorrowStart);

    @Select({"<script>",
            "SELECT",
            "COUNT(*) AS totalRecords,",
            "COALESCE(SUM(CASE WHEN status = 'pending' AND assigned_moderator_id IS NULL THEN 1 ELSE 0 END), 0) AS pendingAssignment,",
            "COALESCE(SUM(CASE WHEN status = 'pending' THEN 1 ELSE 0 END), 0) AS pendingRecords,",
            "COALESCE(SUM(CASE WHEN status = 'in_progress' THEN 1 ELSE 0 END), 0) AS inProgress,",
            "COALESCE(SUM(CASE WHEN status IN ('approved', 'rejected') THEN 1 ELSE 0 END), 0) AS completedRecords,",
            "COALESCE(SUM(CASE WHEN status IN ('approved', 'rejected')",
            "  AND review_time &gt;= #{todayStart} AND review_time &lt; #{tomorrowStart} THEN 1 ELSE 0 END), 0) AS todayCompleted",
            "FROM moderation_record",
            "WHERE 1 = 1",
            "<if test='includeAdminOnly == false'>",
            "  AND target_type NOT IN",
            "  <foreach collection='adminOnlyTypes' item='type' open='(' separator=',' close=')'>#{type}</foreach>",
            "</if>",
            "</script>"})
    Map<String, Object> selectGlobalStats(@Param("includeAdminOnly") boolean includeAdminOnly,
                                          @Param("adminOnlyTypes") Collection<String> adminOnlyTypes,
                                          @Param("todayStart") LocalDateTime todayStart,
                                          @Param("tomorrowStart") LocalDateTime tomorrowStart);

    @Select({"<script>",
            "SELECT target_type AS targetType, COUNT(*) AS typeCount",
            "FROM moderation_record",
            "WHERE status = 'pending'",
            "  AND assigned_moderator_id IS NULL",
            "<if test='includeAdminOnly == false'>",
            "  AND target_type NOT IN",
            "  <foreach collection='adminOnlyTypes' item='type' open='(' separator=',' close=')'>#{type}</foreach>",
            "</if>",
            "GROUP BY target_type",
            "</script>"})
    List<Map<String, Object>> selectPendingAssignmentByType(@Param("includeAdminOnly") boolean includeAdminOnly,
                                                            @Param("adminOnlyTypes") Collection<String> adminOnlyTypes);
}
