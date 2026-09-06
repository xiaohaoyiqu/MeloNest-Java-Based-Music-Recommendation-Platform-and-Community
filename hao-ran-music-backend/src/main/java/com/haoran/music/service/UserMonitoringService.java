


package com.haoran.music.service;

import java.util.Map;




public interface UserMonitoringService {




    Map<String, Object> getUserMonitoringOverview(Long userId, Integer days);




    Map<String, Object> getSystemMonitoringOverview(Integer days, Integer limit);
}