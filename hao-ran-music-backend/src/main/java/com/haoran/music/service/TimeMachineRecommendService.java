package com.haoran.music.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;







public interface TimeMachineRecommendService {








    Map<String, Object> getThatDayHistory(Long userId, LocalDate targetDate);









    List<Map<String, Object>> getThatDayRecommendation(Long userId, Integer limit);









    List<Map<String, Object>> getThatDayRecommendation(Long userId, LocalDate targetDate, Integer limit);








    List<Map<String, Object>> getMusicTimeline(Long userId, Integer months);








    Map<String, Object> getYearlyMemory(Long userId, Integer year);









    Map<String, Object> comparePeriods(Long userId, Integer period1, Integer period2);
}
