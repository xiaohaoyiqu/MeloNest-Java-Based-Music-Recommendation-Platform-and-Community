   
                      
   
package com.haoran.music.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.entity.ModerationRecord;

import java.util.Map;

   
            
   
public interface ModerationRecordService extends IService<ModerationRecord> {

       
              
       
    Long createRecord(String targetType, Long targetId, Long submitterId, String submitterSource, Integer priority);

       
                      
       
    Long assignModerator(Long recordId);

       
                    
       
    Long assignModerator(Long recordId, Long moderatorId);

       
            
       
    void completeReview(Long recordId, Long reviewerId, String reviewResult, String reviewReason);

       
                                                                         
       
    void completeTargetReview(String targetType, Long targetId, Long reviewerId,
                              String reviewResult, String reviewReason);

       
            
       
    void startReview(Long recordId, Long reviewerId);

       
                            
       
    IPage<ModerationRecord> getPendingAssignments(PageQuery pageQuery, String targetType, String submitterSource);

       
                   
      
                                            
       
    IPage<ModerationRecord> getPendingAssignments(PageQuery pageQuery, String targetType,
                                                   String submitterSource, boolean includeAdminOnly);

       
                 
       
    IPage<ModerationRecord> getModeratorTasks(Long moderatorId, String status, PageQuery pageQuery);

       
                 
       
    IPage<ModerationRecord> getModeratorTasks(Long moderatorId, String status, String targetType, PageQuery pageQuery);

       
                 
       
    Map<String, Object> getModeratorStats(Long moderatorId);

       
                         
       
    Map<String, Object> getGlobalStats();

       
                
      
                                            
       
    Map<String, Object> getGlobalStats(boolean includeAdminOnly);

       
                 
       
    void skipReview(Long recordId, Long reviewerId, String skipReason);
}
