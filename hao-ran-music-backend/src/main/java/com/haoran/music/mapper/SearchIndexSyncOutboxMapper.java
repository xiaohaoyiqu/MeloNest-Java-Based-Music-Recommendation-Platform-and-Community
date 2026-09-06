package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.SearchIndexSyncOutboxEvent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

   
                            
  
                      
   
@Mapper
public interface SearchIndexSyncOutboxMapper extends BaseMapper<SearchIndexSyncOutboxEvent> {

    @Insert("INSERT INTO search_index_sync_outbox "
            + "(event_id, resource_type, resource_id, status, attempt_count, max_attempts, "
            + "deleted, create_time, update_time) VALUES "
            + "(#{eventId}, #{resourceType}, #{resourceId}, 'pending', 0, #{maxAttempts}, 0, NOW(), NOW())")
    int insertEvent(SearchIndexSyncOutboxEvent event);

    @Select("SELECT event_id FROM search_index_sync_outbox WHERE deleted = 0 "
            + "AND attempt_count < max_attempts AND "
            + "((status IN ('pending','failed') AND (next_retry_time IS NULL OR next_retry_time <= NOW())) "
            + "OR (status = 'processing' AND lease_until IS NOT NULL AND lease_until <= NOW())) "
            + "ORDER BY id ASC LIMIT #{limit}")
    List<String> selectDueEventIds(@Param("limit") int limit);

    @Update("UPDATE search_index_sync_outbox SET status = 'processing', worker_id = #{workerId}, lease_until = DATE_ADD(NOW(), INTERVAL #{leaseSeconds} SECOND), attempt_count = attempt_count + 1, error_category = NULL, update_time = NOW() WHERE event_id = #{eventId} AND deleted = 0 AND attempt_count < max_attempts AND ((status IN ('pending','failed') AND (next_retry_time IS NULL OR next_retry_time <= NOW())) OR (status = 'processing' AND lease_until IS NOT NULL AND lease_until <= NOW()))")
    int claimEvent(@Param("eventId") String eventId,
                   @Param("workerId") String workerId,
                   @Param("leaseSeconds") int leaseSeconds);

    @Select("SELECT * FROM search_index_sync_outbox WHERE event_id = #{eventId} "
            + "AND deleted = 0 AND status = 'processing' AND worker_id = #{workerId} "
            + "AND lease_until IS NOT NULL AND lease_until > NOW() LIMIT 1")
    SearchIndexSyncOutboxEvent selectClaimedEvent(@Param("eventId") String eventId,
                                                   @Param("workerId") String workerId);

    @Update("UPDATE search_index_sync_outbox SET status = 'success', completed_at = NOW(), worker_id = NULL, lease_until = NULL, next_retry_time = NULL, error_category = NULL, update_time = NOW() WHERE event_id = #{eventId} AND deleted = 0 AND status = 'processing' AND worker_id = #{workerId}")
    int markSuccess(@Param("eventId") String eventId, @Param("workerId") String workerId);

    @Update("UPDATE search_index_sync_outbox SET status = 'failed', worker_id = NULL, lease_until = NULL, error_category = #{errorCategory}, next_retry_time = CASE WHEN #{retryDelaySeconds} IS NULL THEN NULL ELSE DATE_ADD(NOW(), INTERVAL #{retryDelaySeconds} SECOND) END, update_time = NOW() WHERE event_id = #{eventId} AND deleted = 0 AND status = 'processing' AND worker_id = #{workerId}")
    int markFailed(@Param("eventId") String eventId,
                   @Param("workerId") String workerId,
                   @Param("errorCategory") String errorCategory,
                   @Param("retryDelaySeconds") Integer retryDelaySeconds);

    @Update("UPDATE search_index_sync_outbox SET status = 'failed', attempt_count = max_attempts, worker_id = NULL, lease_until = NULL, error_category = #{errorCategory}, next_retry_time = NULL, update_time = NOW() WHERE event_id = #{eventId} AND deleted = 0 AND status = 'processing' AND worker_id = #{workerId}")
    int markTerminalFailed(@Param("eventId") String eventId,
                           @Param("workerId") String workerId,
                           @Param("errorCategory") String errorCategory);

    @Update("UPDATE search_index_sync_outbox SET status = 'pending', attempt_count = 0, worker_id = NULL, lease_until = NULL, error_category = NULL, next_retry_time = NOW(), completed_at = NULL, update_time = NOW() WHERE event_id = #{eventId} AND deleted = 0 AND status = 'failed'")
    int requeueFailed(@Param("eventId") String eventId);

    @Select("SELECT status, COUNT(*) AS eventCount, MIN(create_time) AS oldestCreateTime "
            + "FROM search_index_sync_outbox WHERE deleted = 0 GROUP BY status ORDER BY status")
    List<Map<String, Object>> selectStatusSummary();

    @Select("SELECT event_id AS eventId, resource_type AS resourceType, resource_id AS resourceId, "
            + "attempt_count AS attemptCount, max_attempts AS maxAttempts, "
            + "error_category AS errorCategory, next_retry_time AS nextRetryTime, update_time AS updateTime "
            + "FROM search_index_sync_outbox WHERE deleted = 0 AND status = 'failed' "
            + "ORDER BY update_time DESC LIMIT #{limit}")
    List<Map<String, Object>> selectRecentFailures(@Param("limit") int limit);
}
