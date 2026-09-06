   
                      
   
package com.haoran.music.service;

import java.io.File;

   
                                                                           
   
public interface SubmissionFileSecurityService {

       
                                                                      
      
                                  
                                         
                                                      
       
    void validateSingleAudioFile(Long userId, String fileUrl, boolean scan);

       
                                                        
      
                                  
                                                                
                                                      
       
    void validateAlbumFileUrls(Long userId, String fileUrls, boolean scan);

       
                                                                            
      
                                  
                                        
                                   
       
    File resolveSubmissionFile(Long userId, String fileUrl);
}