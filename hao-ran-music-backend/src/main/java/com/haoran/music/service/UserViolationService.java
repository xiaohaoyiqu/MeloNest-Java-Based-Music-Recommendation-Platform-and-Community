package com.haoran.music.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.haoran.music.entity.UserViolation;

   
                      
                          
   
public interface UserViolationService extends IService<UserViolation> {

       
           
                         
                              
                            
                                
                                        
                              
       
    void recordViolation(Long userId, String contentType, Long contentId,
                        String violationType, Integer level, String description);

       
              
                         
                          
       
    void checkAndPenalty(Long userId, Integer level);
}
