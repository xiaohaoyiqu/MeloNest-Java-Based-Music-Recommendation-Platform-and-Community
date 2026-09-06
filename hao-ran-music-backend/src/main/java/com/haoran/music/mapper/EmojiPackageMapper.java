   
                      
                           
   

package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.EmojiPackage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

   
              
   
@Mapper
public interface EmojiPackageMapper extends BaseMapper<EmojiPackage> {

    @Select("SELECT * FROM music_emoji_package WHERE id = #{packageId} AND is_deleted = 0 FOR UPDATE")
    EmojiPackage selectActiveByIdForUpdate(@Param("packageId") Long packageId);

    @Select("SELECT COUNT(*) FROM music_emoji_package WHERE creator_id = #{creatorId} " +
            "AND type = 'custom' AND review_status IN ('draft', 'rejected') AND is_deleted = 0")
    long countEditableByCreator(@Param("creatorId") Long creatorId);

    @Select("SELECT COUNT(*) FROM music_emoji_package WHERE creator_id = #{creatorId} " +
            "AND type = 'custom' AND review_status = 'pending' AND is_deleted = 0")
    long countPendingByCreator(@Param("creatorId") Long creatorId);

    @Update("UPDATE music_emoji_package SET download_count = COALESCE(download_count, 0) + 1, update_time = NOW() WHERE id = #{packageId} AND status = 1 AND is_deleted = 0")
    int incrementDownloadCount(@Param("packageId") Long packageId);

    @Update("UPDATE music_emoji_package SET cover_emoji_id = #{coverEmojiId}, cover_url = #{coverUrl}, update_time = NOW() WHERE id = #{packageId} AND is_deleted = 0")
    int updateCover(@Param("packageId") Long packageId,
                    @Param("coverEmojiId") Long coverEmojiId,
                    @Param("coverUrl") String coverUrl);
}
