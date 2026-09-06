package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.MediaAssetReference;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;






@Mapper
public interface MediaAssetReferenceMapper extends BaseMapper<MediaAssetReference> {










    @Insert("INSERT IGNORE INTO media_asset_reference "
            + "(asset_id, target_type, target_id, reference_role, create_time) "
            + "VALUES (#{assetId}, #{targetType}, #{targetId}, #{referenceRole}, NOW())")
    int retainActive(@Param("assetId") Long assetId,
                     @Param("targetType") String targetType,
                     @Param("targetId") Long targetId,
                     @Param("referenceRole") String referenceRole);







    @Select("SELECT * FROM media_asset_reference WHERE asset_id = #{assetId} "
            + "AND released_at IS NULL ORDER BY id ASC")
    List<MediaAssetReference> selectActiveByAsset(@Param("assetId") Long assetId);








    @Select("SELECT r.asset_id FROM media_asset_reference r INNER JOIN media_asset a ON a.id = r.asset_id "
            + "WHERE r.target_type = #{targetType} AND r.target_id = #{targetId} "
            + "AND r.released_at IS NULL AND a.visibility = 'PRIVATE' AND a.status = 'ACTIVE' "
            + "ORDER BY r.id ASC")
    List<Long> selectActiveAssetIdsByTarget(@Param("targetType") String targetType,
                                            @Param("targetId") Long targetId);










    @Select("SELECT * FROM media_asset_reference WHERE asset_id = #{assetId} "
            + "AND target_type = #{targetType} AND target_id = #{targetId} "
            + "AND reference_role = #{referenceRole} AND released_at IS NULL "
            + "ORDER BY id DESC LIMIT 1")
    MediaAssetReference selectActiveReference(@Param("assetId") Long assetId,
                                              @Param("targetType") String targetType,
                                              @Param("targetId") Long targetId,
                                              @Param("referenceRole") String referenceRole);








    @Update("UPDATE media_asset_reference SET released_at = NOW() WHERE target_type = #{targetType} AND target_id = #{targetId} AND released_at IS NULL")
    int releaseByTarget(@Param("targetType") String targetType, @Param("targetId") Long targetId);
}
