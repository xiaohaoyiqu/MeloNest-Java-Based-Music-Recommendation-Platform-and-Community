   
                      
                        
   

package com.haoran.music.service;

import com.haoran.music.common.vo.ModerationAssignmentVO;

import java.util.List;
import java.util.Map;

   
           
                          
   
public interface ModerationAssignmentService {

       
                   
      
                               
                   
       
    boolean assignModeration(Long moderationId);
       
                                                           
      
                                               
                                           
                                
       
    boolean assignModeration(Long moderationId, Long moderatorId);

       
                 
      
                                  
                                  
                                   
                                   
                       
       
    Long assignTask(String targetType, Long targetId, Long submitterId, String submitterSource);

       
                
                               
      
                      
       
    int autoAssignPendingModerations();

       
                   
      
                               
                   
       
    List<ModerationAssignmentVO> getModeratorAssignments(Long moderatorId);

       
                
      
                               
                      
       
    int getModeratorWorkload(Long moderatorId);

       
                      
      
                               
                                
                   
       
    boolean releaseModeration(Long moderationId, Long moderatorId);

       
             
                          
      
                               
                     
       
    boolean reassignModeration(Long moderationId);

       
                
      
                               
                   
       
    boolean isModeratorOnline(Long moderatorId);

       
                   
      
                        
       
    List<Long> getOnlineModerators();

       
                
      
                               
                              
       
    void updateModeratorOnlineStatus(Long moderatorId, boolean online);

       
                           
      
                                    
       
    Long getBestModerator();

       
                   
      
                               
                    
       
    boolean hasQuota(Long moderatorId);

       
                   
      
                               
       
    Map<Long, Integer> getAllModeratorLoads();

       
                  
      
                      
       
    List<Long> getOnlineModeratorIds();

       
                  
      
                      
       
    List<Long> getActiveModeratorIds();

       
           
      
                               
       
    void completeTask(Long moderatorId);
}
