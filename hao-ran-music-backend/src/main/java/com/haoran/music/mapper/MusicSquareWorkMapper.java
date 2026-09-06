package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.MusicSquareWork;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;





@Mapper
public interface MusicSquareWorkMapper extends BaseMapper<MusicSquareWork> {





    @Update("UPDATE music_square_work SET view_count = COALESCE(view_count, 0) + 1, update_time = NOW() WHERE id = #{workId} AND deleted = 0 AND status = 1")
    int incrementViewCount(@Param("workId") Long workId);

    @Update("UPDATE music_square_work SET like_count = COALESCE(like_count, 0) + 1, update_time = NOW() WHERE id = #{workId} AND deleted = 0 AND status = 1")
    int incrementLikeCount(@Param("workId") Long workId);

    @Update("UPDATE music_square_work SET like_count = GREATEST(COALESCE(like_count, 0) - 1, 0), update_time = NOW() WHERE id = #{workId} AND deleted = 0")
    int decrementLikeCount(@Param("workId") Long workId);

    @Update("UPDATE music_square_work SET status = #{status}, reviewer_id = #{reviewerId}, review_time = NOW(), review_reason = #{reviewReason}, publish_time = CASE WHEN #{status} = 1 THEN NOW() ELSE publish_time END WHERE id = #{workId} AND status = 0 AND deleted = 0")
    int reviewPendingWork(@Param("workId") Long workId,
                          @Param("reviewerId") Long reviewerId,
                          @Param("status") Integer status,
                          @Param("reviewReason") String reviewReason);

    @Update("UPDATE music_square_work SET related_song_id = #{songId}, related_mv_id = #{mvId}, update_time = NOW() WHERE id = #{workId} AND deleted = 0")
    int updateReviewRelations(@Param("workId") Long workId,
                              @Param("songId") Long songId,
                              @Param("mvId") Long mvId);

    @Update("UPDATE music_square_work SET deleted = 1, update_time = NOW() WHERE id = #{workId} AND user_id = #{userId} AND deleted = 0 AND status <> 1")
    int deleteDraftByOwner(@Param("workId") Long workId, @Param("userId") Long userId);

    @Update("UPDATE music_square_work SET title = #{work.title}, name = #{work.name}, description = #{work.description}, cover_url = #{work.coverUrl}, artist_names = #{work.artistNames}, audio_url = #{work.audioUrl}, audio_quality = #{work.audioQuality}, audio_size = #{work.audioSize}, audio_duration = #{work.audioDuration}, audio_bitrate = #{work.audioBitrate}, audio_sample_rate = #{work.audioSampleRate}, audio_format = #{work.audioFormat}, video_url = #{work.videoUrl}, video_quality = #{work.videoQuality}, video_size = #{work.videoSize}, video_duration = #{work.videoDuration}, video_format = #{work.videoFormat}, lyric_content = #{work.lyricContent}, lyric_file_url = #{work.lyricFileUrl}, has_translation = #{work.hasTranslation}, update_time = NOW() WHERE id = #{work.id} AND user_id = #{work.userId} AND status = 0 AND deleted = 0")
    int updatePendingWork(@Param("work") MusicSquareWork work);
}
