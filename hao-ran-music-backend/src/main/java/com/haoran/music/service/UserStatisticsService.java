package com.haoran.music.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.haoran.music.entity.UserStatistics;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;





public interface UserStatisticsService extends IService<UserStatistics> {









    void recordPlay(Long userId, Long songId, Integer duration, Boolean complete);







    void recordLogin(Long userId, String ip);






    void recordSearch(Long userId);








    void incrementInteraction(Long userId, String type, Integer increment);








    UserStatistics getStatsByDate(Long userId, LocalDate statDate);









    List<UserStatistics> getStatsByDateRange(Long userId, LocalDate startDate, LocalDate endDate);









    Map<String, Object> getUserTotalStats(Long userId, LocalDate startDate, LocalDate endDate);







    List<UserStatistics> getAllStatsByDate(LocalDate statDate);








    void markAsAbnormal(Long userId, LocalDate statDate, String abnormalReason);








    Boolean isBotUser(Long userId, Integer days);








    Integer getActiveDaysCount(Long userId, Integer days);






    void dailyBatchCreateOrUpdate(LocalDate statDate);
}
