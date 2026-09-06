package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.ArtistApplication;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

   
                      
                             
   
@Mapper
public interface ArtistApplicationMapper extends BaseMapper<ArtistApplication> {

    @Update("UPDATE artist_application SET status = #{status}, reviewer_id = #{reviewerId}, review_time = NOW(), review_reason = #{reviewReason}, update_time = NOW() WHERE id = #{applicationId} AND status = 0 AND deleted = 0")
    int reviewPending(@Param("applicationId") Long applicationId,
                      @Param("reviewerId") Long reviewerId,
                      @Param("status") Integer status,
                      @Param("reviewReason") String reviewReason);
}
