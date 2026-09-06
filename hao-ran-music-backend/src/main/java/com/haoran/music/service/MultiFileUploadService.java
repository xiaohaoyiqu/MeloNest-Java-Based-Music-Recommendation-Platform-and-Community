   
                      
                               
  
        
                           
                                  
                               
                            
   

package com.haoran.music.service;

import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

   
              
  
public interface MultiFileUploadService {

       
             
      
                        
                         
                               
       
    Map<String, Object> uploadMultipleFiles(List<MultipartFile> files, Long userId);

       
               
      
                                         
                         
                            
       
    Map<String, Object> uploadAndExtractZip(MultipartFile zipFile, Long userId);

       
             
      
                          
                                                 
       
    String identifyFileType(String fileName);

       
               
      
                                 
                          
       
    List<Map<String, Object>> batchDetectAudioQuality(List<String> audioUrls, Long userId);

       
               
      
                                 
                          
       
    List<Map<String, Object>> batchDetectVideoInfo(List<String> videoUrls, Long userId);

       
              
      
                    
               
               
               
               
      
                                     
                       
       
    Map<String, List<Map<String, Object>>> categorizeExtractedFiles(List<Map<String, String>> extractedFiles);

       
                
      
                        
                               
       
    Map<String, Object> generateContentSummary(List<Map<String, String>> files);
}
