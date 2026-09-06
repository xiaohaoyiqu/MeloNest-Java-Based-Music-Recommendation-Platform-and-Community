package com.haoran.music.service;

import java.util.Map;







public interface ActivityRewardService {







    Map<String, Object> getCurrentReward(Long userId);








    Boolean claimReward(Long userId);







    Boolean isClaimedThisMonth(Long userId);









    Map<String, Object> getClaimHistory(Long userId, Integer page, Integer size);







    Integer calculateRewardPoints(Integer activityScore);







    Map<String, Integer> getRewardConfig();
}
