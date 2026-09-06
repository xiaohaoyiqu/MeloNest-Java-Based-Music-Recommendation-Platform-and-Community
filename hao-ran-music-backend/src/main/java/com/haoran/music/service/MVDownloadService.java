package com.haoran.music.service;

   
                      
                        
   
public interface MVDownloadService {

       
           
      
                           
                                                         
                          
                             
       
    void downloadMV(Long mvId, String quality, Long userId, javax.servlet.http.HttpServletResponse response);

       
               
      
                           
                         
                            
                   
       
    Object getDownloadInfo(Long mvId, String quality, Long userId);
}
