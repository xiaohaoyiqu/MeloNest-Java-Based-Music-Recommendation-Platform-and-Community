package com.haoran.music.service;

import com.haoran.music.dto.song.SongRatingDTO;
import com.haoran.music.vo.song.SongRatingVO;

import java.util.Map;





public interface SongRatingService {








    SongRatingVO rateSong(Long userId, SongRatingDTO dto);







    void batchRateSong(Long userId, Map<Long, Integer> ratings);








    SongRatingVO getSongRating(Long songId, Long userId);







    void deleteRating(Long userId, Long songId);







    Object getUserRatings(Long userId);







    Map<String, Object> getUserRatingStats(Long userId);
}
