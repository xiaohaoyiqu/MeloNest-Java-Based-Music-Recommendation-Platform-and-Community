




package com.haoran.music.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.common.result.Result;
import com.haoran.music.entity.UserInteraction;

import java.util.List;
import java.util.Map;





public interface UserInteractionService {










    void recordInteraction(Long userId, Long targetUserId, String interactionType,
                           String targetType, Long targetId);









    Result<IPage<UserInteraction>> getUserInteractions(Long userId, String interactionType, PageQuery query);









    Result<IPage<UserInteraction>> getInteractionsWithUser(Long userId, Long targetUserId, PageQuery query);







    Result<Map<String, Object>> getInteractionStats(Long userId);








    Result<List<Map<String, Object>>> getMostInteractedUsers(Long userId, Integer limit);






    void cleanOldInteractions(Integer days);






    void batchRecord(List<UserInteraction> interactions);
}
