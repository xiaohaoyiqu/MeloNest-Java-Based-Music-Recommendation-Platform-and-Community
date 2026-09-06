package com.haoran.music.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.haoran.music.entity.ModerationAppeal;

import java.util.List;
import java.util.Map;

   
                      
                        
   
public interface ModerationAppealService extends IService<ModerationAppeal> {

       
           
                               
                         
                               
                                
                              
                   
       
    Long submitAppeal(Long moderationId, Long userId, String appealReason,
                     String appealContent, String attachments);

       
                      
      
                               
                         
                               
                                
                                         
                   
       
    Long submitAppealWithAssets(Long moderationId, Long userId, String appealReason,
                                String appealContent, List<Long> attachmentAssetIds);

       
                        
      
                           
                             
                   
       
    Map<String, Object> getAppealDetail(Long appealId, Long viewerId);

       
           
                           
                              
                                    
                                 
       
    void processAppeal(Long appealId, Long reviewerId, Integer decision, String decisionReason);

       
               
                         
                               
                     
       
    Boolean canAppeal(Long userId, Long moderationId);
}
