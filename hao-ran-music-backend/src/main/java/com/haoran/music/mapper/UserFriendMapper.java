




package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.UserFriend;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;




@Mapper
public interface UserFriendMapper extends BaseMapper<UserFriend> {

    @Select({
            "<script>",
            "SELECT user_id FROM user_friend",
            "WHERE friend_id = #{userId}",
            "AND user_id IN",
            "<foreach collection='friendIds' item='friendId' open='(' separator=',' close=')'>",
            "  #{friendId}",
            "</foreach>",
            "AND status = 'accepted'",
            "AND deleted = 0",
            "</script>"
    })
    List<Long> selectAcceptedFriendIds(@Param("userId") Long userId,
                                       @Param("friendIds") Collection<Long> friendIds);
}
