package com.haoran.music.service;

import javax.servlet.http.HttpServletResponse;

   
                      
                        
   
public interface SongDownloadService {

       
           
      
                         
                        
                         
                             
       
    void downloadSong(Long songId, String quality, Long userId, HttpServletResponse response);

       
             
      
                         
                        
                           
                   
       
    Object getDownloadInfo(Long songId, String quality, Long userId);
}
