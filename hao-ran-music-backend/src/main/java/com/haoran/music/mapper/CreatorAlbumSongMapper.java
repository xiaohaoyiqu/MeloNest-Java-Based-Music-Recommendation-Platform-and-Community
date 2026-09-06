package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.CreatorAlbumSong;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;





@Mapper
public interface CreatorAlbumSongMapper extends BaseMapper<CreatorAlbumSong> {




    List<CreatorAlbumSong> getSongsByAlbumId(@Param("albumId") Long albumId);




    int updateAlbumStats(@Param("albumId") Long albumId);
}
