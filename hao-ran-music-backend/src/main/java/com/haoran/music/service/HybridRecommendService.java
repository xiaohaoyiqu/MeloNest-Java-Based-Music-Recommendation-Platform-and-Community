package com.haoran.music.service;

import com.haoran.music.vo.recommend.RecommendVO;
import com.haoran.music.vo.recommend.RecommendedSongVO;

import java.util.List;
import java.util.Map;










public interface HybridRecommendService {








    RecommendVO getHybridRecommend(Long userId, Integer limit);








    List<RecommendedSongVO> getHybridRecommendWithReason(Long userId, Integer limit);









    RecommendVO getColdStartRecommend(Long userId, Integer limit);









    RecommendVO getDiscoveryRecommend(Long userId, Integer limit);










    RecommendVO getMoodBasedRecommend(Long userId, String mood, Integer limit);







    void refreshUserRecommendProfile(Long userId);






    Map<String, Double> getRecommendWeights();






    void updateRecommendWeights(Map<String, Double> weights);




    Map<String, Object> getModelStatus();







    Map<String, Object> reloadModels(Long operatorId);
}
