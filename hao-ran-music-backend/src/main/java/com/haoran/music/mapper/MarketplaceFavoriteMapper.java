




package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.MarketplaceFavorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;




@Mapper
public interface MarketplaceFavoriteMapper extends BaseMapper<MarketplaceFavorite> {

    @Insert("INSERT INTO marketplace_favorite (item_id, user_id, deleted, create_time, update_time) " +
            "VALUES (#{itemId}, #{userId}, 0, NOW(), NOW()) " +
            "ON DUPLICATE KEY UPDATE update_time = IF(deleted = 1, NOW(), update_time), deleted = 0")
    int activateFavorite(@Param("itemId") Long itemId, @Param("userId") Long userId);

    @Update("UPDATE marketplace_favorite SET deleted = 1, update_time = NOW() WHERE item_id = #{itemId} AND user_id = #{userId} AND deleted = 0")
    int deactivateFavorite(@Param("itemId") Long itemId, @Param("userId") Long userId);
}
