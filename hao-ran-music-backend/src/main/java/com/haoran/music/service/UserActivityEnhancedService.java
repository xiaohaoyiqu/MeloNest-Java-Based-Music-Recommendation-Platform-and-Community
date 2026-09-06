package com.haoran.music.service;

import java.util.List;
import java.util.Map;









public interface UserActivityEnhancedService {















    Integer getEnhancedActivityScore(Long userId);








    Map<String, Object> getActivityDimensionDetail(Long userId);










    Integer getCheckinActivityScore(Long userId);








    Integer getSocialActivityScore(Long userId);








    Integer getConsumptionActivityScore(Long userId);








    Integer getCreationActivityScore(Long userId);








    Integer getContentActivityScore(Long userId);











    Map<String, Object> checkBehaviorAbnormal(Long userId, String behaviorType);












    Double getCreditWeight(Long userId);










    Double calculateBehaviorScore(Long userId, String behaviorType, Double baseScore);









    Map<String, Object> checkRateLimit(Long userId, String behaviorType);










    Map<String, Object> getUserBehaviorAnalysis(Long userId);








    Boolean markSuspiciousUser(Long userId, String reason);







    Boolean unmarkSuspiciousUser(Long userId);










    List<Map<String, Object>> getActivityRanking(String dimension, Integer limit);








    Integer getUserRanking(Long userId, String dimension);










    Map<String, Object> getActivityRewards(Long userId);







    Map<String, Object> getActivityLevelProgress(Long userId);
}
