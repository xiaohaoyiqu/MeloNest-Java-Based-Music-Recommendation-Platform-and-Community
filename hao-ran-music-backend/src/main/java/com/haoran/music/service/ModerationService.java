package com.haoran.music.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.entity.Moderation;

   
                      
                      
   
public interface ModerationService extends IService<Moderation> {

       
             
      
                               
                               
                             
                             
                                
                                 
                   
       
    Long submitForModeration(String contentType, Long contentId, String title,
                             String description, Long submitterId, String submitterName);

       
               
      
                            
                            
                              
                   
       
    IPage<Moderation> getModerationList(PageQuery pageQuery, Integer status, String contentType);

       
           
      
                               
                                
                                
       
    void approve(Long moderationId, Long reviewerId, String reviewerName);

       
           
      
                               
                                
                                
                               
       
    void reject(Long moderationId, Long reviewerId, String reviewerName, String reason);

       
              
      
                 
       
    Long getPendingCount();
}
