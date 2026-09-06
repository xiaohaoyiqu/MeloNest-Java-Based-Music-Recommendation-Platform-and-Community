package com.haoran.music.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.entity.MV;
import com.haoran.music.vo.mv.MVVO;

import java.time.LocalDate;
import java.util.List;

   
                      
                      
   
public interface MVService extends IService<MV> {

       
                 
      
                          
                                         
                   
       
    MVVO getMVById(Long mvId, Long userId);

       
                        
      
                            
                                        
                                      
                           
                                         
                             
                     
       
    IPage<MVVO> pageMVs(PageQuery pageQuery, String area, String genre, String keyword, String sortBy, Long userId);

    IPage<MVVO> pageMVs(PageQuery pageQuery, String area, String genre, String keyword, String sortBy,
                        String language, Integer publishYear, LocalDate publishDateStart,
                        LocalDate publishDateEnd, Integer minDuration, Integer maxDuration,
                        String quality, String binding, String albumType, Long userId);

       
                   
      
                         
                             
                   
       
    List<MVVO> getMVsBySongId(Long songId, Long userId);

       
                   
      
                           
                            
                                
                     
       
    IPage<MVVO> getMVsByArtistId(Long artistId, PageQuery pageQuery, Long userId);

       
               
      
                        
                             
                     
       
    List<MVVO> getHotMVs(Integer limit, Long userId);

       
               
      
                        
                             
                     
       
    List<MVVO> getNewestMVs(Integer limit, Long userId);

       
             
      
                          
                         
       
    void recordPlay(Long mvId, Long userId);

       
           
      
                         
                          
                   
       
    Boolean favoriteMV(Long userId, Long mvId);

       
             
      
                         
                          
                   
       
    Boolean unfavoriteMV(Long userId, Long mvId);

       
                  
      
                         
                      
       
    List<MVVO> getFavoriteMVs(Long userId);

       
           
      
                         
                          
                   
       
    Boolean likeMV(Long userId, Long mvId);

       
             
      
                         
                          
                   
       
    Boolean unlikeMV(Long userId, Long mvId);

       
                       
      
                           
                                          
                    
       
    String getPlayUrl(Long mvId, String quality, Long userId);

                              
    String getPreviewUrl(Long mvId, Long userId);

       
                    
       
    void streamMV(Long mvId, String quality, String grant, String range, Long userId,
                  javax.servlet.http.HttpServletResponse response);

       
               
                       
      
                         
                        
                     
       
    List<MVVO> getSimilarMVs(Long mvId, Integer limit);

       
                  
      
                         
                        
                       
       
    List<MVVO> getArtistOtherMVs(Long mvId, Integer limit);
}
