


package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.PlayEventDeadLetter;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface PlayEventDeadLetterMapper extends BaseMapper<PlayEventDeadLetter> {

    @Insert("INSERT INTO play_event_dead_letter " +
            "(event_id, topic, partition_id, offset_value, message_key, payload, retry_count, " +
            "error_message, status, first_failed_at, last_failed_at, create_time, update_time) " +
            "VALUES (#{eventId}, #{topic}, #{partitionId}, #{offsetValue}, #{messageKey}, #{payload}, #{retryCount}, " +
            "#{errorMessage}, 'PENDING', NOW(), NOW(), NOW(), NOW()) " +
            "ON DUPLICATE KEY UPDATE retry_count = retry_count + 1, error_message = VALUES(error_message), " +
            "last_failed_at = NOW(), update_time = NOW()")
    int insertOrUpdateFailure(@Param("eventId") String eventId,
                              @Param("topic") String topic,
                              @Param("partitionId") Integer partitionId,
                              @Param("offsetValue") Long offsetValue,
                              @Param("messageKey") String messageKey,
                              @Param("payload") String payload,
                              @Param("retryCount") Integer retryCount,
                              @Param("errorMessage") String errorMessage);

    @Delete("DELETE FROM play_event_dead_letter " +
            "WHERE status IN ('RESOLVED', 'IGNORED') " +
            "AND update_time < DATE_SUB(NOW(), INTERVAL #{retentionDays} DAY) " +
            "LIMIT #{limit}")
    int deleteOldResolved(@Param("retentionDays") int retentionDays, @Param("limit") int limit);

    @Update("UPDATE play_event_dead_letter SET status = 'RESOLVED', handler_id = #{handlerId}, handle_reason = #{reason}, resolved_at = NOW(), update_time = NOW() WHERE id = #{id} AND status = 'PENDING'")
    int markResolved(@Param("id") Long id,
                     @Param("handlerId") Long handlerId,
                     @Param("reason") String reason);

    @Update("UPDATE play_event_dead_letter SET status = 'IGNORED', handler_id = #{handlerId}, handle_reason = #{reason}, resolved_at = NOW(), update_time = NOW() WHERE id = #{id} AND status = 'PENDING'")
    int markIgnored(@Param("id") Long id,
                    @Param("handlerId") Long handlerId,
                    @Param("reason") String reason);

    @Update("UPDATE play_event_dead_letter SET retry_count = retry_count + 1, error_message = #{errorMessage}, last_failed_at = NOW(), update_time = NOW() WHERE id = #{id} AND status = 'PENDING'")
    int markReplayFailed(@Param("id") Long id, @Param("errorMessage") String errorMessage);

    @Select("SELECT status, COUNT(*) AS count, MAX(last_failed_at) AS lastFailedAt " +
            "FROM play_event_dead_letter GROUP BY status ORDER BY status")
    List<Map<String, Object>> selectStatusSummary();
}
