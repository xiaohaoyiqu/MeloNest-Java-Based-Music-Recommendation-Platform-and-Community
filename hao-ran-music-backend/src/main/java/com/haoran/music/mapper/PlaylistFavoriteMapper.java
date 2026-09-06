package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.PlaylistFavorite;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;





@Mapper
public interface PlaylistFavoriteMapper extends BaseMapper<PlaylistFavorite> {

    @Select("SELECT * FROM playlist_favorite "
            + "WHERE user_id = #{userId} AND playlist_id = #{playlistId} AND deleted = 0 LIMIT 1")
    PlaylistFavorite selectActive(@Param("userId") Long userId, @Param("playlistId") Long playlistId);

    @Update("UPDATE playlist_favorite SET deleted = 0, update_time = NOW() WHERE user_id = #{userId} AND playlist_id = #{playlistId} AND deleted = 1 LIMIT 1")
    int restoreDeleted(@Param("userId") Long userId, @Param("playlistId") Long playlistId);

    @Insert("INSERT IGNORE INTO playlist_favorite (user_id, playlist_id, deleted, create_time, update_time) "
            + "VALUES (#{userId}, #{playlistId}, 0, NOW(), NOW())")
    int insertIgnoreActive(@Param("userId") Long userId, @Param("playlistId") Long playlistId);

    @Delete("DELETE FROM playlist_favorite "
            + "WHERE user_id = #{userId} AND playlist_id = #{playlistId} AND deleted = 1")
    int deleteDeletedHistory(@Param("userId") Long userId, @Param("playlistId") Long playlistId);

    @Update("UPDATE playlist_favorite SET deleted = 1, update_time = NOW() WHERE user_id = #{userId} AND playlist_id = #{playlistId} AND deleted = 0")
    int logicalDeleteActive(@Param("userId") Long userId, @Param("playlistId") Long playlistId);
}
