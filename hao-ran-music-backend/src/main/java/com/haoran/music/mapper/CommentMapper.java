package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.dto.comment.CommentUserCountDTO;
import com.haoran.music.entity.Comment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;





@Mapper
public interface CommentMapper extends BaseMapper<Comment> {




    @Select({
            "<script>",
            "SELECT user_id AS user_id, COUNT(*) AS comment_count",
            "FROM comment",
            "WHERE user_id IN",
            "<foreach collection='userIds' item='userId' open='(' separator=',' close=')'>",
            "  #{userId}",
            "</foreach>",
            "AND target_type = #{targetType}",
            "AND target_id = #{targetId}",
            "AND status = 1",
            "AND deleted = 0",
            "GROUP BY user_id",
            "</script>"
    })
    List<CommentUserCountDTO> selectUserCommentCounts(@Param("userIds") Collection<Long> userIds,
                                                        @Param("targetType") Integer targetType,
                                                        @Param("targetId") Long targetId);

    @Update("UPDATE comment SET like_count = GREATEST(COALESCE(like_count, 0) + #{delta}, 0), update_time = NOW() WHERE id = #{commentId} AND status = 1 AND deleted = 0")
    int adjustLikeCount(@Param("commentId") Long commentId, @Param("delta") int delta);

    @Update("UPDATE comment SET reply_count = GREATEST(COALESCE(reply_count, 0) + #{delta}, 0), update_time = NOW() WHERE id = #{commentId} AND status = 1 AND deleted = 0")
    int adjustReplyCount(@Param("commentId") Long commentId, @Param("delta") int delta);
}
