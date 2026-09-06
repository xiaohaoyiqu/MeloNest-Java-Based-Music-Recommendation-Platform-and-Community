package com.haoran.music.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.haoran.music.entity.UserCheckin;

import java.time.LocalDate;
import java.util.Map;





public interface UserCheckinService extends IService<UserCheckin> {







    Map<String, Object> checkin(Long userId);







    boolean hasCheckedInToday(Long userId);







    Integer getContinuousDays(Long userId);







    Integer getMonthCheckinCount(Long userId);







    java.util.List<LocalDate> getMonthCheckinDates(Long userId);








    Map<String, Object> makeupCheckin(Long userId, LocalDate date);







    Map<String, Object> getCheckinStats(Long userId);
}
