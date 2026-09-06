   
                      
                          
   

package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.TopicFollow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

   
             
   
@Mapper
public interface TopicFollowMapper extends BaseMapper<TopicFollow> {

    @Insert("INSERT IGNORE INTO topic_follow (topic_id, user_id, create_time) "
            + "VALUES (#{topicId}, #{userId}, NOW())")
    int insertIgnore(@Param("topicId") Long topicId, @Param("userId") Long userId);

    @Delete("DELETE FROM topic_follow WHERE topic_id = #{topicId} AND user_id = #{userId}")
    int deleteByTopicAndUser(@Param("topicId") Long topicId, @Param("userId") Long userId);
}
