package com.haoran.music.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;
import java.util.List;

   
                                  
  
                      
   
@Mapper
public interface PlaylistCollaborationAuditMapper {

    @Insert("INSERT INTO playlist_collaboration_audit "
            + "(event_id, playlist_id, actor_id, target_user_id, event_type, before_summary, "
            + "after_summary, reason, create_time) VALUES "
            + "(#{eventId}, #{playlistId}, #{actorId}, #{targetUserId}, #{eventType}, "
            + "#{beforeSummary}, #{afterSummary}, #{reason}, NOW())")
    int insertEvent(@Param("eventId") String eventId,
                    @Param("playlistId") Long playlistId,
                    @Param("actorId") Long actorId,
                    @Param("targetUserId") Long targetUserId,
                    @Param("eventType") String eventType,
                    @Param("beforeSummary") String beforeSummary,
                    @Param("afterSummary") String afterSummary,
                    @Param("reason") String reason);

    @Select("SELECT event_id AS eventId, playlist_id AS playlistId, actor_id AS actorId, "
            + "target_user_id AS targetUserId, event_type AS eventType, before_summary AS beforeSummary, "
            + "after_summary AS afterSummary, reason, create_time AS createTime "
            + "FROM playlist_collaboration_audit WHERE event_id = #{eventId} LIMIT 1")
    Map<String, Object> selectByEventId(@Param("eventId") String eventId);

       
                       
      
                             
                        
                   
       
    @Select("SELECT event_id AS eventId, playlist_id AS playlistId, actor_id AS actorId, "
            + "target_user_id AS targetUserId, event_type AS eventType, before_summary AS beforeSummary, "
            + "after_summary AS afterSummary, reason, create_time AS createTime "
            + "FROM playlist_collaboration_audit WHERE playlist_id = #{playlistId} "
            + "ORDER BY create_time DESC, id DESC LIMIT #{limit}")
    List<Map<String, Object>> selectRecentByPlaylist(@Param("playlistId") Long playlistId,
                                                     @Param("limit") int limit);
}
