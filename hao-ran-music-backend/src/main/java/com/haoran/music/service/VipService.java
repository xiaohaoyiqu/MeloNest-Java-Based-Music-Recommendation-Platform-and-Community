   
                      
                       
   

package com.haoran.music.service;

import java.time.LocalDateTime;
import java.util.Map;

   
          
                   
   
public interface VipService {

       
            
      
                         
                                                                 
                           
       
    Map<String, Object> rechargeVip(Long userId, String vipType);

       
               
      
                             
                            
                         
                   
       
    Map<String, Object> creatorApplyVip(Long creatorId, Integer applyDays, String reason);

       
                 
      
                          
                              
                           
                               
                   
       
    Map<String, Object> reviewCreatorVipApply(Long applyId, Long reviewerId,
                                             Boolean approved, String reviewReason);

       
                
      
                   
       
    Map<String, Object> getVipPriceConfig();

       
                
      
                         
                      
       
    Map<String, Object> checkVipStatus(Long userId);

       
                
      
                         
                   
       
    Long getVipRemainingDays(Long userId);

       
                    
      
                      
       
    Integer handleExpiredVips();

       
                      
      
                         
                     
                       
                              
                   
       
    Boolean grantVip(Long userId, Integer days, String reason, Long operatorId);

       
                   
      
                         
                     
                       
                              
                             
                   
       
    Boolean grantVipWithSource(Long userId, Integer days, String reason, Long operatorId, String sourceType);

       
                
      
                   
       
    Map<String, Object> getVipPrivileges();

       
                   
      
                             
                       
       
    Map<String, Object> checkCreatorVipCondition(Long creatorId);

       
                   
      
                             
                     
       
    Map<String, Object> getCreatorVipApplies(Long creatorId);

       
                        
      
                     
                       
                    
       
    Map<String, Object> getPendingVipApplies(Integer page, Integer size);

       
                  
      
                         
                     
                       
                   
       
    Map<String, Object> getUserVipOrders(Long userId, Integer page, Integer size);

       
            
      
                         
                           
                   
       
    Map<String, Object> renewVip(Long userId, String vipType);

       
                    
      
                         
                            
                     
       
    Boolean canUseVipPrivilege(Long userId, String privilege);

       
                
                           
      
                         
                                                                          
                                
       
    void recordVipSource(Long userId, String sourceType, LocalDateTime expireTime);

       
                  
      
                         
                                                   
       
    String getVipSourceType(Long userId);
}
