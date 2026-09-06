


package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.CreatorEligibilityOutboxEvent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;





@Mapper
public interface CreatorEligibilityOutboxMapper extends BaseMapper<CreatorEligibilityOutboxEvent> {

    @Insert("INSERT INTO creator_eligibility_outbox (" +
            "creator_id, event_version, schema_version, event_type, old_status, new_status, " +
            "is_creator, creator_type, operator_id, reason, status, attempt_count, max_attempts, " +
            "deleted, create_time, update_time) VALUES (" +
            "#{creatorId}, #{eventVersion}, #{schemaVersion}, #{eventType}, #{oldStatus}, #{newStatus}, " +
            "#{isCreator}, #{creatorType}, #{operatorId}, #{reason}, #{status}, 0, #{maxAttempts}, " +
            "0, NOW(), NOW())")
    int insertEvent(CreatorEligibilityOutboxEvent event);

    @Select("SELECT * FROM creator_eligibility_outbox WHERE id = #{id} AND deleted = 0")
    CreatorEligibilityOutboxEvent selectActiveById(@Param("id") Long id);

    @Select("SELECT id FROM creator_eligibility_outbox " +
            "WHERE deleted = 0 AND attempt_count < max_attempts AND " +
            "((status = 'pending' AND (next_retry_time IS NULL OR next_retry_time <= NOW())) " +
            "OR (status = 'failed' AND next_retry_time IS NOT NULL AND next_retry_time <= NOW())) " +
            "ORDER BY create_time ASC LIMIT #{limit}")
    List<Long> selectDueEventIds(@Param("limit") int limit);




    @Update("UPDATE creator_eligibility_outbox SET status = 'failed', error_message = '执行节点中断，事件自动恢复', next_retry_time = CASE WHEN attempt_count < max_attempts THEN NOW() ELSE NULL END, completed_at = NOW(), update_time = NOW() WHERE deleted = 0 AND status = 'processing' AND started_at IS NOT NULL AND started_at < DATE_SUB(NOW(), INTERVAL 10 MINUTE)")
    int recoverStaleProcessingEvents();




    @Update("UPDATE creator_eligibility_outbox SET status = 'processing', attempt_count = attempt_count + 1, started_at = NOW(), completed_at = NULL, error_message = NULL, update_time = NOW() WHERE id = #{id} AND deleted = 0 AND attempt_count < max_attempts AND ((status = 'pending' AND (next_retry_time IS NULL OR next_retry_time <= NOW())) OR (status = 'failed' AND next_retry_time IS NOT NULL AND next_retry_time <= NOW()))")
    int claimEvent(@Param("id") Long id);




    @Update("UPDATE creator_eligibility_outbox SET status = 'success', error_message = NULL, next_retry_time = NULL, completed_at = NOW(), update_time = NOW() WHERE id = #{id} AND deleted = 0 AND status = 'processing' AND started_at = #{startedAt}")
    int markSuccess(@Param("id") Long id, @Param("startedAt") LocalDateTime startedAt);




    @Update("UPDATE creator_eligibility_outbox SET status = 'failed', error_message = #{errorMessage}, next_retry_time = CASE WHEN #{retryDelayMinutes} IS NULL THEN NULL ELSE DATE_ADD(NOW(), INTERVAL #{retryDelayMinutes} MINUTE) END, completed_at = NOW(), update_time = NOW() WHERE id = #{id} AND deleted = 0 AND status = 'processing' AND started_at = #{startedAt}")
    int markFailed(@Param("id") Long id,
                   @Param("startedAt") LocalDateTime startedAt,
                   @Param("errorMessage") String errorMessage,
                   @Param("retryDelayMinutes") Integer retryDelayMinutes);




    @Update("UPDATE creator_eligibility_outbox SET status = 'failed', error_message = '创作者资格事件缺少认领令牌', next_retry_time = NULL, completed_at = NOW(), update_time = NOW() WHERE id = #{id} AND deleted = 0 AND status = 'processing' AND started_at IS NULL")
    int markFailedWithoutClaimToken(@Param("id") Long id);
}
