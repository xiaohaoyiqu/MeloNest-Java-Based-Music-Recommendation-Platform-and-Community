package com.haoran.music.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.haoran.music.dto.lyric.LyricRequestDTO;
import com.haoran.music.entity.LyricRequest;

   
                      
                        
   
public interface LyricRequestService extends IService<LyricRequest> {

       
             
      
                         
                      
                   
       
    Long submitRequest(Long userId, LyricRequestDTO dto);

       
             
      
                            
                              
                                    
                               
       
    void reviewRequest(Long requestId, Long reviewerId, Integer status, String reviewReason);

       
                    
      
                            
                                         
       
    void applyRequest(Long requestId, Long operatorId);

       
               
      
                        
                       
                         
                   
       
    IPage<LyricRequest> pageRequests(Integer current, Integer size, Integer status);

       
                  
      
                         
                   
       
    java.util.List<LyricRequest> getSongRequests(Long songId);

       
                                  
      
                         
                         
                     
       
    Boolean canSubmit(Long userId, Long songId);
}
