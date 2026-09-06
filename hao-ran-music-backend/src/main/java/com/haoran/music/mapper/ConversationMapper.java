




package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;




@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {







    @Select("SELECT * FROM conversation " +
            "WHERE (user_a_id = #{userId} OR user_b_id = #{userId}) " +
            "AND is_deleted = 0 " +
            "ORDER BY last_message_time DESC")
    List<Conversation> getConversationsByUserId(@Param("userId") Long userId);








    @Select("SELECT * FROM conversation " +
            "WHERE ((user_a_id = #{userId1} AND user_b_id = #{userId2}) " +
            "OR (user_a_id = #{userId2} AND user_b_id = #{userId1})) " +
            "AND is_deleted = 0 " +
            "LIMIT 1")
    Conversation getConversationBetweenUsers(@Param("userId1") Long userId1,
                                             @Param("userId2") Long userId2);

    @Select("SELECT * FROM conversation " +
            "WHERE ((user_a_id = #{userId1} AND user_b_id = #{userId2}) " +
            "OR (user_a_id = #{userId2} AND user_b_id = #{userId1})) " +
            "AND is_deleted = 0 LIMIT 1 FOR UPDATE")
    Conversation getConversationBetweenUsersForUpdate(@Param("userId1") Long userId1,
                                                       @Param("userId2") Long userId2);

    @Update("UPDATE conversation SET " +
            "user_a_unread_count = CASE WHEN user_a_id = #{receiverId} " +
            "THEN GREATEST(user_a_unread_count - 1, 0) ELSE user_a_unread_count END, " +
            "user_b_unread_count = CASE WHEN user_b_id = #{receiverId} " +
            "THEN GREATEST(user_b_unread_count - 1, 0) ELSE user_b_unread_count END " +
            "WHERE ((user_a_id = #{receiverId} AND user_b_id = #{senderId}) " +
            "OR (user_a_id = #{senderId} AND user_b_id = #{receiverId})) AND is_deleted = 0")
    int decrementUnreadCount(@Param("receiverId") Long receiverId,
                             @Param("senderId") Long senderId);







    @Select("SELECT CASE " +
            "WHEN user_a_id = #{userId} THEN user_a_unread_count " +
            "ELSE user_b_unread_count " +
            "END as unread_count " +
            "FROM conversation " +
            "WHERE (user_a_id = #{userId} OR user_b_id = #{userId}) " +
            "AND is_deleted = 0")
    List<Integer> getUnreadCounts(@Param("userId") Long userId);
}
