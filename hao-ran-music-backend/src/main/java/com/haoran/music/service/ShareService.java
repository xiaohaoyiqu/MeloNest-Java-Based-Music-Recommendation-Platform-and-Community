   
                      
                      
   

package com.haoran.music.service;

import java.util.List;
import java.util.Map;

   
         
   
public interface ShareService {

       
             
      
                                                          
                             
                            
                                  
       
    Map<String, Object> generateShareLink(String type, Long resourceId, Long userId);

       
                  
      
                           
                   
       
    Map<String, Object> getResourceByShareCode(String shareCode);

       
             
      
                            
                             
                            
                            
       
    void recordShare(String type, Long resourceId, String platform, Long userId);

       
             
      
                            
                             
                               
       
    Map<String, Object> getShareStats(String type, Long resourceId);

       
               
      
                                             
                         
                     
       
    Map<String, Object> batchGenerateShareLinks(Map<String, Object> items, Long userId);

       
               
      
                           
                                
       
    void incrementShareView(String shareCode, Long userId);

       
                   
      
                           
                            
                   
       
    boolean cancelShare(String shareCode, Long userId);

       
                
      
                           
                   
       
    boolean validateShareCode(String shareCode);
}
