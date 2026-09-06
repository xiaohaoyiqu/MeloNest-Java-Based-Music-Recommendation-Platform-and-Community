   
                      
                        
   

package com.haoran.music.service;

import java.util.Map;

   
           
                     
   
public interface UserPointsService {

       
               
      
                         
                   
       
    Map<String, Object> getUserPoints(Long userId);

       
              
      
                         
                   
       
    Boolean initUserPoints(Long userId);

       
           
      
                         
                                                                                  
                         
                       
                             
                               
                    
       
    Integer addPoints(Long userId, String changeType, Integer points,
                     String reason, Long businessId, String businessType);

       
           
      
                         
                                           
                         
                       
                             
                               
                    
       
    Integer deductPoints(Long userId, String changeType, Integer points,
                        String reason, Long businessId, String businessType);

       
               
      
                         
                         
                   
       
    Boolean checkPointsEnough(Long userId, Integer points);

       
               
      
                         
                               
                     
                       
                     
       
    Map<String, Object> getPointsRecords(Long userId, String changeType,
                                        Integer page, Integer size);

       
                     
      
                      
       
    Integer resetDailyPoints();

       
              
      
                         
                                    
                       
                              
                   
       
    Boolean adjustPoints(Long userId, Integer points, String reason, Long operatorId);

       
             
      
                         
                    
       
    Integer checkinPoints(Long userId);

       
                
      
                         
                      
       
    Integer getTodayPoints(Long userId);

       
                
      
                         
                      
       
    Integer getMonthPoints(Long userId);

       
                   
      
                         
                     
       
    Boolean isReachDailyLimit(Long userId);

       
             
      
                   
       
    Map<String, Object> getPointsConfig();

       
              
      
                        
                  
       
    Map<String, Object> getPointsRanking(Integer limit);

       
                
      
                         
                         
                     
       
    Integer convertPointsToExp(Long userId, Integer points);
}
