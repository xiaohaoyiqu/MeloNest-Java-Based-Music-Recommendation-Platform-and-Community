package com.haoran.music.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.haoran.music.entity.UserProfile;
import com.haoran.music.vo.user.UserProfileVO;

import java.util.List;
import java.util.Map;

   
                      
                        
   
public interface UserProfileService extends IService<UserProfile> {

       
               
      
                         
                     
       
    UserProfileVO getUserProfile(Long userId);

       
                 
      
                                 
                                       
                                     
                                     
       
    void updateUserPreferences(Long userId, List<String> preferredGenres,
                               List<String> preferredLanguages, List<String> preferredMoods);

       
                       
      
                         
       
    void refreshUserProfile(Long userId);

       
                 
       
    void batchRefreshUserProfiles();

       
               
      
                         
                      
       
    Map<String, Object> getUserPreferenceTags(Long userId);

       
                   
      
                         
                                                    
                                       
                                  
       
    void recordUserBehavior(Long userId, String action, Long targetId, String metadata);

       
               
      
                      
       
    Map<String, Object> getUserSegmentStats();

       
               
      
                         
                          
       
    Integer predictChurnProbability(Long userId);
}
