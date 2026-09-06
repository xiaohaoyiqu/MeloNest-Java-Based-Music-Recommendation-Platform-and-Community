   
                      
                      
   

package com.haoran.music.service;

import java.util.Map;
import java.util.List;

   
         
                    
   
public interface ReportService {

       
           
      
                              
                                
                              
                             
                         
                              
                                  
                   
       
    Map<String, Object> submitReport(Long reporterId, String targetType, Long targetId,
                                     String reportType, String reason, String description,
                                     String attachmentUrls);

       
                    
      
                              
                                
                              
                             
                         
                              
                                         
                   
       
    Map<String, Object> submitReportWithAssets(Long reporterId, String targetType, Long targetId,
                                               String reportType, String reason, String description,
                                               List<Long> attachmentAssetIds);

       
                   
      
                           
                              
                        
       
    Boolean withdrawReport(Long reportId, Long reporterId);

       
               
      
                              
                         
                     
                       
                     
       
    Map<String, Object> getMyReports(Long reporterId, String status,
                                     Integer page, Integer size);

       
                     
      
                     
                       
                    
       
    Map<String, Object> getPendingReports(Integer page, Integer size);

       
           
      
                           
                              
                             
                               
                         
                   
       
    Map<String, Object> reviewReport(Long reportId, Long reviewerId,
                                    Boolean approved, String reviewReason, String action);

       
             
      
                           
                   
       
    Map<String, Object> getReportDetail(Long reportId);

       
             
      
                           
                               
                   
       
    Map<String, Object> getReportDetail(Long reportId, Long viewerId);

       
                
      
                             
                           
                     
                       
                     
       
    Map<String, Object> getTargetReports(String targetType, Long targetId,
                                        Integer page, Integer size);

       
                
      
                             
                           
                     
                       
                               
                     
       
    Map<String, Object> getTargetReports(String targetType, Long targetId,
                                        Integer page, Integer size, Long viewerId);

       
             
      
                           
                   
       
    Boolean grantReportReward(Long reportId);

       
                
      
                         
                  
       
    Integer getReportCredit(Long userId);

       
                    
      
                         
                           
                        
                       
                    
       
    Integer addReportCredit(Long userId, Long reportId, Integer score, String reason);

       
                    
      
                         
                           
                        
                       
                    
       
    Integer deductReportCredit(Long userId, Long reportId, Integer score, String reason);

       
                    
      
                      
       
    Integer resetReportCredit();

       
             
      
                         
                   
       
    Map<String, Object> getReportStatistics(Long userId);

       
               
      
                         
                     
       
    Boolean checkReportFrequency(Long userId);

       
               
      
                   
       
    Integer getReportRewardPoints();
}
