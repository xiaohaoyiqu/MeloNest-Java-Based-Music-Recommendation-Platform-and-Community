package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.haoran.music.entity.MV;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

   
                      
                           
   
@Mapper
public interface MVMapper extends BaseMapper<MV> {

       
                                                               
       
    @Select("SELECT id FROM mv WHERE status = 1 AND deleted = 0 "
            + "AND (song_id = #{songId} OR (#{preferredMvId} IS NOT NULL AND id = #{preferredMvId} "
            + "AND (song_id IS NULL OR song_id = #{songId}))) "
            + "ORDER BY CASE WHEN id = #{preferredMvId} THEN 0 ELSE 1 END, priority DESC, publish_date DESC, id ASC LIMIT 1")
    Long selectPublicRelatedMvId(@Param("songId") Long songId,
                                 @Param("preferredMvId") Long preferredMvId);

       
                       
      
                       
                                      
                                                     
                           
                                         
                     
       
    IPage<MV> selectPageWithFilters(
            Page<MV> page,
            @Param("area") String area,
            @Param("genre") String genre,
            @Param("keyword") String keyword,
            @Param("sortBy") String sortBy,
            @Param("language") String language,
            @Param("publishYear") Integer publishYear,
            @Param("publishDateStart") LocalDate publishDateStart,
            @Param("publishDateEnd") LocalDate publishDateEnd,
            @Param("minDuration") Integer minDuration,
            @Param("maxDuration") Integer maxDuration,
            @Param("quality") String quality,
            @Param("binding") String binding,
            @Param("albumType") String albumType
    );

    @Select("SELECT COUNT(*) FROM mv WHERE status = 1 AND deleted = 0")
    Long countPublicMvs();

    @Select("SELECT COUNT(*) FROM mv WHERE status = 1 AND deleted = 0 "
            + "AND (NULLIF(TRIM(url_360p), '') IS NOT NULL "
            + "OR NULLIF(TRIM(url_720p), '') IS NOT NULL "
            + "OR NULLIF(TRIM(url_1080p), '') IS NOT NULL "
            + "OR NULLIF(TRIM(url_2160p), '') IS NOT NULL)")
    Long countPublicPlayableMvs();

    @Select("SELECT COUNT(*) FROM mv WHERE status = 1 AND deleted = 0 "
            + "AND NULLIF(TRIM(url_360p), '') IS NULL "
            + "AND NULLIF(TRIM(url_720p), '') IS NULL "
            + "AND NULLIF(TRIM(url_1080p), '') IS NULL "
            + "AND NULLIF(TRIM(url_2160p), '') IS NULL")
    Long countPublicMvsMissingPlayableUrl();

    @Select("SELECT id, name, artist_names AS artistNames, song_id AS songId, "
            + "play_count AS playCount, create_time AS createTime "
            + "FROM mv WHERE status = 1 AND deleted = 0 "
            + "AND NULLIF(TRIM(url_360p), '') IS NULL "
            + "AND NULLIF(TRIM(url_720p), '') IS NULL "
            + "AND NULLIF(TRIM(url_1080p), '') IS NULL "
            + "AND NULLIF(TRIM(url_2160p), '') IS NULL "
            + "ORDER BY play_count DESC, update_time DESC LIMIT #{limit}")
    List<Map<String, Object>> selectPublicMvsMissingPlayableUrl(@Param("limit") Integer limit);

    @Update("UPDATE mv SET comment_count = GREATEST(COALESCE(comment_count, 0) + #{delta}, 0), update_time = NOW() WHERE id = #{mvId} AND deleted = 0")
    int adjustCommentCount(@Param("mvId") Long mvId, @Param("delta") int delta);
}
