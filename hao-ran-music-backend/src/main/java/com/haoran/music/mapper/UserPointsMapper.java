




package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.common.constant.PublicStatsSql;
import com.haoran.music.entity.UserPoints;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;




@Mapper
public interface UserPointsMapper extends BaseMapper<UserPoints> {







    @Select("SELECT p.* FROM user_points p"
            + " INNER JOIN `user` u ON u.id = p.user_id"
            + " WHERE 1 = 1"
            + PublicStatsSql.USER_FILTER
            + " ORDER BY p.total_points DESC, p.id ASC LIMIT #{limit}")
    List<UserPoints> selectPublicRanking(@Param("limit") int limit);
}
