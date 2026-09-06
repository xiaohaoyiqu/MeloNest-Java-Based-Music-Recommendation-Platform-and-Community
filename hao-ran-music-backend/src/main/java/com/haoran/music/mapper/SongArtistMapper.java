   
                      
   
package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.SongArtist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

   
                               
   
@Mapper
public interface SongArtistMapper extends BaseMapper<SongArtist> {

    @Select("SELECT COUNT(DISTINCT sa.song_id) " +
            "FROM song_artist sa " +
            "INNER JOIN song s ON s.id = sa.song_id " +
            "WHERE sa.artist_id = #{artistId} AND s.status = 1 AND s.deleted = 0")
    Long countSongByArtistId(@Param("artistId") Long artistId);

    @Select("SELECT COUNT(DISTINCT s.album_id) " +
            "FROM song_artist sa " +
            "INNER JOIN song s ON s.id = sa.song_id " +
            "WHERE sa.artist_id = #{artistId} AND s.status = 1 AND s.deleted = 0 AND s.album_id IS NOT NULL")
    Long countAlbumByArtistId(@Param("artistId") Long artistId);

    @Update("UPDATE artist ar LEFT JOIN (  SELECT sa.artist_id, COUNT(DISTINCT s.id) AS song_count, COUNT(DISTINCT s.album_id) AS album_count   FROM song_artist sa   INNER JOIN song s ON s.id = sa.song_id AND s.status = 1 AND s.deleted = 0   GROUP BY sa.artist_id) stats ON stats.artist_id = ar.id SET ar.song_count = COALESCE(stats.song_count, 0),     ar.album_count = COALESCE(stats.album_count, 0),     ar.update_time = NOW() WHERE ar.deleted = 0")
    int syncAllArtistSongAlbumCounts();
}