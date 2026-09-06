   
                      
                        
   

package com.haoran.music.service;

import java.util.Map;

   
           
                  
   
public interface FeedbackRewardService {

       
             
      
                             
                                                          
                                    
                   
       
    Long createReward(Long feedbackId, String rewardLevel, String rewardDescription);

       
           
      
                           
                             
                   
       
    Boolean grantReward(Long rewardId, Long grantorId);

       
           
      
                           
                               
                   
       
    Boolean cancelReward(Long rewardId, String cancelReason);

       
               
      
                         
                     
                       
                   
       
    Map<String, Object> getMyRewards(Long userId, Integer page, Integer size);

       
                     
      
                     
                       
                    
       
    Map<String, Object> getPendingRewards(Integer page, Integer size);

       
               
      
                   
       
    Map<String, Object> getRewardLevels();

       
             
      
                           
                   
       
    Map<String, Object> getRewardDetail(Long rewardId);

       
             
      
                              
                             
                     
       
    Integer batchGrantRewards(Long[] rewardIds, Long grantorId);

       
             
      
                         
                   
       
    Map<String, Object> getRewardStatistics(Long userId);
}
