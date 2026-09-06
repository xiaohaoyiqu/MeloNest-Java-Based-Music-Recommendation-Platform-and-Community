package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDateTime;





@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT id FROM `user` WHERE deleted = 0 AND id > #{lastUserId} ORDER BY id ASC LIMIT #{limit}")
    List<Long> selectActiveUserIdsAfter(@Param("lastUserId") Long lastUserId, @Param("limit") int limit);

    @Select("SELECT id FROM `user` WHERE deleted = 0 AND last_login_time IS NOT NULL " +
            "AND last_login_time >= #{threshold} AND id > #{lastUserId} ORDER BY id ASC LIMIT #{limit}")
    List<Long> selectRecentlyActiveUserIdsAfter(@Param("threshold") LocalDateTime threshold,
                                                @Param("lastUserId") Long lastUserId,
                                                @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM `user` WHERE deleted = 0")
    int countActiveUsers();




    @Select("SELECT * FROM `user` WHERE id = #{userId} FOR UPDATE")
    User selectByIdForUpdate(@Param("userId") Long userId);





    @Select("SELECT id, status, deleted, is_banned AS isBanned, user_type AS userType "
            + "FROM `user` WHERE id = #{userId} LIMIT 1")
    User selectAccountAccessStateById(@Param("userId") Long userId);





    @Update("UPDATE `user` SET deleted = 1, update_time = NOW() WHERE id = #{userId} AND deleted = 0")
    int softDeleteActiveById(@Param("userId") Long userId);

    @Update("UPDATE `user` SET following_count = COALESCE(following_count, 0) + 1 WHERE id = #{userId} AND deleted = 0")
    int incrementFollowingCount(@Param("userId") Long userId);

    @Update("UPDATE `user` SET fans_count = COALESCE(fans_count, 0) + 1 WHERE id = #{userId} AND deleted = 0")
    int incrementFansCount(@Param("userId") Long userId);

    @Update("UPDATE `user` SET following_count = GREATEST(COALESCE(following_count, 0) - 1, 0) WHERE id = #{userId} AND deleted = 0")
    int decrementFollowingCount(@Param("userId") Long userId);

    @Update("UPDATE `user` SET fans_count = GREATEST(COALESCE(fans_count, 0) - 1, 0) WHERE id = #{userId} AND deleted = 0")
    int decrementFansCount(@Param("userId") Long userId);

    @Update("UPDATE `user` SET total_earnings = COALESCE(total_earnings, 0) + #{amount}, pending_earnings = COALESCE(pending_earnings, 0) + #{amount}, update_time = NOW() WHERE id = #{userId} AND deleted = 0")
    int incrementRewardEarnings(@Param("userId") Long userId,
                                @Param("amount") BigDecimal amount);
}
