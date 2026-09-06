package com.haoran.music.service;

import java.util.List;
import java.util.Map;

   
                      
                             
  
        
                           
                               
   
public interface UserActivityQuickService {

       
                       
               
                    
                   
                    
                            
      
                         
                          
       
    Integer getActivityScore(Long userId);

       
                  
                           
      
                         
                   
       
    Boolean isActiveUser(Long userId);

       
               
                               
                             
                             
                                     
      
                         
                   
       
    String getActivityLevel(Long userId);

       
                
                             
      
                         
                    
       
    Map<String, Object> getActivityDetail(Long userId);

       
                  
      
                            
                            
       
    Map<Long, Integer> batchGetActivityScore(List<Long> userIds);

       
               
      
                         
                   
       
    Map<String, Object> getCheckinStatistics(Long userId);

       
                 
                         
      
                         
                     
       
    Map<String, Object> getSocialStatistics(Long userId);

       
                
                                
      
                         
                     
       
    Boolean refreshActivityCache(Long userId);
}
