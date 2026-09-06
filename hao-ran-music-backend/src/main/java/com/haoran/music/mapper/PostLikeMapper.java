




package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.PostLike;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;




@Mapper
public interface PostLikeMapper extends BaseMapper<PostLike> {

    @Insert("INSERT IGNORE INTO post_like (post_id, user_id, create_time) "
            + "VALUES (#{postId}, #{userId}, NOW())")
    int insertIgnore(@Param("postId") Long postId, @Param("userId") Long userId);

    @Delete("DELETE FROM post_like WHERE post_id = #{postId} AND user_id = #{userId}")
    int deleteByPostAndUser(@Param("postId") Long postId, @Param("userId") Long userId);
}
