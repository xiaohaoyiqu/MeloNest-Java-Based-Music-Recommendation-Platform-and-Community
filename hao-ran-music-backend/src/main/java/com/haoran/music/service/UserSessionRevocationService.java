package com.haoran.music.service;

   
              
  
                                           
  
                      
   
public interface UserSessionRevocationService {

       
                          
      
                                         
      
                         
                               
       
    void revokeWebSocketSessions(Long userId, String reason);
}
