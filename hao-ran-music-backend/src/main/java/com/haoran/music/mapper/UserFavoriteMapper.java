package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.common.constant.PublicStatsSql;
import com.haoran.music.entity.UserFavorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

   
                      
                          
   
@Mapper
public interface UserFavoriteMapper extends BaseMapper<UserFavorite> {

                                           
    @Select("SELECT COUNT(*) FROM user_favorite uf "
            + PublicStatsSql.INNER_USER_JOIN + "uf.user_id "
            + "WHERE uf.target_type <> 'song'"
            + PublicStatsSql.USER_FILTER)
    Long countPublicActiveNonSongFavorites();
}
