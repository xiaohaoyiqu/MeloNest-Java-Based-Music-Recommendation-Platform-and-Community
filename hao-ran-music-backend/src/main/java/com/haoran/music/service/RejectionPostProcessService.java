package com.haoran.music.service;

   
                      
                           
   
public interface RejectionPostProcessService {

       
                   
                               
                               
       
    void processRejection(Long moderationId, String rejectReason);

       
                
                               
                       
       
    String calculateReapplyTime(String rejectReason);

       
               
                               
                     
       
    String categorizeRejection(String rejectReason);

       
             
                               
                   
       
    String generateSuggestions(String rejectReason);
}
