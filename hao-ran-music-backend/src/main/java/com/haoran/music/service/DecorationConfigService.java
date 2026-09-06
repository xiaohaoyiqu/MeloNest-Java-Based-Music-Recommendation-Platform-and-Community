   
                      
                           
   

package com.haoran.music.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.haoran.music.entity.DecorationConfig;

   
              
   
public interface DecorationConfigService extends IService<DecorationConfig> {

       
                 
                               
                   
       
    DecorationConfig getByDecorationId(String decorationId);

       
                 
                                 
                   
       
    java.util.List<DecorationConfig> listByType(String decorationType);
}
