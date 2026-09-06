package com.haoran.music.service;

import com.haoran.music.vo.analysis.FunnelAnalysisVO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;





public interface FunnelAnalysisService {









    FunnelAnalysisVO analyzeFunnel(String funnelType, LocalDateTime startTime, LocalDateTime endTime);










    FunnelAnalysisVO analyzeCustomFunnel(String funnelName, List<String> steps, LocalDateTime startTime, LocalDateTime endTime);






    List<Map<String, String>> getAvailableFunnelTypes();











    FunnelAnalysisVO.FunnelComparison compareFunnels(String funnelType, LocalDateTime startTime1, LocalDateTime endTime1,
                                                      LocalDateTime startTime2, LocalDateTime endTime2);










    Map<String, Object> getStepDetails(String funnelType, Integer stepNumber, LocalDateTime startTime, LocalDateTime endTime);










    List<Map<String, Object>> getDropOffUsers(String funnelType, Integer stepNumber, LocalDateTime startTime, LocalDateTime endTime);








    Map<String, Object> getFunnelTrend(String funnelType, Integer days);









    Map<String, Object> getHighConversionUserSegment(String funnelType, LocalDateTime startTime, LocalDateTime endTime);









    Long createCustomFunnel(String funnelName, String description, List<String> steps);







    Boolean deleteCustomFunnel(Long funnelId);









    Map<String, Object> exportFunnelReport(String funnelType, LocalDateTime startTime, LocalDateTime endTime);







    Map<String, Object> getRealTimeFunnelData(String funnelType);
}
