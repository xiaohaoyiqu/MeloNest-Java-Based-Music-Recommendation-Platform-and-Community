package com.haoran.music.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.haoran.music.dto.artist.ArtistApplicationDTO;
import com.haoran.music.entity.ArtistApplication;

   
                      
                         
   
public interface ArtistApplicationService extends IService<ArtistApplication> {

       
              
      
                         
                      
                   
       
    Long submitApplication(Long userId, ArtistApplicationDTO dto);

       
           
      
                                
                              
                                    
                               
       
    void reviewApplication(Long applicationId, Long reviewerId, Integer status, String reviewReason);

       
               
      
                         
                   
       
    ArtistApplication getUserApplication(Long userId);

       
               
      
                        
                       
                         
                   
       
    IPage<ArtistApplication> pageApplications(Integer current, Integer size, Integer status);

       
                                
      
                         
                     
       
    Boolean canApply(Long userId);

}
