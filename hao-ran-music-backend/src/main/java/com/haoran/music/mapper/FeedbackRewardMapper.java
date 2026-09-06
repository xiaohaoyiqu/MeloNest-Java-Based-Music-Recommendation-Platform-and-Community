   
                      
                            
   

package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.FeedbackReward;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Map;

   
               
   
@Mapper
public interface FeedbackRewardMapper extends BaseMapper<FeedbackReward> {

    @Update("UPDATE feedback_reward SET status = #{targetStatus}, update_time = NOW() WHERE id = #{rewardId} AND status = #{expectedStatus}")
    int transitionStatus(@Param("rewardId") Long rewardId,
                         @Param("expectedStatus") String expectedStatus,
                         @Param("targetStatus") String targetStatus);

    @Update("UPDATE feedback_reward SET status = 'granted', is_granted = 1, grantor_id = #{grantorId}, granted_time = #{grantedTime}, update_time = NOW() WHERE id = #{rewardId} AND status = 'processing'")
    int completeGrant(@Param("rewardId") Long rewardId,
                      @Param("grantorId") Long grantorId,
                      @Param("grantedTime") java.time.LocalDateTime grantedTime);

    @Update("UPDATE feedback_reward SET status = 'cancelled', is_granted = 0, cancel_reason = #{cancelReason}, cancelled_time = #{cancelledTime}, update_time = NOW() WHERE id = #{rewardId} AND status = 'pending'")
    int cancelPending(@Param("rewardId") Long rewardId,
                      @Param("cancelReason") String cancelReason,
                      @Param("cancelledTime") java.time.LocalDateTime cancelledTime);

    @Select("SELECT " +
            "COUNT(*) AS totalRewards, " +
            "SUM(CASE WHEN status = 'pending' THEN 1 ELSE 0 END) AS pendingRewards, " +
            "SUM(CASE WHEN status = 'granted' THEN 1 ELSE 0 END) AS grantedRewards, " +
            "COALESCE(SUM(CASE WHEN status = 'granted' THEN COALESCE(points, 0) ELSE 0 END), 0) AS totalPoints, " +
            "COALESCE(SUM(CASE WHEN status = 'granted' THEN COALESCE(vip_days, 0) ELSE 0 END), 0) AS totalVipDays " +
            "FROM feedback_reward " +
            "WHERE user_id = #{userId}")
    Map<String, Object> selectRewardStatistics(@Param("userId") Long userId);
}
