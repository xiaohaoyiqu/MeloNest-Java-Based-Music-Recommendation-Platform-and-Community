



package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.EmojiUploadBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface EmojiUploadBatchMapper extends BaseMapper<EmojiUploadBatch> {

    @Select("SELECT * FROM emoji_upload_batch WHERE creator_id = #{creatorId} " +
            "AND idempotency_key = #{key} LIMIT 1 FOR UPDATE")
    EmojiUploadBatch selectByCreatorAndKeyForUpdate(@Param("creatorId") Long creatorId,
                                                     @Param("key") String key);

    @Select("SELECT * FROM emoji_upload_batch WHERE id = #{id} FOR UPDATE")
    EmojiUploadBatch selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM emoji_upload_batch WHERE creator_id = #{creatorId} " +
            "AND status = 'PROCESSING' AND expires_at > NOW()")
    long countActiveProcessingByCreator(@Param("creatorId") Long creatorId);

    @Select("SELECT COALESCE(SUM(file_count), 0) FROM emoji_upload_batch " +
            "WHERE creator_id = #{creatorId} AND created_at >= #{since}")
    long sumReservedFilesSince(@Param("creatorId") Long creatorId,
                               @Param("since") LocalDateTime since);

    @Select("SELECT COALESCE(SUM(total_bytes), 0) FROM emoji_upload_batch " +
            "WHERE creator_id = #{creatorId} AND created_at >= #{since}")
    long sumReservedBytesSince(@Param("creatorId") Long creatorId,
                               @Param("since") LocalDateTime since);

    @Select("SELECT * FROM emoji_upload_batch WHERE " +
            "(status IN ('FAILED', 'CLEANING') OR (status = 'PROCESSING' AND expires_at <= NOW())) " +
            "AND updated_at <= #{before} ORDER BY updated_at ASC LIMIT #{limit}")
    List<EmojiUploadBatch> selectCleanupCandidates(@Param("before") LocalDateTime before,
                                                    @Param("limit") int limit);

    @Update("UPDATE emoji_upload_batch SET status = 'CLEANING', updated_at = NOW() WHERE id = #{id} " +
            "AND (status = 'FAILED' OR (status = 'PROCESSING' AND expires_at <= NOW()) " +
            "OR (status = 'CLEANING' AND updated_at <= #{staleBefore}))")
    int claimForCleanup(@Param("id") Long id, @Param("staleBefore") LocalDateTime staleBefore);

    @Delete("DELETE FROM emoji_upload_batch WHERE status IN ('SUCCEEDED', 'CLEANED') " +
            "AND updated_at < #{before} LIMIT #{limit}")
    int deleteExpiredTerminal(@Param("before") LocalDateTime before, @Param("limit") int limit);
}
