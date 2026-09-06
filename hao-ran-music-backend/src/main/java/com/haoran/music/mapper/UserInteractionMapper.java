




package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.common.constant.PublicStatsSql;
import com.haoran.music.entity.UserInteraction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;




@Mapper
public interface UserInteractionMapper extends BaseMapper<UserInteraction> {

    @Select("SELECT ui.interaction_type AS interactionType, ui.target_type AS targetType, " +
            "COUNT(*) AS interactionCount " +
            "FROM user_interaction ui " +
            "JOIN `user` u ON u.id = ui.target_user_id " +
            "WHERE ui.user_id = #{userId}" + PublicStatsSql.USER_FILTER + " " +
            "GROUP BY ui.interaction_type, ui.target_type")
    List<Map<String, Object>> selectPublicInteractionTypeStats(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) AS totalCount, COUNT(DISTINCT ui.target_user_id) AS uniqueUsersCount " +
            "FROM user_interaction ui " +
            "JOIN `user` u ON u.id = ui.target_user_id " +
            "WHERE ui.user_id = #{userId}" + PublicStatsSql.USER_FILTER)
    Map<String, Object> selectPublicInteractionTotals(@Param("userId") Long userId);

    @Select("SELECT ui.target_user_id AS targetUserId, COUNT(*) AS interactionCount, " +
            "MAX(ui.interaction_time) AS lastInteractionTime, " +
            "u.nickname AS targetUserName, u.avatar AS targetUserAvatar " +
            "FROM user_interaction ui " +
            "JOIN `user` u ON u.id = ui.target_user_id " +
            "WHERE ui.user_id = #{userId}" + PublicStatsSql.USER_FILTER + " " +
            "GROUP BY ui.target_user_id, u.nickname, u.avatar " +
            "ORDER BY interactionCount DESC, lastInteractionTime DESC " +
            "LIMIT #{limit}")
    List<Map<String, Object>> selectMostInteractedPublicUsers(@Param("userId") Long userId,
                                                                @Param("limit") Integer limit);
}
