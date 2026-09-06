package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.haoran.music.entity.Song;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;





@Mapper
public interface SongMapper extends BaseMapper<Song> {




    @Select("SELECT * FROM song WHERE id = #{songId} AND deleted = 0 LIMIT 1 FOR UPDATE")
    Song selectByIdForUpdate(@Param("songId") Long songId);








    @Select("<script>" +
            "SELECT * FROM song " +
            "WHERE status = 1 AND deleted = 0 " +
            "<if test='type != null and type != \"\"'> " +
            "AND main_type = #{type} " +
            "</if>" +
            "ORDER BY hot_score DESC, play_count DESC " +
            "LIMIT #{size}" +
            "</script>")
    List<Song> selectHotSongsByType(@Param("type") String type, @Param("size") Integer size);








    IPage<Song> selectNewSongs(Page<Song> page, @Param("days") Integer days);






    @Update("UPDATE song SET hot_score = hot_score + 1 WHERE id = #{songId} AND deleted = 0")
    void updateHotScore(@Param("songId") Long songId);






    @Update("UPDATE song SET download_count = download_count + 1 WHERE id = #{songId}")
    void updateDownloadCount(@Param("songId") Long songId);






    @Update("UPDATE song SET play_count = COALESCE(play_count, 0) + 1 WHERE id = #{songId} AND deleted = 0")
    void incrementPlayCount(@Param("songId") Long songId);

    @Update("UPDATE song SET comment_count = GREATEST(COALESCE(comment_count, 0) + #{delta}, 0), update_time = NOW() WHERE id = #{songId} AND deleted = 0")
    int adjustCommentCount(@Param("songId") Long songId, @Param("delta") int delta);

    @Select("SELECT DISTINCT s.* " +
            "FROM song s " +
            "INNER JOIN song_artist sa ON sa.song_id = s.id " +
            "WHERE sa.artist_id = #{artistId} AND s.status = 1 AND s.deleted = 0 " +
            "ORDER BY s.play_count DESC, s.hot_score DESC " +
            "LIMIT #{limit}")
    List<Song> selectHotSongsByArtistId(@Param("artistId") Long artistId, @Param("limit") Integer limit);

    @Select("SELECT COUNT(*) FROM song WHERE status = 1 AND deleted = 0")
    Long countPublicSongs();

    @Select("SELECT COUNT(*) FROM song WHERE status = 1 AND deleted = 0 "
            + "AND (NULLIF(TRIM(url_standard), '') IS NOT NULL "
            + "OR NULLIF(TRIM(url_high), '') IS NOT NULL "
            + "OR NULLIF(TRIM(url_lossless), '') IS NOT NULL)")
    Long countPublicPlayableSongs();

    @Select("SELECT COUNT(*) FROM song WHERE status = 1 AND deleted = 0 "
            + "AND NULLIF(TRIM(url_standard), '') IS NULL "
            + "AND NULLIF(TRIM(url_high), '') IS NULL "
            + "AND NULLIF(TRIM(url_lossless), '') IS NULL")
    Long countPublicSongsMissingPlayableUrl();

    @Select("SELECT id, name, artist_names AS artistNames, uploader_id AS uploaderId, "
            + "play_count AS playCount, hot_score AS hotScore, create_time AS createTime "
            + "FROM song WHERE status = 1 AND deleted = 0 "
            + "AND NULLIF(TRIM(url_standard), '') IS NULL "
            + "AND NULLIF(TRIM(url_high), '') IS NULL "
            + "AND NULLIF(TRIM(url_lossless), '') IS NULL "
            + "ORDER BY hot_score DESC, play_count DESC, update_time DESC LIMIT #{limit}")
    List<Map<String, Object>> selectPublicSongsMissingPlayableUrl(@Param("limit") Integer limit);
}
