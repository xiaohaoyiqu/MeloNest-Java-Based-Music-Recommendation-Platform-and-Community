   
                      
                          
   

package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.MusicTopic;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

   
             
   
@Mapper
public interface MusicTopicMapper extends BaseMapper<MusicTopic> {

    @Update("UPDATE topic SET post_count = GREATEST(COALESCE(post_count, 0) + #{delta}, 0), update_time = NOW() WHERE name = #{name} AND is_deleted = 0")
    int adjustPostCountByName(@Param("name") String name, @Param("delta") int delta);

    @Update("UPDATE topic SET follower_count = GREATEST(COALESCE(follower_count, 0) + #{delta}, 0), update_time = NOW() WHERE id = #{topicId} AND is_deleted = 0")
    int adjustFollowerCount(@Param("topicId") Long topicId, @Param("delta") int delta);
}
