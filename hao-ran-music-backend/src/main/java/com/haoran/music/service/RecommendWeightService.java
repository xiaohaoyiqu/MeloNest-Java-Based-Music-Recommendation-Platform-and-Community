package com.haoran.music.service;

import java.util.Map;

   
           
                
  
                      
   
public interface RecommendWeightService {

       
                
      
                            
                                        
                                   
                   
       
    Double calculateRecommendWeight(Long userId, Long contentId, Long creatorId);

       
                  
      
                         
                     
       
    Double getUserRecommendCoefficient(Long userId);

       
                
      
                            
                                                    
                   
       
    Double calculateBaseScore(Long contentId, String type);

       
                   
      
                            
                            
                          
       
    Double calculateUserInterestScore(Long userId, Long contentId);

       
               
      
                               
                                        
                                  
                     
       
    Double applyTimeDecay(Double score, Long contentTime, Integer halfLifeDays);

       
                 
      
                         
                             
       
    Double getVipWeight(Long userId);

       
                
      
                         
                            
       
    Double getRoleWeight(Long userId);

       
                   
      
                         
                             
       
    Double getCreditWeight(Long userId);

       
                     
      
                         
                            
       
    Double getDecorationWeight(Long userId);

       
               
      
                            
                             
                            
       
    Double getSocialRelationWeight(Long userId, Long creatorId);

       
                
      
                             
                               
       
    Double getCreatorTypeWeight(Long creatorId);

       
               
      
                             
                               
                             
                         
       
    Map<Long, Double> batchCalculateRecommendWeight(Long userId, java.util.List<Long> contentIds, String type);

       
                 
                            
      
                          
                          
                              
       
    Double getCollaborativePlaylistWeight(Long userId, Long songId);
}
