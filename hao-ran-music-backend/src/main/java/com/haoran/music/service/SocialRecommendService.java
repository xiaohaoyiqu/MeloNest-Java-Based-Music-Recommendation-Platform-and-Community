package com.haoran.music.service;

import com.haoran.music.vo.recommend.RecommendVO;

import java.util.List;

   
           
  
        
                  
                
                     
              
  
                      
   
public interface SocialRecommendService {

       
                        
      
                         
                        
                   
       
    RecommendVO getSocialBasedRecommend(Long userId, Integer limit);

       
                  
      
                         
                        
                   
       
    RecommendVO getFriendsLikedRecommend(Long userId, Integer limit);

       
                    
      
                         
                        
                   
       
    RecommendVO getFollowingUsersContent(Long userId, Integer limit);

       
                            
      
                         
                        
                   
       
    RecommendVO getSocialInteractionRecommend(Long userId, Integer limit);

       
                         
      
                         
                        
                   
       
    RecommendVO getMixedSocialRecommend(Long userId, Integer limit);

       
                   
      
                         
                            
                              
                   
       
    String getSocialRecommendReason(Long userId, Long contentId, String contentType);

       
                             
      
                         
                         
                           
       
    Integer calculateSocialIntimacy(Long userId1, Long userId2);

       
                  
      
                         
                                  
       
    Object getUserSocialPortrait(Long userId);
}
