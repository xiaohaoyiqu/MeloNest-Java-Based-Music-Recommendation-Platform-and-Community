package com.haoran.music.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.entity.FavoriteHistory;

import java.util.Map;

   
                      
                                                    
   
public interface FavoriteHistoryService extends IService<FavoriteHistory> {

       
                             
      
                                
                                
                                                              
                      
       
    Boolean recordFavoriteAction(Long userId, Long songId, Integer actionType);

       
                                              
      
                               
                                   
                                    
       
    IPage<FavoriteHistory> getFavoriteHistory(Long userId, PageQuery pageQuery);

       
                              
      
                            
                                                                         
       
    Map<String, Object> getFavoriteStatistics(Long userId);

       
                                    
      
                            
                      
       
    Boolean clearFavoriteHistory(Long userId);

       
                                            
      
                            
                                      
                      
       
    Boolean deleteFavoriteHistory(Long userId, Long id);
}
