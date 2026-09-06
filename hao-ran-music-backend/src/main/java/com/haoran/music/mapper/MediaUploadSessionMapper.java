package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.MediaUploadSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;






@Mapper
public interface MediaUploadSessionMapper extends BaseMapper<MediaUploadSession> {







    @Select("SELECT * FROM media_upload_session WHERE session_token = #{sessionToken} LIMIT 1")
    MediaUploadSession selectByToken(@Param("sessionToken") String sessionToken);









    @Update("UPDATE media_upload_session SET uploaded_count = uploaded_count + 1, update_time = NOW() WHERE id = #{sessionId} AND owner_id = #{ownerId} AND status = 'OPEN' AND expires_at > #{now} AND uploaded_count < max_files")
    int reserveSlot(@Param("sessionId") Long sessionId,
                    @Param("ownerId") Long ownerId,
                    @Param("now") LocalDateTime now);








    @Update("UPDATE media_upload_session SET uploaded_count = uploaded_count - 1, update_time = NOW() WHERE id = #{sessionId} AND owner_id = #{ownerId} AND uploaded_count > 0")
    int releaseSlot(@Param("sessionId") Long sessionId, @Param("ownerId") Long ownerId);











    @Update("UPDATE media_upload_session SET status = 'COMPLETED', target_type = #{targetType}, target_id = #{targetId}, update_time = NOW() WHERE id = #{sessionId} AND owner_id = #{ownerId} AND ((status = 'OPEN' AND expires_at > #{now}) OR (status = 'COMPLETED' AND target_type = #{targetType} AND target_id = #{targetId}))")
    int claimTarget(@Param("sessionId") Long sessionId,
                    @Param("ownerId") Long ownerId,
                    @Param("targetType") String targetType,
                    @Param("targetId") Long targetId,
                    @Param("now") LocalDateTime now);








    @Update("UPDATE media_upload_session SET status = 'CANCELLED', expires_at = NOW(), update_time = NOW() WHERE id = #{sessionId} AND owner_id = #{ownerId} AND status = 'OPEN'")
    int cancel(@Param("sessionId") Long sessionId, @Param("ownerId") Long ownerId);







    @Update("UPDATE media_upload_session SET status = 'EXPIRED', update_time = NOW() WHERE status = 'OPEN' AND expires_at <= NOW() ORDER BY id ASC LIMIT #{limit}")
    int expireSessions(@Param("limit") Integer limit);
}
