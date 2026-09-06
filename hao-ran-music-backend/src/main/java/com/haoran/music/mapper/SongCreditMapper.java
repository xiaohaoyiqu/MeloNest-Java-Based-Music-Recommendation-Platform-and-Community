package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.SongCredit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;






@Mapper
public interface SongCreditMapper extends BaseMapper<SongCredit> {






    @Select("SELECT id FROM song WHERE id = #{songId} FOR UPDATE")
    Long lockSong(@Param("songId") Long songId);







    @Select("SELECT COALESCE(MAX(credit_version), 0) + 1 FROM song_credit WHERE song_id = #{songId}")
    int selectNextVersion(@Param("songId") Long songId);







    @Update("UPDATE song_credit SET valid_to = NOW(), update_time = NOW() WHERE song_id = #{songId} AND acceptance_status = 'accepted' AND valid_to IS NULL AND rights_scope = 'display_only' AND source_type IN ('legacy_song_artist', 'song_service', 'work_processing')")
    int retireManagedDisplayCredits(@Param("songId") Long songId);
}
