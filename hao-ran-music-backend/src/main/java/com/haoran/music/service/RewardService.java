




package com.haoran.music.service;

import java.math.BigDecimal;
import java.util.Map;





public interface RewardService {













    Map<String, Object> rewardCreator(Long userId, Long creatorId, BigDecimal amount,
                                      String message, Long resourceId,
                                      String resourceType, Boolean isAnonymous);









    Map<String, Object> getMyRewardRecords(Long userId, Integer page, Integer size);









    Map<String, Object> getReceivedRewards(Long creatorId, Integer page, Integer size);










    Map<String, Object> getResourceRewards(Long resourceId, String resourceType,
                                          Integer page, Integer size);








    Map<String, Object> getRewardDetail(Long rewardId, Long viewerId);







    Map<String, Object> getRewardStatistics(Long creatorId);







    Boolean completeReward(Long rewardId);








    Boolean cancelReward(Long rewardId, Long userId);








    Boolean checkRewardPermission(Long userId, BigDecimal amount);







    Boolean checkCreatorReceiveStatus(Long creatorId);
}
