package com.haoran.music.service;

import com.haoran.music.vo.user.ChurnPredictionVO;

import java.util.List;
import java.util.Map;





public interface ChurnPredictionService {







    ChurnPredictionVO predictUserChurn(Long userId);







    List<ChurnPredictionVO> batchPredictUserChurn(List<Long> userIds);








    List<ChurnPredictionVO> getHighRiskUsers(String riskLevel, Integer limit);






    Map<String, Object> getChurnStatistics();







    List<String> analyzeChurnReasons(Long userId);







    List<String> generateRecallStrategy(Long userId);







    Map<String, Object> getChurnTrend(Integer days);







    Boolean sendChurnAlert(Long userId);







    Integer batchSendChurnAlerts(String riskLevel);









    Boolean recordRecallAction(Long userId, String action, Double cost);







    Map<String, Object> getRecallEffectiveness(Integer days);
}
