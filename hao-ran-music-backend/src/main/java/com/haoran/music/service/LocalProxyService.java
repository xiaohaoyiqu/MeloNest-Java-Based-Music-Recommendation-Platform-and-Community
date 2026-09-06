package com.haoran.music.service;

import com.haoran.music.dto.localMusic.FileInfoDTO;

   
                      
                        
   
public interface LocalProxyService {

       
                      
      
                                 
                                
       
    FileInfoDTO getFileInfo(String filePath);

       
             
      
                           
                                 
       
    Long getFileSize(String filePath);

       
             
      
                           
                              
       
    Integer getAudioDuration(String filePath);
}
