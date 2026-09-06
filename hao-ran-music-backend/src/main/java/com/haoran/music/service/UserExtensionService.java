package com.haoran.music.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.haoran.music.entity.UserExtension;

   
                      
                                                
   
public interface UserExtensionService extends IService<UserExtension> {

       
                                    
      
                            
                                           
       
    UserExtension getByUserId(Long userId);
}
