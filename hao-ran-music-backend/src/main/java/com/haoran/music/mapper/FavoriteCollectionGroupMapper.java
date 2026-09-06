package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.FavoriteCollectionGroup;
import com.haoran.music.vo.favorite.FavoriteGroupVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface FavoriteCollectionGroupMapper extends BaseMapper<FavoriteCollectionGroup> {
    @Select("SELECT g.id, g.name, g.sort_order, COUNT(i.id) AS item_count, g.create_time, g.update_time "
            + "FROM favorite_collection_group g "
            + "LEFT JOIN favorite_collection_item i ON i.group_id = g.id AND i.user_id = g.user_id "
            + "WHERE g.user_id = #{userId} GROUP BY g.id, g.name, g.sort_order, g.create_time, g.update_time "
            + "ORDER BY g.sort_order ASC, g.id ASC")
    List<FavoriteGroupVO> selectGroups(@Param("userId") Long userId);

    @Select("SELECT * FROM favorite_collection_group WHERE id = #{groupId} AND user_id = #{userId} LIMIT 1")
    FavoriteCollectionGroup selectOwned(@Param("userId") Long userId, @Param("groupId") Long groupId);

    @Select("SELECT * FROM favorite_collection_group WHERE user_id = #{userId} ORDER BY sort_order ASC, id ASC FOR UPDATE")
    List<FavoriteCollectionGroup> selectOwnedForUpdate(@Param("userId") Long userId);

    @Select("SELECT COALESCE(MAX(sort_order), 0) FROM favorite_collection_group WHERE user_id = #{userId}")
    Integer selectMaxSortOrder(@Param("userId") Long userId);

    @Update("UPDATE favorite_collection_group SET name = #{name}, update_time = NOW() "
            + "WHERE id = #{groupId} AND user_id = #{userId}")
    int updateOwnedName(@Param("userId") Long userId, @Param("groupId") Long groupId,
                        @Param("name") String name);

    @Update("UPDATE favorite_collection_group SET sort_order = #{sortOrder}, update_time = NOW() "
            + "WHERE id = #{groupId} AND user_id = #{userId}")
    int updateOwnedSortOrder(@Param("userId") Long userId, @Param("groupId") Long groupId,
                             @Param("sortOrder") Integer sortOrder);

    @org.apache.ibatis.annotations.Delete("DELETE FROM favorite_collection_group WHERE id = #{groupId} AND user_id = #{userId}")
    int deleteOwned(@Param("userId") Long userId, @Param("groupId") Long groupId);
}
