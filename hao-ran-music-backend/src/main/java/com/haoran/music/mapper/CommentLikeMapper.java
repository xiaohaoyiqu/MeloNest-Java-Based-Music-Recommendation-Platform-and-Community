package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.CommentLike;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;





@Mapper
public interface CommentLikeMapper extends BaseMapper<CommentLike> {

    @Insert("INSERT IGNORE INTO comment_like "
            + "(id, user_id, comment_id, is_like, deleted, create_time, update_time) "
            + "VALUES (#{id}, #{userId}, #{commentId}, 1, 0, NOW(), NOW())")
    int insertIgnoreActive(@Param("id") Long id,
                           @Param("userId") Long userId,
                           @Param("commentId") Long commentId);

    @Delete("DELETE FROM comment_like "
            + "WHERE user_id = #{userId} AND comment_id = #{commentId} AND deleted = 1")
    int deleteDeletedHistory(@Param("userId") Long userId, @Param("commentId") Long commentId);

    @Delete("DELETE FROM comment_like WHERE comment_id = #{commentId} AND deleted = 1")
    int deleteDeletedHistoryByComment(@Param("commentId") Long commentId);

    @Update("UPDATE comment_like SET deleted = 1, update_time = NOW() WHERE user_id = #{userId} AND comment_id = #{commentId} AND is_like = 1 AND deleted = 0")
    int deleteActiveLike(@Param("userId") Long userId, @Param("commentId") Long commentId);
}
