package com.haoran.music.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;






@Mapper
public interface MusicSquareWorkLikeMapper {

    @Insert("INSERT IGNORE INTO music_square_work_like (work_id, user_id, create_time) "
            + "VALUES (#{workId}, #{userId}, NOW())")
    int insertIgnore(@Param("workId") Long workId, @Param("userId") Long userId);

    @Delete("DELETE FROM music_square_work_like WHERE work_id = #{workId} AND user_id = #{userId}")
    int deleteByWorkAndUser(@Param("workId") Long workId, @Param("userId") Long userId);
}
