




package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.MarketplaceItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;




@Mapper
public interface MarketplaceItemMapper extends BaseMapper<MarketplaceItem> {

    @Select("SELECT * FROM marketplace_item WHERE id = #{itemId} AND is_deleted = 0 FOR UPDATE")
    MarketplaceItem selectByIdForUpdate(@Param("itemId") Long itemId);

    @Update("UPDATE marketplace_item SET view_count = COALESCE(view_count, 0) + 1 WHERE id = #{itemId} AND is_deleted = 0")
    int incrementViewCount(@Param("itemId") Long itemId);

    @Update("UPDATE marketplace_item SET favorite_count = COALESCE(favorite_count, 0) + 1 WHERE id = #{itemId} AND is_deleted = 0")
    int incrementFavoriteCount(@Param("itemId") Long itemId);

    @Update("UPDATE marketplace_item SET favorite_count = GREATEST(COALESCE(favorite_count, 0) - 1, 0) WHERE id = #{itemId} AND is_deleted = 0")
    int decrementFavoriteCount(@Param("itemId") Long itemId);

    @Update("UPDATE marketplace_item SET status = #{nextStatus}, sold_time = CASE WHEN #{nextStatus} = 'sold' THEN NOW() ELSE sold_time END WHERE id = #{itemId} AND seller_id = #{sellerId} AND status = #{currentStatus} AND is_deleted = 0")
    int transitionStatus(@Param("itemId") Long itemId,
                         @Param("sellerId") Long sellerId,
                         @Param("currentStatus") String currentStatus,
                         @Param("nextStatus") String nextStatus);
}
