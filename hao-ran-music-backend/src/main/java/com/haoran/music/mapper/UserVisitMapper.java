




package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.UserVisit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;




@Mapper
public interface UserVisitMapper extends BaseMapper<UserVisit> {

    @Select("SELECT " +
            "(SELECT COUNT(*) FROM user_visit WHERE visitor_id = #{userId}) AS totalVisitCount, " +
            "(SELECT COUNT(*) FROM user_visit WHERE visited_user_id = #{userId}) AS totalVisitorCount, " +
            "(SELECT COUNT(DISTINCT visited_user_id) FROM user_visit WHERE visitor_id = #{userId}) AS uniqueVisitedCount, " +
            "(SELECT COUNT(DISTINCT visitor_id) FROM user_visit WHERE visited_user_id = #{userId}) AS uniqueVisitorCount")
    Map<String, Object> selectVisitStats(@Param("userId") Long userId);
}
