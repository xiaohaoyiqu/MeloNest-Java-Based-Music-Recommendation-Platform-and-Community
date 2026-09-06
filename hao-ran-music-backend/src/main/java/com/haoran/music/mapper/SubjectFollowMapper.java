package com.haoran.music.mapper;

import com.haoran.music.entity.SubjectFollow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;






@Mapper
public interface SubjectFollowMapper {









    @Insert("INSERT INTO subject_follow "
            + "(follower_user_id, target_type, target_id, status, source_type, "
            + "contributes_public_stats, create_time, update_time) "
            + "VALUES (#{followerUserId}, #{targetType}, #{targetId}, 'active', 'application', "
            + "#{contributesPublicStats}, NOW(), NOW()) "
            + "ON DUPLICATE KEY UPDATE "
            + "contributes_public_stats = IF(status = 'cancelled', VALUES(contributes_public_stats), contributes_public_stats), "
            + "source_type = IF(status = 'cancelled', VALUES(source_type), source_type), "
            + "update_time = IF(status = 'cancelled', NOW(), update_time), "
            + "status = IF(status = 'cancelled', 'active', status)")
    int activate(@Param("followerUserId") Long followerUserId,
                 @Param("targetType") String targetType,
                 @Param("targetId") Long targetId,
                 @Param("contributesPublicStats") boolean contributesPublicStats);









    @Select("SELECT id, follower_user_id, target_type, target_id, status, source_type, source_id, "
            + "contributes_public_stats, create_time, update_time FROM subject_follow "
            + "WHERE follower_user_id = #{followerUserId} AND target_type = #{targetType} "
            + "AND target_id = #{targetId} AND status = 'active' FOR UPDATE")
    SubjectFollow selectActiveForUpdate(@Param("followerUserId") Long followerUserId,
                                        @Param("targetType") String targetType,
                                        @Param("targetId") Long targetId);







    @Update("UPDATE subject_follow SET status = 'cancelled', update_time = NOW() WHERE id = #{id} AND status = 'active'")
    int cancel(@Param("id") Long id);









    @Update("UPDATE subject_follow SET status = 'cancelled', update_time = NOW() WHERE follower_user_id = #{followerUserId} AND target_type = #{targetType} AND target_id = #{targetId} AND status = 'active'")
    int cancelBySubject(@Param("followerUserId") Long followerUserId,
                        @Param("targetType") String targetType,
                        @Param("targetId") Long targetId);









    @Select({"<script>",
            "SELECT target_id FROM subject_follow WHERE follower_user_id = #{followerUserId}",
            "AND target_type = #{targetType} AND status = 'active' AND target_id IN",
            "<foreach collection='targetIds' item='targetId' open='(' separator=',' close=')'>",
            "#{targetId}",
            "</foreach>",
            "</script>"})
    List<Long> selectActiveTargetIds(@Param("followerUserId") Long followerUserId,
                                     @Param("targetType") String targetType,
                                     @Param("targetIds") List<Long> targetIds);
}
