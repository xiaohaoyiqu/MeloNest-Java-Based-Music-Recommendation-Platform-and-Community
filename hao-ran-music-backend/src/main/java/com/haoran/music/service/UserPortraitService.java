package com.haoran.music.service;

import java.util.Map;

   
           
  
        
                  
              
              
                  
  
                      
   
public interface UserPortraitService {

       
               
      
                         
                     
       
    Map<String, Object> getUserPortrait(Long userId);

       
               
      
                         
                              
       
    Map<String, Object> getUserMusicPreference(Long userId);

       
               
      
                         
                     
       
    java.util.List<String> getUserInterestTags(Long userId);

       
               
      
                         
                               
       
    Map<String, Integer> getUserActivePeriods(Long userId);

       
                      
      
                         
                                      
                                             
                          
       
    Integer calculatePreferenceScore(Long userId, String contentType, String contentValue);

       
             
      
                         
                     
       
    Boolean refreshUserPortrait(Long userId);

       
               
      
                            
                      
       
    Integer batchRefreshPortrait(java.util.List<Long> userIds);

       
               
      
                         
                                    
       
    Map<String, Long> getUserBehaviorSummary(Long userId);

       
                  
      
                         
                        
                        
       
    java.util.List<Long> predictUserPreferences(Long userId, Integer limit);

       
               
      
                         
                          
                       
       
    java.util.List<Long> getSimilarUsers(Long userId, Integer limit);

       
                 
      
                         
                         
                           
       
    Integer calculateUserSimilarity(Long userId1, Long userId2);
}
