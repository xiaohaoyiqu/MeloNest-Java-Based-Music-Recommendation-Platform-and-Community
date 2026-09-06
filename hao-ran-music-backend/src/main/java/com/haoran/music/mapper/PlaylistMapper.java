package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.Playlist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;





@Mapper
public interface PlaylistMapper extends BaseMapper<Playlist> {

    @Select("SELECT DISTINCT user_id FROM playlist "
            + "WHERE status = 1 AND deleted = 0 AND is_public = 1 AND user_id IS NOT NULL")
    List<Long> selectPublicCreatorIds();

    @Select({"<script>",
            "SELECT ps.playlist_id AS playlistId, s.language AS language, COUNT(*) AS songCount ",
            "FROM playlist_song ps JOIN song s ON s.id = ps.song_id ",
            "WHERE ps.deleted = 0 AND s.deleted = 0 AND s.status = 1 ",
            "AND ps.playlist_id IN ",
            "<foreach collection='playlistIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> ",
            "GROUP BY ps.playlist_id, s.language",
            "</script>"})
    List<Map<String, Object>> selectLanguageStats(@Param("playlistIds") List<Long> playlistIds);

    @Select({"<script>",
            "SELECT ps.playlist_id AS playlistId, COUNT(*) AS songCount ",
            "FROM playlist_song ps JOIN song s ON s.id = ps.song_id ",
            "WHERE ps.deleted = 0 AND s.deleted = 0 AND s.status = 1 ",
            "AND ps.playlist_id IN ",
            "<foreach collection='playlistIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> ",
            "GROUP BY ps.playlist_id",
            "</script>"})
    List<Map<String, Object>> selectVisibleSongCounts(@Param("playlistIds") List<Long> playlistIds);

    @Select("SELECT * FROM playlist WHERE id = #{playlistId} AND deleted = 0 FOR UPDATE")
    Playlist selectByIdForUpdate(@Param("playlistId") Long playlistId);

    @Update("UPDATE playlist SET song_count = GREATEST(COALESCE(song_count, 0) + #{delta}, 0), update_time = NOW() WHERE id = #{playlistId} AND deleted = 0")
    int adjustSongCount(@Param("playlistId") Long playlistId, @Param("delta") int delta);

    @Update("UPDATE playlist SET favorite_count = GREATEST(COALESCE(favorite_count, 0) + #{delta}, 0), update_time = NOW() WHERE id = #{playlistId} AND deleted = 0")
    int adjustFavoriteCount(@Param("playlistId") Long playlistId, @Param("delta") int delta);
}
