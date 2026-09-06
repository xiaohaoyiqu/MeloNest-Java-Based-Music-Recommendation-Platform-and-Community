package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.RankingSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;






@Mapper
public interface RankingSnapshotMapper extends BaseMapper<RankingSnapshot> {







    @Select("SELECT ranking_type FROM ranking_build_lock "
            + "WHERE ranking_type = #{rankingType} AND partition_key = #{partitionKey} FOR UPDATE")
    String lockBuild(@Param("rankingType") String rankingType,
                     @Param("partitionKey") String partitionKey);








    @Select("SELECT * FROM ranking_snapshot WHERE ranking_type = #{rankingType} "
            + "AND partition_key = #{partitionKey} AND status = 'ready' AND is_active = 1 "
            + "ORDER BY completed_at DESC LIMIT 1 FOR UPDATE")
    RankingSnapshot selectActiveForUpdate(@Param("rankingType") String rankingType,
                                          @Param("partitionKey") String partitionKey);








    @Select("SELECT * FROM ranking_snapshot WHERE ranking_type = #{rankingType} "
            + "AND partition_key = #{partitionKey} AND status = 'ready' AND is_active = 1 "
            + "ORDER BY completed_at DESC LIMIT 1")
    RankingSnapshot selectActive(@Param("rankingType") String rankingType,
                                 @Param("partitionKey") String partitionKey);








    @Update("UPDATE ranking_snapshot SET is_active = 0, status = 'retired' WHERE ranking_type = #{rankingType} AND partition_key = #{partitionKey} AND is_active = 1")
    int retireActive(@Param("rankingType") String rankingType,
                     @Param("partitionKey") String partitionKey);










    @Update("UPDATE ranking_snapshot SET status = 'ready', is_active = 1, source_fact_count = #{sourceFactCount}, item_count = #{itemCount}, content_checksum = #{contentChecksum}, completed_at = NOW() WHERE snapshot_id = #{snapshotId} AND status = 'building' AND is_active = 0")
    int publish(@Param("snapshotId") String snapshotId,
                @Param("sourceFactCount") long sourceFactCount,
                @Param("itemCount") int itemCount,
                @Param("contentChecksum") String contentChecksum);
}
