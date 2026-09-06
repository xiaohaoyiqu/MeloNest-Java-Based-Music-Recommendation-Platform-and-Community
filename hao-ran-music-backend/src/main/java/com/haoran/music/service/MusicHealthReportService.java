package com.haoran.music.service;

import java.util.Map;

   
           
                 
  
                      
   
public interface MusicHealthReportService {

       
                 
      
                         
                   
       
    Map<String, Object> generateReport(Long userId);

       
                 
      
                         
                   
       
    Map<String, Object> getListeningSummary(Long userId);

       
                 
                   
      
                         
                         
       
    Integer getExplorationScore(Long userId);

       
                 
      
                         
                   
       
    Map<String, Integer> getListeningTimeDistribution(Long userId);

       
                 
      
                         
                     
                   
       
    Map<String, Object> getYearlyReport(Long userId, Integer year);

       
               
                      
      
                         
                   
       
    Map<String, Object> getMusicFingerprint(Long userId);
}
