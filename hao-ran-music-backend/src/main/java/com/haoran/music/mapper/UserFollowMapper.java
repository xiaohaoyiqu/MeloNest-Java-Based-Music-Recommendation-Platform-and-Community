package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.UserFollow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

   
                      
                            
   
@Mapper
public interface UserFollowMapper extends BaseMapper<UserFollow> {

    @Select("SELECT followee_id FROM user_follow " +
            "WHERE follower_id = #{userId} AND deleted = 0")
    List<Long> selectFolloweeIds(@Param("userId") Long userId);

    @Select({
            "<script>",
            "SELECT followee_id FROM user_follow",
            "WHERE follower_id = #{userId}",
            "AND followee_id IN",
            "<foreach collection='followeeIds' item='followeeId' open='(' separator=',' close=')'>",
            "  #{followeeId}",
            "</foreach>",
            "AND deleted = 0",
            "</script>"
    })
    List<Long> selectFolloweeIds(@Param("userId") Long userId,
                                 @Param("followeeIds") Collection<Long> followeeIds);

    @Select({
            "<script>",
            "SELECT follower_id FROM user_follow",
            "WHERE follower_id IN",
            "<foreach collection='friendIds' item='friendId' open='(' separator=',' close=')'>",
            "  #{friendId}",
            "</foreach>",
            "AND followee_id = #{userId}",
            "AND deleted = 0",
            "</script>"
    })
    List<Long> selectMutualFollowerIds(@Param("userId") Long userId,
                                        @Param("friendIds") Collection<Long> friendIds);
}
