package com.haoran.music.service;

import com.haoran.music.common.dto.ModerationCreateDTO;
import com.haoran.music.common.vo.ModerationRecordVO;
import com.haoran.music.common.vo.ModeratorVO;
import com.haoran.music.common.vo.WorkStatusVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.haoran.music.common.dto.PageQuery;

import java.util.List;

   
                      
                        
                     
   
public interface ModerationIntegrationService {

       
               
                           
      
                         
                     
       
    Long submitForModeration(ModerationCreateDTO dto);

       
                    
      
                                  
                                    
                                   
                                   
                     
       
    Long submitForModeration(String targetType, Long targetId, Long submitterId, String submitterSource);

       
                     
      
                                  
                                    
                                   
                                   
                                 
                     
       
    Long submitForModeration(String targetType, Long targetId, Long submitterId,
                             String submitterSource, Integer priority);

       
               
      
                             
                     
       
    ModerationRecordVO getRecordDetail(Long recordId);

       
                
      
                                      
                                       
                                  
                     
       
    IPage<ModerationRecordVO> getPendingAssignments(String targetType, String submitterSource, PageQuery pageQuery);

       
                 
      
                               
                                
                              
                     
       
    IPage<ModerationRecordVO> getModeratorTasks(Long moderatorId, String status, PageQuery pageQuery);

       
                
      
                    
       
    List<ModeratorVO> getOnlineModerators();

       
                
      
                    
       
    List<ModeratorVO> getActiveModerators();

       
             
      
                     
       
    WorkStatusVO getWorkStatus();

       
                
      
                               
                   
       
    ModeratorVO getModeratorStats(Long moderatorId);

       
           
      
                                 
                                
                               
       
    void approve(Long recordId, Long reviewerId, String reviewReason);

       
           
      
                                 
                                
                               
       
    void reject(Long recordId, Long reviewerId, String reviewReason);

       
                                                                                
       
    void completeTargetReview(String targetType, Long targetId, Long reviewerId,
                              String reviewResult, String reviewReason);

       
           
      
                               
                              
                             
       
    void skip(Long recordId, Long reviewerId, String skipReason);

       
               
      
                             
                               
                                     
       
    boolean isApproved(String targetType, Long targetId);

       
               
      
                             
                               
                                 
       
    boolean isRejected(String targetType, Long targetId);

       
             
      
                             
                               
                                                       
       
    String getModerationStatus(String targetType, Long targetId);
}
