package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.UserActivity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Map;





@Mapper
public interface UserActivityMapper extends BaseMapper<UserActivity> {

    @Select("SELECT COUNT(*) AS recordCount, COUNT(DISTINCT user_id) AS userCount, "
            + "MAX(create_time) AS latestCreateTime "
            + "FROM user_activity WHERE deleted = 0 AND create_time >= #{startTime}")
    Map<String, Object> selectRecentStatus(@Param("startTime") LocalDateTime startTime);
}
