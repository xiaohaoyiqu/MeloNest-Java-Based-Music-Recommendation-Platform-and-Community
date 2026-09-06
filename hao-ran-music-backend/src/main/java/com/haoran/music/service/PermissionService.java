package com.haoran.music.service;

import com.haoran.music.enums.UserRole;

   
                      
                        
   
public interface PermissionService {

       
                     
      
                         
                     
       
    boolean isAdmin(Long userId);

       
                    
      
                         
                      
       
    boolean isModerator(Long userId);

       
               
      
                         
                     
       
    boolean isCreator(Long userId);

       
                
      
                         
                         
                     
       
    boolean hasRole(Long userId, String role);

       
                
      
                         
                         
                     
       
    boolean hasRole(Long userId, UserRole role);

       
               
      
                         
                   
       
    String getUserRole(Long userId);

       
                    
      
                             
                   
       
    Long getUserIdFromToken(String token);

       
                    
      
                           
                           
                           
                    
       
    boolean hasPermission(Long userId, String resource, String action);

       
             
      
                         
                        
       
    void updateUserRole(Long userId, String role);

       
               
      
                         
       
    void clearRoleCache(Long userId);
}
