




package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;




@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {

    @Select("<script>"
            + "SELECT COUNT(*) AS totalActions, "
            + "COALESCE(SUM(CASE WHEN success = 1 OR success IS NULL THEN 1 ELSE 0 END), 0) AS successCount, "
            + "COALESCE(SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END), 0) AS failCount, "
            + "COALESCE(ROUND(AVG(duration_seconds)), 0) AS avgDurationSeconds "
            + "FROM audit_log WHERE 1 = 1 "
            + "<if test='startTime != null'>AND create_time &gt;= #{startTime} </if>"
            + "<if test='endTime != null'>AND create_time &lt;= #{endTime} </if>"
            + "</script>")
    Map<String, Object> selectAuditSummary(@Param("startTime") LocalDateTime startTime,
                                           @Param("endTime") LocalDateTime endTime);

    @Select("<script>"
            + "SELECT COALESCE(operation_type, 'unknown') AS statKey, COUNT(*) AS statCount "
            + "FROM audit_log WHERE 1 = 1 "
            + "<if test='startTime != null'>AND create_time &gt;= #{startTime} </if>"
            + "<if test='endTime != null'>AND create_time &lt;= #{endTime} </if>"
            + "GROUP BY COALESCE(operation_type, 'unknown') ORDER BY statCount DESC"
            + "</script>")
    List<Map<String, Object>> selectActionStats(@Param("startTime") LocalDateTime startTime,
                                                @Param("endTime") LocalDateTime endTime);

    @Select("<script>"
            + "SELECT COALESCE(target_type, 'unknown') AS statKey, COUNT(*) AS statCount "
            + "FROM audit_log WHERE 1 = 1 "
            + "<if test='startTime != null'>AND create_time &gt;= #{startTime} </if>"
            + "<if test='endTime != null'>AND create_time &lt;= #{endTime} </if>"
            + "GROUP BY COALESCE(target_type, 'unknown') ORDER BY statCount DESC"
            + "</script>")
    List<Map<String, Object>> selectRecordTypeStats(@Param("startTime") LocalDateTime startTime,
                                                    @Param("endTime") LocalDateTime endTime);

    @Select("<script>"
            + "SELECT operator_id AS moderatorId, "
            + "COALESCE(MAX(operator_name), CONCAT('Moderator #', operator_id)) AS moderatorName, "
            + "COUNT(*) AS totalActions, "
            + "COALESCE(SUM(CASE WHEN operation_type = 'approve' THEN 1 ELSE 0 END), 0) AS approvedCount, "
            + "COALESCE(SUM(CASE WHEN operation_type = 'reject' THEN 1 ELSE 0 END), 0) AS rejectedCount, "
            + "COALESCE(SUM(CASE WHEN operation_type = 'skip' THEN 1 ELSE 0 END), 0) AS skippedCount, "
            + "CASE WHEN COALESCE(SUM(CASE WHEN operation_type IN ('approve', 'reject') THEN 1 ELSE 0 END), 0) = 0 "
            + "THEN 0 ELSE "
            + "COALESCE(SUM(CASE WHEN operation_type = 'approve' THEN 1 ELSE 0 END), 0) * 100 / "
            + "COALESCE(SUM(CASE WHEN operation_type IN ('approve', 'reject') THEN 1 ELSE 0 END), 1) END AS successRate, "
            + "COALESCE(ROUND(AVG(duration_seconds)), 0) AS avgDurationSeconds "
            + "FROM audit_log WHERE operator_id IS NOT NULL "
            + "<if test='startTime != null'>AND create_time &gt;= #{startTime} </if>"
            + "<if test='endTime != null'>AND create_time &lt;= #{endTime} </if>"
            + "GROUP BY operator_id ORDER BY totalActions DESC LIMIT 200"
            + "</script>")
    List<Map<String, Object>> selectModeratorWorkStats(@Param("startTime") LocalDateTime startTime,
                                                       @Param("endTime") LocalDateTime endTime);
}
