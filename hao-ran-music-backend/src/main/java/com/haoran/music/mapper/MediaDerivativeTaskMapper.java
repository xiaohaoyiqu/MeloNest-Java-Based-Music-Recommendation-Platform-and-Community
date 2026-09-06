package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.MediaDerivativeTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;






@Mapper
public interface MediaDerivativeTaskMapper extends BaseMapper<MediaDerivativeTask> {









    @Select("SELECT * FROM media_derivative_task "
            + "WHERE media_type = #{mediaType} AND media_id = #{mediaId} AND source_url = #{sourceUrl} "
            + "ORDER BY id DESC LIMIT 1")
    MediaDerivativeTask selectLatest(@Param("mediaType") String mediaType,
                                     @Param("mediaId") Long mediaId,
                                     @Param("sourceUrl") String sourceUrl);








    @Select("SELECT * FROM media_derivative_task "
            + "WHERE ((status IN ('PENDING', 'FAILED') "
            + "AND retry_count < max_retry_count "
            + "AND (next_retry_time IS NULL OR next_retry_time <= NOW())) "
            + "OR (status = 'PROCESSING' "
            + "AND retry_count < max_retry_count "
            + "AND started_at IS NOT NULL "
            + "AND started_at <= #{staleProcessingBefore})) "
            + "ORDER BY created_at ASC LIMIT #{limit}")
    List<MediaDerivativeTask> selectDueTasks(@Param("limit") Integer limit,
                                             @Param("staleProcessingBefore") LocalDateTime staleProcessingBefore);








    @Update("UPDATE media_derivative_task SET status = 'PROCESSING', started_at = NOW(), finished_at = NULL, updated_at = NOW() WHERE id = #{taskId} AND ((status IN ('PENDING', 'FAILED') AND retry_count < max_retry_count AND (next_retry_time IS NULL OR next_retry_time <= NOW())) OR (status = 'PROCESSING' AND retry_count < max_retry_count AND started_at IS NOT NULL AND started_at <= #{staleProcessingBefore}))")
    int claimForProcessing(@Param("taskId") Long taskId,
                           @Param("staleProcessingBefore") LocalDateTime staleProcessingBefore);

    @Select("SELECT status, COUNT(*) AS count, MAX(updated_at) AS latestUpdatedAt, "
            + "SUM(retry_count) AS totalRetryCount "
            + "FROM media_derivative_task GROUP BY status ORDER BY status")
    List<Map<String, Object>> selectStatusSummary();

    @Select("SELECT id, media_type AS mediaType, media_id AS mediaId, status, retry_count AS retryCount, "
            + "max_retry_count AS maxRetryCount, last_error AS lastError, next_retry_time AS nextRetryTime, "
            + "updated_at AS updatedAt "
            + "FROM media_derivative_task "
            + "WHERE status IN ('FAILED', 'PROCESSING') "
            + "ORDER BY updated_at DESC LIMIT #{limit}")
    List<Map<String, Object>> selectRecentRiskTasks(@Param("limit") Integer limit);






    @Select("SELECT COUNT(*) AS outstandingCount, MIN(created_at) AS oldestCreatedAt, "
            + "MIN(updated_at) AS oldestUpdatedAt, COALESCE(SUM(retry_count), 0) AS totalRetryCount, "
            + "COALESCE(MAX(retry_count), 0) AS maxRetryCountObserved, "
            + "SUM(CASE WHEN status IN ('PENDING', 'FAILED') "
            + "AND (next_retry_time IS NULL OR next_retry_time <= NOW()) THEN 1 ELSE 0 END) AS dueCount "
            + "FROM media_derivative_task WHERE status IN ('PENDING', 'PROCESSING', 'FAILED')")
    Map<String, Object> selectRecoveryObservation();
}
