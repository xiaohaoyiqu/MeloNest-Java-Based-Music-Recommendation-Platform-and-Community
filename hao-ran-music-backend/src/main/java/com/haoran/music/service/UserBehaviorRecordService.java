




package com.haoran.music.service;

import com.haoran.music.common.result.Result;
import com.haoran.music.entity.UserBehaviorRecord;

import java.util.List;
import java.util.Map;





public interface UserBehaviorRecordService {













    void recordBehavior(Long userId, String behaviorType, String targetType, Long targetId,
                        Integer duration, String deviceType, String clientType, String ipAddress);






    void batchRecord(List<UserBehaviorRecord> records);











    Result<List<UserBehaviorRecord>> getUserBehaviors(Long userId, String behaviorType,
                                                       String startTime, String endTime, Integer limit);









    Result<Map<String, Object>> getUserBehaviorStats(Long userId, String startDate, String endDate);








    Result<Map<String, Object>> getTargetBehaviorStats(String targetType, Long targetId);









    Result<List<Map<String, Object>>> getHotResources(String targetType, String behaviorType, Integer limit);






    void cleanOldRecords(Integer days);










    void recordBehaviorAsync(Long userId, String behaviorType, String targetType, Long targetId, Integer duration);
}
