   
                      
                        
   

package com.haoran.music.service;

import java.util.List;
import java.util.Map;

   
           
   
public interface SongVoteService {

       
            
      
                         
                         
                   
       
    Integer vote(Long songId, Long userId);

       
             
      
                         
                         
                   
       
    Boolean unvote(Long songId, Long userId);

       
               
      
                        
                           
                     
       
    List<Map<String, Object>> getHotVotedSongs(Integer limit, Long userId);

       
                            
                             
      
                         
                        
                            
       
    List<Map<String, Object>> getPersonalizedVotedSongs(Long userId, Integer limit);

       
               
      
                                    
                   
       
    Map<String, Object> getTodayVoteStats(Long userId);

       
              
      
                         
                         
                    
       
    Boolean hasVoted(Long songId, Long userId);
}
