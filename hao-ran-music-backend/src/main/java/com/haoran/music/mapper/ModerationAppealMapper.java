package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.ModerationAppeal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

   
                      
                          
   
@Mapper
public interface ModerationAppealMapper extends BaseMapper<ModerationAppeal> {

    @Update("UPDATE moderation_appeal SET status = #{decision}, reviewer_id = #{reviewerId}, decision_reason = #{decisionReason}, process_time = #{processTime}, update_time = NOW() WHERE id = #{appealId} AND status = 0")
    int processPending(@Param("appealId") Long appealId,
                       @Param("reviewerId") Long reviewerId,
                       @Param("decision") Integer decision,
                       @Param("decisionReason") String decisionReason,
                       @Param("processTime") LocalDateTime processTime);
}
