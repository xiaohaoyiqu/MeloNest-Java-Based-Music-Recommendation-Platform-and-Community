package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.FavoriteCollectionItem;
import com.haoran.music.vo.favorite.FavoriteGroupItemVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FavoriteCollectionItemMapper extends BaseMapper<FavoriteCollectionItem> {
    @Select("SELECT group_id, resource_type, resource_id FROM favorite_collection_item "
            + "WHERE user_id = #{userId} ORDER BY update_time DESC, id DESC")
    List<FavoriteGroupItemVO> selectUserItems(@Param("userId") Long userId);

    @Insert("INSERT INTO favorite_collection_item "
            + "(user_id, group_id, resource_type, resource_id, create_time, update_time) "
            + "VALUES (#{userId}, #{groupId}, #{resourceType}, #{resourceId}, NOW(), NOW()) "
            + "ON DUPLICATE KEY UPDATE group_id = VALUES(group_id), update_time = NOW()")
    int upsert(@Param("userId") Long userId, @Param("groupId") Long groupId,
               @Param("resourceType") String resourceType, @Param("resourceId") Long resourceId);

    @Delete("DELETE FROM favorite_collection_item WHERE user_id = #{userId} "
            + "AND resource_type = #{resourceType} AND resource_id = #{resourceId}")
    int deleteResource(@Param("userId") Long userId, @Param("resourceType") String resourceType,
                       @Param("resourceId") Long resourceId);

    @Delete("DELETE FROM favorite_collection_item WHERE user_id = #{userId} AND group_id = #{groupId}")
    int deleteGroupItems(@Param("userId") Long userId, @Param("groupId") Long groupId);
}
