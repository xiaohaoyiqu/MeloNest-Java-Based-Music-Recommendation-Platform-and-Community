package com.haoran.music.service;

import com.haoran.music.vo.recommend.RecommendVO;
import com.haoran.music.dto.song.SongVO;

import java.util.List;
import java.util.Map;







public interface AudioFeatureRecommendService {









    RecommendVO recommendByScenario(Long userId, String scenarioCode, Integer limit);










    RecommendVO recommendByMood(Long userId, Double valence, Double energy, Integer limit);









    RecommendVO recommendByAudioFeatures(Long userId, Long songId, Integer limit);






    List<Map<String, Object>> getScenarios();







    Map<String, Object> getSongAudioFeatures(Long songId);







    List<Map<String, Object>> batchGetAudioFeatures(List<Long> songIds);








    Double calculateSimilarity(Long songId1, Long songId2);










    RecommendVO recommendByMoodAndPreference(Long userId, Double valence, Double energy, Integer limit);







    Map<String, Object> getUserMoodAnalysis(Long userId);










    RecommendVO recommendByBpmRange(Long userId, Integer minBpm, Integer maxBpm, Integer limit);
}
