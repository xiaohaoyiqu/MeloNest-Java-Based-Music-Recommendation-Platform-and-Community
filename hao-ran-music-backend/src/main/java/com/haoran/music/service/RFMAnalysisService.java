package com.haoran.music.service;

import com.haoran.music.vo.user.RFMVO;

import java.util.List;
import java.util.Map;





public interface RFMAnalysisService {







    RFMVO calculateUserRFM(Long userId);







    List<RFMVO> batchCalculateUserRFM(List<Long> userIds);






    Map<String, Integer> getSegmentStatistics();









    List<RFMVO> getUsersBySegment(String segmentType, Integer page, Integer size);






    void refreshUserRFMCache(Long userId);






    Map<String, Object> getSegmentDistribution();







    Integer predictChurnProbability(Long userId);







    String getSegmentAdvice(String segmentType);






    Map<String, Object> exportRFMReport();
}
