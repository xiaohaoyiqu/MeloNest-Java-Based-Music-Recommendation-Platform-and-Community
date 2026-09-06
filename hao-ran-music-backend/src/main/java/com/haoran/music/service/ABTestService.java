package com.haoran.music.service;

import com.haoran.music.vo.experiment.ABTestVO;

import java.util.List;
import java.util.Map;





public interface ABTestService {










    Long createExperiment(String experimentName, String description, String experimentType, Map<String, Integer> trafficAllocation);







    ABTestVO getExperiment(Long experimentId);







    List<ABTestVO> getAllExperiments(String status);







    Boolean startExperiment(Long experimentId);







    Boolean stopExperiment(Long experimentId);







    Boolean deleteExperiment(Long experimentId);








    String assignUserToVariant(Long userId, Long experimentId);








    Map<Long, String> batchAssignUsersToVariant(List<Long> userIds, Long experimentId);










    Boolean trackExperimentEvent(Long userId, Long experimentId, String eventType, Map<String, Object> eventData);







    ABTestVO analyzeExperiment(Long experimentId);







    ABTestVO.StatisticalSignificance calculateSignificance(Long experimentId);







    Map<String, Object> generateExperimentReport(Long experimentId);









    List<ABTestVO> getExperimentList(Integer page, Integer size, String status);








    Boolean updateExperimentConfig(Long experimentId, Map<String, Object> config);
}
