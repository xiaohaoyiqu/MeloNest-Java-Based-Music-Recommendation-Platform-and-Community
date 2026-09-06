




package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.RewardDailyLimit;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDate;




@Mapper
public interface RewardDailyLimitMapper extends BaseMapper<RewardDailyLimit> {

    @Insert("INSERT IGNORE INTO reward_daily_limit " +
            "(user_id, limit_date, reward_count, reward_amount, create_time, update_time) " +
            "VALUES (#{userId}, #{limitDate}, 0, 0.00, NOW(), NOW())")
    int ensureDailyLimit(@Param("userId") Long userId,
                         @Param("limitDate") LocalDate limitDate);

    @Update("UPDATE reward_daily_limit SET reward_count = reward_count + 1, reward_amount = reward_amount + #{amount}, update_time = NOW() WHERE user_id = #{userId} AND limit_date = #{limitDate} AND reward_count < #{maxCount} AND reward_amount + #{amount} <= #{maxAmount}")
    int reserveWithinLimits(@Param("userId") Long userId,
                            @Param("limitDate") LocalDate limitDate,
                            @Param("amount") BigDecimal amount,
                            @Param("maxCount") Integer maxCount,
                            @Param("maxAmount") BigDecimal maxAmount);

    @Update("UPDATE reward_daily_limit SET reward_count = GREATEST(reward_count - 1, 0), reward_amount = GREATEST(reward_amount - #{amount}, 0.00), update_time = NOW() WHERE user_id = #{userId} AND limit_date = #{limitDate} AND (reward_count > 0 OR reward_amount > 0)")
    int releaseReservation(@Param("userId") Long userId,
                           @Param("limitDate") LocalDate limitDate,
                           @Param("amount") BigDecimal amount);
}
