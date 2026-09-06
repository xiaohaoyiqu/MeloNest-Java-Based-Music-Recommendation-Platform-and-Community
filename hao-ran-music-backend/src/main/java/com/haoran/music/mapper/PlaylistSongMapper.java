package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.haoran.music.entity.PlaylistSong;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

   
                      
                            
   
@Mapper
public interface PlaylistSongMapper extends BaseMapper<PlaylistSong> {

    @Insert("INSERT IGNORE INTO playlist_song "
            + "(id, playlist_id, song_id, sort_order, add_time, deleted) "
            + "VALUES (#{song.id}, #{song.playlistId}, #{song.songId}, #{song.sortOrder}, NOW(), 0)")
    int insertIgnore(@Param("song") PlaylistSong song);

    @Insert({
            "<script>",
            "INSERT INTO playlist_song ",
            "(playlist_id, song_id, sort_order, add_time, deleted, create_time, update_time) VALUES ",
            "<foreach collection='songs' item='song' separator=','>",
            "(#{song.playlistId}, #{song.songId}, #{song.sortOrder}, #{song.addTime}, 0, NOW(), NOW())",
            "</foreach>",
            "</script>"
    })
    int insertBatch(@Param("songs") List<PlaylistSong> songs);

    @Select("SELECT COALESCE(MAX(sort_order), -1) FROM playlist_song "
            + "WHERE playlist_id = #{playlistId} AND deleted = 0")
    Integer selectMaxSortOrder(@Param("playlistId") Long playlistId);

    @Select("SELECT song_id FROM playlist_song WHERE playlist_id = #{playlistId} "
            + "AND deleted = 0 ORDER BY sort_order, id")
    List<Long> selectActiveSongIds(@Param("playlistId") Long playlistId);

    @Select("SELECT DISTINCT s.uploader_id FROM playlist_song ps JOIN song s ON s.id = ps.song_id "
            + "WHERE ps.playlist_id = #{playlistId} AND ps.deleted = 0 AND s.deleted = 0 "
            + "AND s.uploader_id IS NOT NULL")
    List<Long> selectUploaderIdsByPlaylist(@Param("playlistId") Long playlistId);

    IPage<PlaylistSong> selectVisibleSongPage(
            Page<PlaylistSong> page,
            @Param("playlistId") Long playlistId,
            @Param("keyword") String keyword,
            @Param("language") String language,
            @Param("sortBy") String sortBy,
            @Param("publicPlaylist") boolean publicPlaylist,
            @Param("allowedUploaderIds") java.util.Collection<Long> allowedUploaderIds);

       
                                                                                                  
       
    Long countVisibleSongs(@Param("playlistId") Long playlistId,
                           @Param("publicPlaylist") boolean publicPlaylist,
                           @Param("allowedUploaderIds") java.util.Collection<Long> allowedUploaderIds);

    @Update({
            "<script>",
            "UPDATE playlist_song SET sort_order = CASE song_id ",
            "<foreach collection='songIds' item='songId' index='index'>",
            "WHEN #{songId} THEN #{index} + 1 ",
            "</foreach>",
            "ELSE sort_order END, update_time = NOW() ",
            "WHERE playlist_id = #{playlistId} AND deleted = 0 AND song_id IN ",
            "<foreach collection='songIds' item='songId' open='(' separator=',' close=')'>",
            "#{songId}",
            "</foreach>",
            "</script>"
    })
    int updateSortOrdersBatch(@Param("playlistId") Long playlistId,
                              @Param("songIds") List<Long> songIds);

       
                         
      
                             
                                 
       
    @Select("SELECT CONCAT(song_id, ':', sort_order) FROM playlist_song "
            + "WHERE playlist_id = #{playlistId} AND deleted = 0 ORDER BY sort_order, id")
    List<String> selectActiveOrderTokens(@Param("playlistId") Long playlistId);

    @Delete({
            "<script>",
            "DELETE FROM playlist_song ",
            "WHERE playlist_id = #{playlistId} AND deleted = 1 ",
            "AND song_id IN ",
            "<foreach collection='songIds' item='songId' open='(' separator=',' close=')'>",
            "#{songId}",
            "</foreach>",
            "</script>"
    })
    int deleteDeletedHistoryBySongIds(@Param("playlistId") Long playlistId,
                                      @Param("songIds") java.util.Collection<Long> songIds);

    @Update({
            "<script>",
            "UPDATE playlist_song SET deleted = 1 ",
            "WHERE playlist_id = #{playlistId} AND deleted = 0 ",
            "AND song_id IN ",
            "<foreach collection='songIds' item='songId' open='(' separator=',' close=')'>",
            "#{songId}",
            "</foreach>",
            "</script>"
    })
    int deleteActiveBySongIds(@Param("playlistId") Long playlistId, @Param("songIds") java.util.Collection<Long> songIds);

    @Update({
            "<script>",
            "UPDATE playlist_song SET deleted = 1 ",
            "WHERE id IN ",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>",
            "#{id}",
            "</foreach>",
            " AND deleted = 0",
            "</script>"
    })
    int deleteActiveByIds(@Param("ids") java.util.Collection<Long> ids);
}
