package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.MediaAsset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

   
               
  
                      
   
@Mapper
public interface MediaAssetMapper extends BaseMapper<MediaAsset> {

       
                     
      
                        
                           
       
    @Insert("INSERT IGNORE INTO media_asset (owner_id, upload_session_id, purpose, visibility, "
            + "original_name, content_type, media_type, source_type, source_id, asset_role, "
            + "public_url, storage_node, storage_path, file_hash, file_size, scan_status, status, "
            + "grace_until, create_time, updated_at) VALUES (#{asset.ownerId}, #{asset.uploadSessionId}, "
            + "#{asset.purpose}, #{asset.visibility}, #{asset.originalName}, #{asset.contentType}, "
            + "#{asset.mediaType}, #{asset.sourceType}, #{asset.sourceId}, #{asset.assetRole}, "
            + "#{asset.publicUrl}, #{asset.storageNode}, #{asset.storagePath}, #{asset.fileHash}, "
            + "#{asset.fileSize}, #{asset.scanStatus}, #{asset.status}, #{asset.graceUntil}, "
            + "#{asset.createTime}, #{asset.updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "asset.id")
    int insertPrivateAssetIfAbsent(@Param("asset") MediaAsset asset);

       
                      
      
                            
                              
                   
       
    @Select("SELECT * FROM media_asset WHERE upload_session_id = #{sessionId} "
            + "AND file_hash = #{fileHash} AND visibility = 'PRIVATE' "
            + "AND status = 'ACTIVE' ORDER BY id ASC LIMIT 1")
    MediaAsset selectPrivateBySessionHash(@Param("sessionId") Long sessionId,
                                          @Param("fileHash") String fileHash);

       
                 
      
                          
                   
       
    @Select("SELECT * FROM media_asset WHERE id = #{assetId} AND visibility = 'PRIVATE' FOR UPDATE")
    MediaAsset selectPrivateForUpdate(@Param("assetId") Long assetId);

       
                      
      
                          
                   
       
    @Update("UPDATE media_asset SET grace_until = NULL, updated_at = NOW() WHERE id = #{assetId} AND status = 'ACTIVE'")
    int clearGraceUntil(@Param("assetId") Long assetId);

       
                           
      
                            
                               
                   
       
    @Update("UPDATE media_asset a SET a.grace_until = #{graceUntil}, a.updated_at = NOW() WHERE a.upload_session_id = #{sessionId} AND a.visibility = 'PRIVATE' AND a.status = 'ACTIVE' AND NOT EXISTS (SELECT 1 FROM media_asset_reference r WHERE r.asset_id = a.id AND r.released_at IS NULL)")
    int expireUnboundSessionAssets(@Param("sessionId") Long sessionId,
                                   @Param("graceUntil") java.time.LocalDateTime graceUntil);

       
                 
      
                              
                              
                   
       
    @Select("SELECT * FROM media_asset WHERE storage_node = #{storageNode} "
            + "AND storage_path = #{storagePath} ORDER BY id DESC LIMIT 1")
    MediaAsset selectByStorage(@Param("storageNode") String storageNode,
                               @Param("storagePath") String storagePath);

    @Select("SELECT * FROM media_asset WHERE storage_node = #{storageNode} "
            + "AND storage_path = #{storagePath} ORDER BY id DESC LIMIT 1 FOR UPDATE")
    MediaAsset selectByStorageForUpdate(@Param("storageNode") String storageNode,
                                        @Param("storagePath") String storagePath);

       
                  
      
                             
                   
       
    @Select("SELECT * FROM media_asset WHERE public_url = #{publicUrl} ORDER BY id DESC LIMIT 1")
    MediaAsset selectByPublicUrl(@Param("publicUrl") String publicUrl);

    @Select("SELECT * FROM media_asset WHERE public_url = #{publicUrl} "
            + "ORDER BY id DESC LIMIT 1 FOR UPDATE")
    MediaAsset selectByPublicUrlForUpdate(@Param("publicUrl") String publicUrl);

       
                         
      
                        
                     
       
    @Select("SELECT a.* FROM media_asset a WHERE ("
            + "(a.status = 'ACTIVE' AND a.grace_until IS NOT NULL AND a.grace_until <= NOW()) "
            + "OR (a.status = 'RECLAIM_FAILED' AND a.updated_at <= DATE_SUB(NOW(), INTERVAL 1 HOUR)) "
            + "OR (a.status = 'RECLAIMING' AND a.updated_at <= DATE_SUB(NOW(), INTERVAL 30 MINUTE))) "
            + "AND NOT EXISTS (SELECT 1 FROM media_asset_reference r "
            + "WHERE r.asset_id = a.id AND r.released_at IS NULL) "
            + "ORDER BY a.id ASC LIMIT #{limit}")
    List<MediaAsset> selectReclaimCandidates(@Param("limit") Integer limit);

       
                            
      
                             
                           
                               
                   
       
    @Update("UPDATE media_asset a SET a.grace_until = #{graceUntil}, a.updated_at = NOW() WHERE a.status = 'ACTIVE' AND EXISTS (SELECT 1 FROM media_asset_reference r0 WHERE r0.asset_id = a.id AND r0.target_type = #{targetType} AND r0.target_id = #{targetId}) AND NOT EXISTS (SELECT 1 FROM media_asset_reference r1 WHERE r1.asset_id = a.id AND r1.released_at IS NULL)")
    int markOrphanGraceUntil(@Param("targetType") String targetType,
                             @Param("targetId") Long targetId,
                             @Param("graceUntil") java.time.LocalDateTime graceUntil);

       
              
      
                          
                   
       
    @Update("UPDATE media_asset SET status = 'RECLAIMING', updated_at = NOW() WHERE id = #{assetId} AND ((status = 'ACTIVE' AND grace_until IS NOT NULL AND grace_until <= NOW()) OR (status = 'RECLAIM_FAILED' AND updated_at <= DATE_SUB(NOW(), INTERVAL 1 HOUR)) OR (status = 'RECLAIMING' AND updated_at <= DATE_SUB(NOW(), INTERVAL 30 MINUTE))) AND NOT EXISTS (SELECT 1 FROM media_asset_reference r WHERE r.asset_id = media_asset.id AND r.released_at IS NULL)")
    int claimForReclaim(@Param("assetId") Long assetId);

    @Update("UPDATE media_asset SET status = 'RECLAIMED', last_error = NULL, reclaimed_at = NOW(), updated_at = NOW() WHERE id = #{assetId} AND status = 'RECLAIMING' AND NOT EXISTS (SELECT 1 FROM media_asset_reference r WHERE r.asset_id = media_asset.id AND r.released_at IS NULL)")
    int markReclaimedAfterClaim(@Param("assetId") Long assetId);

    @Update("UPDATE media_asset SET status = 'RECLAIM_FAILED', last_error = #{error}, updated_at = NOW() WHERE id = #{assetId} AND status = 'RECLAIMING' AND NOT EXISTS (SELECT 1 FROM media_asset_reference r WHERE r.asset_id = media_asset.id AND r.released_at IS NULL)")
    int markReclaimFailedAfterClaim(@Param("assetId") Long assetId,
                                    @Param("error") String error);

       
                               
      
                                
                                      
                       
       
    @Select("SELECT * FROM media_asset WHERE storage_node = 'node3' "
            + "AND status IN ('ACTIVE', 'RECLAIMING', 'RECLAIM_FAILED') "
            + "AND storage_path LIKE CONCAT(#{rootPrefix}, '%') "
            + "ORDER BY id ASC LIMIT #{limit}")
    List<MediaAsset> selectExpectedNode3Assets(@Param("rootPrefix") String rootPrefix,
                                                @Param("limit") Integer limit);

       
                           
      
                   
       
    @Select("SELECT status, COUNT(*) AS count, MIN(grace_until) AS oldestGraceUntil, "
            + "MIN(updated_at) AS oldestUpdatedAt FROM media_asset "
            + "WHERE status IN ('ACTIVE', 'RECLAIMING', 'RECLAIM_FAILED') "
            + "GROUP BY status ORDER BY status")
    List<Map<String, Object>> selectReclaimObservation();
}
