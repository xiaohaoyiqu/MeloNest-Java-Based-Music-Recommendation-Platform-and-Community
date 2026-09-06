package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.common.constant.PublicStatsSql;
import com.haoran.music.entity.UserProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

   
                      
                            
   
@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfile> {

    @Select("SELECT COALESCE(p.user_segment, 'UNKNOWN') AS segment, " +
            "COUNT(*) AS total_count, " +
            "SUM(CASE WHEN u.id IS NOT NULL " + PublicStatsSql.USER_FILTER +
            " THEN 1 ELSE 0 END) AS public_count " +
            "FROM music_user_profile p " +
            "LEFT JOIN `user` u ON u.id = p.user_id " +
            "WHERE p.deleted = 0 " +
            "GROUP BY p.user_segment")
    List<Map<String, Object>> selectSegmentStats();
}
