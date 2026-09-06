package com.haoran.music.service;

import java.util.List;
import java.util.Map;

   
             
                           
  
                      
   
public interface SmartPlaylistService {

       
                   
      
                         
                               
                                    
                      
       
    Map<String, Object> generatePlaylistByPrompt(Long userId, String prompt, Integer durationMinutes);

       
                   
      
                         
                                                                                   
                                
                      
       
    Map<String, Object> generatePlaylistByActivity(Long userId, String activity, Integer durationMinutes);

       
                     
      
                         
                                  
                                
                      
       
    Map<String, Object> generatePlaylistBySeeds(Long userId, List<Long> seedSongIds, Integer durationMinutes);

       
                
                        
      
                         
                              
                            
                            
                      
       
    Map<String, Object> generateGradualPlaylist(Long userId, Double startEnergy, Double endEnergy, Integer songCount);

       
                
      
                         
                         
                              
                            
                   
       
    Map<String, Object> saveGeneratedPlaylist(Long userId, String name, String description, List<Long> songIds);

       
                  
      
                           
                     
       
    List<String> getPlaylistNameSuggestions(String activity);
}
