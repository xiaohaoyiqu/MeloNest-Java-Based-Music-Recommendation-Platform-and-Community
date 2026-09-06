   
                      
                          
   

package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

   
             
   
@Mapper
public interface MessageMapper extends BaseMapper<Message> {

       
                     
                                    
       
    @Select("SELECT * FROM message " +
            "WHERE ((sender_id = #{currentUserId} AND receiver_id = #{otherUserId} AND is_deleted_by_sender = 0) " +
            "OR (sender_id = #{otherUserId} AND receiver_id = #{currentUserId} AND is_deleted_by_receiver = 0)) " +
            "AND is_deleted = 0 " +
            "ORDER BY create_time DESC " +
            "LIMIT #{limit} OFFSET #{offset}")
    List<Message> getMessagesBetweenUsers(@Param("currentUserId") Long currentUserId,
                                          @Param("otherUserId") Long otherUserId,
                                          @Param("offset") long offset,
                                          @Param("limit") int limit);

       
                           
       
    @Select("SELECT COUNT(*) FROM message " +
            "WHERE ((sender_id = #{currentUserId} AND receiver_id = #{otherUserId} AND is_deleted_by_sender = 0) " +
            "OR (sender_id = #{otherUserId} AND receiver_id = #{currentUserId} AND is_deleted_by_receiver = 0)) " +
            "AND is_deleted = 0")
    long countMessagesBetweenUsers(@Param("currentUserId") Long currentUserId,
                                   @Param("otherUserId") Long otherUserId);

    @Select("SELECT * FROM message " +
            "WHERE ((sender_id = #{userId1} AND receiver_id = #{userId2}) " +
            "OR (sender_id = #{userId2} AND receiver_id = #{userId1})) " +
            "AND is_recalled = 0 AND is_deleted = 0 " +
            "ORDER BY create_time DESC, id DESC LIMIT 1")
    Message getLatestActiveMessageBetweenUsers(@Param("userId1") Long userId1,
                                                @Param("userId2") Long userId2);

    @Update("UPDATE message SET is_read = 1, read_time = #{readTime}, update_time = #{readTime} " +
            "WHERE id = #{messageId} AND receiver_id = #{receiverId} " +
            "AND is_read = 0 AND is_recalled = 0 AND is_deleted = 0")
    int markActiveMessageAsRead(@Param("messageId") Long messageId,
                                @Param("receiverId") Long receiverId,
                                @Param("readTime") java.time.LocalDateTime readTime);

    @Update("UPDATE message SET is_recalled = 1, update_time = CURRENT_TIMESTAMP " +
            "WHERE id = #{messageId} AND sender_id = #{senderId} " +
            "AND is_read = 0 AND is_recalled = 0 AND is_deleted = 0")
    int recallUnreadMessage(@Param("messageId") Long messageId,
                            @Param("senderId") Long senderId);

    @Update("UPDATE message SET is_recalled = 1, update_time = CURRENT_TIMESTAMP " +
            "WHERE id = #{messageId} AND sender_id = #{senderId} " +
            "AND is_read = 1 AND is_recalled = 0 AND is_deleted = 0")
    int recallReadMessage(@Param("messageId") Long messageId,
                          @Param("senderId") Long senderId);

       
                  
      
                              
                     
       
    @Select("SELECT COUNT(*) FROM message " +
            "WHERE receiver_id = #{receiverId} AND is_read = 0 " +
            "AND is_recalled = 0 AND is_deleted_by_receiver = 0 AND is_deleted = 0")
    int getUnreadCount(@Param("receiverId") Long receiverId);

       
                     
      
                              
                              
                     
       
    @Select("SELECT COUNT(*) FROM message " +
            "WHERE receiver_id = #{receiverId} AND sender_id = #{senderId} " +
            "AND is_read = 0 AND is_recalled = 0 " +
            "AND is_deleted_by_receiver = 0 AND is_deleted = 0")
    int getUnreadCountFromUser(@Param("receiverId") Long receiverId,
                               @Param("senderId") Long senderId);
}
