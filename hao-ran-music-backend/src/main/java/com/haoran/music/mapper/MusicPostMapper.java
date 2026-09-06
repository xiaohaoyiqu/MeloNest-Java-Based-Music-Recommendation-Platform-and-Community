   
                      
                          
   

package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.MusicPost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

   
             
  
@Mapper
public interface MusicPostMapper extends BaseMapper<MusicPost> {

       
                 
                    
                   
       
    List<MusicPost> selectByCondition(@Param("params") Map<String, Object> params);

    @Select({"<script>",
            "SELECT COUNT(*) AS postCount,",
            "COALESCE(SUM(like_count), 0) AS likeCount,",
            "COALESCE(SUM(comment_count), 0) AS commentCount,",
            "COALESCE(SUM(share_count), 0) AS shareCount",
            "FROM post WHERE user_id = #{userId} AND is_deleted = 0",
            "<if test='includeAll == false'>",
            "AND (visibility = 'public' OR visibility IS NULL OR visibility = ''",
            "<if test='includeFollowers'> OR visibility = 'followers' </if>",
            ")",
            "</if>",
            "</script>"})
    Map<String, Object> selectUserPostStats(@Param("userId") Long userId,
                                            @Param("includeAll") boolean includeAll,
                                            @Param("includeFollowers") boolean includeFollowers);

    @Select("SELECT COUNT(*) AS total, "
            + "COALESCE(SUM(CASE WHEN visibility = 'pending' THEN 1 ELSE 0 END), 0) AS pending, "
            + "COALESCE(SUM(CASE WHEN visibility = 'public' THEN 1 ELSE 0 END), 0) AS published, "
            + "COALESCE(SUM(CASE WHEN visibility = 'private' THEN 1 ELSE 0 END), 0) AS rejected "
            + "FROM post WHERE user_id = #{userId} AND is_deleted = 0 AND post_type = 'video'")
    Map<String, Object> selectVideoPostStats(@Param("userId") Long userId);

    @Update("UPDATE post SET like_count = COALESCE(like_count, 0) + 1, update_time = NOW() WHERE id = #{postId} AND is_deleted = 0")
    int incrementLikeCount(@Param("postId") Long postId);

    @Update("UPDATE post SET like_count = GREATEST(COALESCE(like_count, 0) - 1, 0), update_time = NOW() WHERE id = #{postId} AND is_deleted = 0")
    int decrementLikeCount(@Param("postId") Long postId);

    @Update("UPDATE post SET comment_count = GREATEST(COALESCE(comment_count, 0) + #{delta}, 0), update_time = NOW() WHERE id = #{postId} AND is_deleted = 0")
    int adjustCommentCount(@Param("postId") Long postId, @Param("delta") int delta);

    @Update("UPDATE post SET is_deleted = 1, update_time = NOW() WHERE id = #{postId} AND user_id = #{userId} AND is_deleted = 0")
    int markDeletedByOwner(@Param("postId") Long postId, @Param("userId") Long userId);

    @Update("UPDATE post SET content = #{content}, update_time = NOW() WHERE id = #{postId} AND user_id = #{userId} AND is_deleted = 0")
    int updateContentByOwner(@Param("postId") Long postId,
                             @Param("userId") Long userId,
                             @Param("content") String content);

    @Update("UPDATE post SET allow_comment = #{allowComment}, update_time = NOW() WHERE id = #{postId} AND user_id = #{userId} AND is_deleted = 0")
    int updateAuthorCommentSetting(@Param("postId") Long postId,
                                   @Param("userId") Long userId,
                                   @Param("allowComment") Integer allowComment);

    @Update("UPDATE post SET official_comment_closed = #{closed}, update_time = NOW() WHERE id = #{postId} AND is_deleted = 0")
    int updateOfficialCommentClosed(@Param("postId") Long postId, @Param("closed") Boolean closed);
}
