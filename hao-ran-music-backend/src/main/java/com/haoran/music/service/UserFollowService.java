




package com.haoran.music.service;

import java.util.List;
import java.util.Map;




public interface UserFollowService {







    List<Long> getFollowingIds(Long userId);







    List<Long> getFollowerIds(Long userId);








    boolean isFollowing(Long followerId, Long followeeId);








    boolean follow(Long followerId, Long followeeId);








    boolean unfollow(Long followerId, Long followeeId);







    long getFollowingCount(Long userId);







    long getFollowerCount(Long userId);








    boolean removeFollower(Long followerId, Long userId);








    List<Object> getRecommendUsers(Long userId, Integer limit);









    List<Map<String, Object>> getPersonalizedRecommendUsers(Long userId, Integer limit);
}
