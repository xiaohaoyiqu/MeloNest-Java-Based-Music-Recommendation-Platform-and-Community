package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.RankingSnapshotItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

   
                  
  
                      
   
@Mapper
public interface RankingSnapshotItemMapper extends BaseMapper<RankingSnapshotItem> {
       
                       
      
                                
                              
                         
                               
       
    @Select("SELECT q.song_id AS item_id, CAST(COUNT(*) AS DECIMAL(20,6)) AS score, "
            + "COUNT(*) AS valid_fact_count, CAST(q.song_id AS CHAR) AS tie_breaker "
            + "FROM qualified_play_fact q JOIN song s ON s.id = q.song_id "
            + "WHERE q.fact_status = 'valid' AND q.occurred_at >= #{windowStart} "
            + "AND q.occurred_at < #{windowEnd} AND s.status = 1 AND s.deleted = 0 "
            + "GROUP BY q.song_id ORDER BY valid_fact_count DESC, q.song_id ASC LIMIT #{limit}")
    List<RankingSnapshotItem> selectHotSongCandidates(@Param("windowStart") LocalDateTime windowStart,
                                                      @Param("windowEnd") LocalDateTime windowEnd,
                                                      @Param("limit") int limit);

       
                              
      
                                
                              
                    
       
    @Select("SELECT COUNT(*) FROM qualified_play_fact q JOIN song s ON s.id = q.song_id "
            + "WHERE q.fact_status = 'valid' AND q.occurred_at >= #{windowStart} "
            + "AND q.occurred_at < #{windowEnd} AND s.status = 1 AND s.deleted = 0")
    long countHotSongFacts(@Param("windowStart") LocalDateTime windowStart,
                           @Param("windowEnd") LocalDateTime windowEnd);

       
                        
      
                                
                         
                      
  
    @Select("SELECT item_id FROM ranking_snapshot_item WHERE snapshot_id = #{snapshotId} "
            + "AND item_type = 'song' ORDER BY rank_position ASC LIMIT #{limit}")
    List<Long> selectRankedSongIds(@Param("snapshotId") String snapshotId,
                                   @Param("limit") int limit);
}
