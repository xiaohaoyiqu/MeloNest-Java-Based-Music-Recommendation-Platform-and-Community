package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.CreatorWork;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

   
                      
                             
   
@Mapper
public interface CreatorWorkMapper extends BaseMapper<CreatorWork> {

    @Update("UPDATE creator_work SET status = #{status}, reviewer_id = #{reviewerId}, review_reason = #{reviewReason}, review_time = #{reviewTime}, update_time = NOW() WHERE id = #{id} AND status = 0 AND deleted = 0")
    int reviewPendingWork(CreatorWork work);
}
