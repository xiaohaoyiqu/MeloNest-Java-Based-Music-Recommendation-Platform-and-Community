package com.haoran.music.service;

import java.util.List;
import java.util.Map;

   
         
                            
  
                      
   
public interface MusicMapService {

       
               
                    
      
                                                                               
                        
                   
       
    List<Map<String, Object>> getMusicMapData(String region, Integer limit);

       
               
      
                   
       
    Map<String, Object> getMapRegions();

       
                
      
                         
                        
                   
       
    List<Map<String, Object>> getSongsByRegion(String region, Integer limit);

       
                  
      
                          
                         
                         
                        
                   
       
    List<Map<String, Object>> getNearbySongs(Double valence, Double energy, Double radius, Integer limit);

       
                      
      
                         
                   
       
    Map<String, Object> getUserExplorationMap(Long userId);

       
                    
      
                         
                   
       
    List<Map<String, Object>> getUserPreferredRegions(Long userId);
}
