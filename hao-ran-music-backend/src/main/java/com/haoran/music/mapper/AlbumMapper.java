package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.Album;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;





@Mapper
public interface AlbumMapper extends BaseMapper<Album> {






    @Update("UPDATE album SET play_count = COALESCE(play_count, 0) + 1 WHERE id = #{albumId}")
    void incrementPlayCount(@Param("albumId") Long albumId);

    @Update("UPDATE album SET comment_count = GREATEST(COALESCE(comment_count, 0) + #{delta}, 0), update_time = NOW() WHERE id = #{albumId} AND deleted = 0")
    int adjustCommentCount(@Param("albumId") Long albumId, @Param("delta") int delta);
}
