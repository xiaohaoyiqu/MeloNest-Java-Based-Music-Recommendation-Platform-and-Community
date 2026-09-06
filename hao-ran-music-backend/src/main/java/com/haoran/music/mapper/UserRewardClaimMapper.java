   
                      
   
package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.UserRewardClaim;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface UserRewardClaimMapper extends BaseMapper<UserRewardClaim> {

    @Update("UPDATE user_reward_claim SET status = 'granted', granted_points = #{grantedPoints}, update_time = #{updateTime} WHERE id = #{id} AND status = 'pending'")
    int markGranted(@Param("id") Long id,
                    @Param("grantedPoints") Integer grantedPoints,
                    @Param("updateTime") LocalDateTime updateTime);
}
