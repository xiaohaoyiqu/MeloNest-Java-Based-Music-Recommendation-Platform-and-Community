   
                      
                            
   

package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.RewardRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

   
               
   
@Mapper
public interface RewardRecordMapper extends BaseMapper<RewardRecord> {

    @Update("UPDATE reward_record SET status = 'paid', update_time = NOW() WHERE id = #{rewardId} AND status = 'pending'")
    int markPaidIfPending(@Param("rewardId") Long rewardId);

    @Update("UPDATE reward_record SET status = 'cancelled', update_time = NOW() WHERE id = #{rewardId} AND user_id = #{userId} AND status = 'pending'")
    int cancelPending(@Param("rewardId") Long rewardId,
                      @Param("userId") Long userId);
}
