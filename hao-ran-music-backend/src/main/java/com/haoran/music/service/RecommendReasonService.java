package com.haoran.music.service;

import com.haoran.music.vo.recommend.RecommendReasonVO;

import java.util.List;
import java.util.Map;





public interface RecommendReasonService {









    RecommendReasonVO getRecommendReason(Long userId, Long itemId, String itemType);








    List<RecommendReasonVO> batchGetRecommendReasons(Long userId, Map<Long, String> items);










    RecommendReasonVO generatePersonalizedReason(Long userId, Long itemId, String itemType, String recommendSource);






    Map<String, Integer> getReasonTypeStatistics();









    Boolean updateReasonConfig(String reasonType, Boolean enabled, String template);







    Map<String, Object> getReasonABTestData(String testId);










    Boolean recordReasonFeedback(Long userId, Long itemId, String itemType, Boolean helpful);







    Map<String, Object> getReasonEffectiveness(Integer days);







    Map<String, Object> optimizeReasonDisplay(Long userId);
}
