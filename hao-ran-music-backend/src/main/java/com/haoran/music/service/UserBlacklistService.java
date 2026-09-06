   
                      
                         
   

package com.haoran.music.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.haoran.music.entity.UserBlacklist;
import com.haoran.music.vo.blacklist.BlacklistUserVO;

import java.util.Collection;
import java.util.List;

   
            
   
public interface UserBlacklistService extends IService<UserBlacklist> {

       
               
      
                                   
                                       
                                    
                   
       
    Boolean addToBlacklist(Long userId, Long blacklistedUserId, String reason);

       
             
      
                                   
                                       
                   
       
    Boolean removeFromBlacklist(Long userId, Long blacklistedUserId);

       
                     
      
                         
                        
       
    List<Long> getBlacklistedUserIds(Long userId);

       
                                
      
                                          
                                         
                                  
       
    List<Long> getBlacklisterUserIds(Long blacklistedUserId, Collection<Long> userIds);

       
                      
      
                         
                          
       
    List<BlacklistUserVO> getBlacklistUserInfo(Long userId);

       
                  
      
                                   
                                       
                      
       
    Boolean isBlacklisted(Long userId, Long blacklistedUserId);

       
                  
                                      
      
                                   
                                       
       
    void updateMutualStatus(Long userId, Long blacklistedUserId);
}
