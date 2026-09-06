


package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.PlayEventReceipt;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PlayEventReceiptMapper extends BaseMapper<PlayEventReceipt> {

    @Select("SELECT * FROM play_event_receipt WHERE event_id = #{eventId} LIMIT 1")
    PlayEventReceipt selectByEventId(@Param("eventId") String eventId);

    @Insert("INSERT INTO play_event_receipt " +
            "(event_id, user_id, song_id, is_local, status, create_time, update_time) " +
            "VALUES (#{eventId}, #{userId}, #{songId}, #{isLocal}, 'PROCESSING', NOW(), NOW())")
    int insertProcessing(@Param("eventId") String eventId,
                         @Param("userId") Long userId,
                         @Param("songId") String songId,
                         @Param("isLocal") Integer isLocal);

    @Update("UPDATE play_event_receipt SET status = 'PROCESSED', processed_at = NOW(), update_time = NOW() WHERE event_id = #{eventId} AND status = 'PROCESSING'")
    int markProcessed(@Param("eventId") String eventId);

    @Delete("DELETE FROM play_event_receipt " +
            "WHERE status = 'PROCESSED' AND processed_at < DATE_SUB(NOW(), INTERVAL #{retentionDays} DAY) " +
            "LIMIT #{limit}")
    int deleteOldProcessed(@Param("retentionDays") int retentionDays, @Param("limit") int limit);

    @Delete("DELETE FROM play_event_receipt " +
            "WHERE status = 'PROCESSING' AND update_time < DATE_SUB(NOW(), INTERVAL #{retentionDays} DAY) " +
            "LIMIT #{limit}")
    int deleteStaleProcessing(@Param("retentionDays") int retentionDays, @Param("limit") int limit);
}
