package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.UserActivityPoints;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;





@Mapper
public interface UserActivityPointsMapper extends BaseMapper<UserActivityPoints> {

    String ACTIVITY_CHANGE_TYPES_SQL = "('sign','makeup','achievement','redeem','decoration','emoji','manual_adjustment','expire','invite','playlist','follow')";







    @Select("SELECT IFNULL(SUM(change_amount), 0) FROM user_points_record " +
            "WHERE user_id = #{userId} AND change_type IN " + ACTIVITY_CHANGE_TYPES_SQL)
    Integer getUserTotalPoints(@Param("userId") Long userId);








    @Select("SELECT IFNULL(SUM(change_amount), 0) FROM user_points_record " +
            "WHERE user_id = #{userId} AND change_amount > 0 " +
            "AND create_time >= #{startTime} AND create_time < #{endTime} " +
            "AND change_type IN " + ACTIVITY_CHANGE_TYPES_SQL)
    Integer getTodayPoints(@Param("userId") Long userId,
                           @Param("startTime") LocalDateTime startTime,
                           @Param("endTime") LocalDateTime endTime);
}
