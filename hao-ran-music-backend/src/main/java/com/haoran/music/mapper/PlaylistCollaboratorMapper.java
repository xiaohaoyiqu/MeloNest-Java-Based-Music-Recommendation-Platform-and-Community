package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.PlaylistCollaborator;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;





@Mapper
public interface PlaylistCollaboratorMapper extends BaseMapper<PlaylistCollaborator> {

    @Select("SELECT COUNT(*) FROM playlist_collaborator WHERE playlist_id = #{playlistId} AND deleted = 0")
    Long countActiveByPlaylistId(@Param("playlistId") Long playlistId);

    @Select("SELECT * FROM playlist_collaborator "
            + "WHERE playlist_id = #{playlistId} AND user_id = #{userId} "
            + "ORDER BY deleted ASC, id DESC LIMIT 1")
    PlaylistCollaborator selectAnyByPlaylistAndUser(@Param("playlistId") Long playlistId,
                                                    @Param("userId") Long userId);

    @Select("SELECT * FROM playlist_collaborator "
            + "WHERE playlist_id = #{playlistId} AND user_id = #{userId} "
            + "AND status = 'accepted' AND deleted = 0 LIMIT 1")
    PlaylistCollaborator selectAccepted(@Param("playlistId") Long playlistId,
                                        @Param("userId") Long userId);

    @Insert("INSERT IGNORE INTO playlist_collaborator "
            + "(playlist_id, user_id, role, can_add, can_remove, can_edit, joined_time, invited_by, status, deleted) "
            + "VALUES (#{collaborator.playlistId}, #{collaborator.userId}, #{collaborator.role}, "
            + "#{collaborator.canAdd}, #{collaborator.canRemove}, #{collaborator.canEdit}, "
            + "NOW(), #{collaborator.invitedBy}, #{collaborator.status}, 0)")
    int insertIgnore(@Param("collaborator") PlaylistCollaborator collaborator);

    @Update("UPDATE playlist_collaborator SET role = #{role}, can_add = #{canAdd}, can_remove = #{canRemove}, can_edit = #{canEdit}, status = 'pending', invited_by = #{invitedBy}, joined_time = NOW(), deleted = 0 WHERE id = #{id}")
    int restoreInvitation(@Param("id") Long id,
                          @Param("role") String role,
                          @Param("canAdd") Integer canAdd,
                          @Param("canRemove") Integer canRemove,
                          @Param("canEdit") Integer canEdit,
                          @Param("invitedBy") Long invitedBy);







    @Update("UPDATE playlist_collaborator SET role = 'owner', can_add = 1, can_remove = 1, can_edit = 1, status = 'accepted', joined_time = NOW(), invited_by = NULL, deleted = 0 WHERE id = #{id} AND role = 'owner' AND status = 'closed' AND deleted = 1")
    int restoreClosedOwner(@Param("id") Long id);

    @Update("UPDATE playlist_collaborator SET status = 'accepted' WHERE playlist_id = #{playlistId} AND user_id = #{userId} AND status = 'pending' AND deleted = 0")
    int acceptPending(@Param("playlistId") Long playlistId, @Param("userId") Long userId);

    @Update("UPDATE playlist_collaborator SET status = 'rejected', deleted = 1 WHERE playlist_id = #{playlistId} AND user_id = #{userId} AND status = 'pending' AND deleted = 0")
    int rejectPending(@Param("playlistId") Long playlistId, @Param("userId") Long userId);

    @Update("UPDATE playlist_collaborator SET deleted = 1 WHERE playlist_id = #{playlistId} AND user_id = #{userId} AND role <> 'owner' AND deleted = 0")
    int removeActiveNonOwner(@Param("playlistId") Long playlistId, @Param("userId") Long userId);

    @Update("UPDATE playlist_collaborator SET status = 'closed', deleted = 1 WHERE playlist_id = #{playlistId} AND deleted = 0")
    int closeAllByPlaylistId(@Param("playlistId") Long playlistId);

    @Select("SELECT user_id FROM playlist_collaborator WHERE playlist_id = #{playlistId} "
            + "AND role <> 'owner' AND status = 'accepted' AND deleted = 0 ORDER BY user_id")
    List<Long> selectActiveNonOwnerUserIds(@Param("playlistId") Long playlistId);

    @Update("UPDATE playlist_collaborator SET can_add = #{canAdd}, can_remove = #{canRemove}, can_edit = #{canEdit} WHERE playlist_id = #{playlistId} AND user_id = #{userId} AND role <> 'owner' AND deleted = 0")
    int updatePermissions(@Param("playlistId") Long playlistId,
                          @Param("userId") Long userId,
                          @Param("canAdd") Integer canAdd,
                          @Param("canRemove") Integer canRemove,
                          @Param("canEdit") Integer canEdit);
}
