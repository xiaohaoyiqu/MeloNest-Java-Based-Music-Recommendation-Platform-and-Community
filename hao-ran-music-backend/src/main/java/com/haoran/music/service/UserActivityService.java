package com.haoran.music.service;

import com.haoran.music.common.dto.PageResult;
import com.haoran.music.vo.user.UserActivityVO;

   
                      
                             
   
public interface UserActivityService {

       
               
      
                         
                         
                       
                         
                   
       
    PageResult<UserActivityVO> getUserActivities(Long userId, String type, Integer page, Integer size);

       
               
      
                         
                   
       
    Object getUserActivityStats(Long userId);

       
             
      
                               
                               
                               
                               
                               
       
    void recordActivity(Long userId, String activityType, String targetType, Long targetId, String content);
}
