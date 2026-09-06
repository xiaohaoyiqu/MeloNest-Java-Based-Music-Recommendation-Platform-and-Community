   
                      
                        
   

package com.haoran.music.service;

import java.math.BigDecimal;
import java.util.Map;

   
           
                      
   
public interface SubscribeService {

       
                
      
                             
                             
                      
                            
                   
       
    Map<String, Object> setPlaylistSubscribe(Long creatorId, Long playlistId,
                                            BigDecimal price, Integer period);

       
           
      
                         
                             
                                                         
                              
                   
       
    Map<String, Object> subscribePlaylist(Long userId, Long playlistId,
                                         String subscribeType, Boolean autoRenew);

    Map<String, Object> subscribePlaylist(Long userId, Long playlistId,
                                          String subscribeType, Boolean autoRenew,
                                          String idempotencyKey);

                              
    Boolean completeSubscribeOrder(Long subscribeOrderId, Long paymentOrderId);

       
           
      
                         
                             
                   
       
    Boolean cancelSubscribe(Long userId, Long playlistId);

       
                 
      
                         
                             
                        
       
    Map<String, Object> checkSubscribed(Long userId, Long playlistId);

       
               
      
                         
                         
                     
                       
                   
       
    Map<String, Object> getMySubscribes(Long userId, String status,
                                       Integer page, Integer size);

       
                  
      
                             
                             
                     
                       
                     
       
    Map<String, Object> getPlaylistSubscribers(Long creatorId, Long playlistId,
                                              Integer page, Integer size);

       
               
      
                             
                   
       
    Map<String, Object> getPlaylistSubscribeStats(Long playlistId);

       
                   
      
                      
       
    Integer handleExpiredSubscribes();

       
             
      
                              
                   
       
    Map<String, Object> handleAutoRenew(Long subscribeId);

       
             
      
                              
                   
       
    Map<String, Object> getSubscribeDetail(Long subscribeId);

       
           
      
                         
                             
                                
                   
       
    Map<String, Object> renewSubscribe(Long userId, Long playlistId, String subscribeType);

       
             
      
                         
                             
                   
       
    Boolean cancelAutoRenew(Long userId, Long playlistId);

       
             
      
                         
                             
                   
       
    Boolean enableAutoRenew(Long userId, Long playlistId);

       
                     
      
                     
                        
       
    Map<String, Object> getExpiringSubscribes(Integer days);

       
               
      
                             
                             
                   
       
    Boolean cancelPlaylistSubscribe(Long creatorId, Long playlistId);
       
                 
      
                         
                     
                       
                   
       
    Map<String, Object> getMySubscribedPlaylists(Long userId, Integer page, Integer size);
}
