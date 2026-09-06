




package com.haoran.music.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.common.result.Result;
import com.haoran.music.entity.UserVisit;

import java.util.List;
import java.util.Map;





public interface UserVisitService {









    void recordVisit(Long visitorId, Long visitedUserId, String visitSource, String ipAddress);








    Result<IPage<UserVisit>> getVisitRecords(Long visitorId, PageQuery query);








    Result<IPage<UserVisit>> getVisitorRecords(Long visitedUserId, PageQuery query);







    Result<Map<String, Object>> getVisitStats(Long userId);






    void cleanOldVisits(Integer days);








    Result<List<Map<String, Object>>> getRecentVisitors(Long userId, Integer limit);
}
