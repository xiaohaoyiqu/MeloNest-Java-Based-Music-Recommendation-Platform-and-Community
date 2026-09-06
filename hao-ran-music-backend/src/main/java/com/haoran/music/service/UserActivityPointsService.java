package com.haoran.music.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.haoran.music.entity.UserActivityPoints;

import java.util.List;
import java.util.Map;

   
                      
                         
   
public interface UserActivityPointsService extends IService<UserActivityPoints> {

       
            
      
                             
                            
                           
                            
                     
       
    Integer addPoints(Long userId, Integer points, String type, String description);

       
                 
      
                             
                            
                           
                            
                             
                              
                     
       
    Integer addPoints(Long userId, Integer points, String type, String description,
                     Long relatedId, String relatedType);

       
            
      
                             
                            
                           
                            
                             
       
    Integer consumePoints(Long userId, Integer points, String type, String description);

       
                  
      
                         
                    
       
    Integer getUserTotalPoints(Long userId);

       
                   
      
                         
                    
       
    Integer getTodayPoints(Long userId);

       
              
      
                       
       
    void expirePoints(Integer days);

                                                                        
    List<Map<String, Object>> getVipExchangePackages();

       
                                                                                              
       
    Map<String, Object> redeemVip(Long userId, String packageCode, String requestId);
}
