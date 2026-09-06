package com.haoran.music.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.entity.SongLike;

import java.util.List;

   
                      
                           
   
public interface SongLikeService extends IService<SongLike> {

       
           
      
                         
                         
                   
       
    Boolean likeSong(Long userId, Long songId);

       
             
      
                         
                         
                   
       
    Boolean unlikeSong(Long userId, Long songId);

       
           
      
                         
                         
                   
       
    Boolean favoriteSong(Long userId, Long songId);

       
             
      
                         
                         
                   
       
    Boolean unfavoriteSong(Long userId, Long songId);

       
             
      
                         
                         
                                         
       
    Boolean toggleLike(Long userId, Long songId);

       
             
      
                         
                         
                                         
       
    Boolean toggleFavorite(Long userId, Long songId);

       
              
      
                         
                         
                    
       
    Boolean isLiked(Long userId, Long songId);

       
              
      
                         
                         
                    
       
    Boolean isFavorited(Long userId, Long songId);

       
                  
      
                            
                            
                     
       
    IPage<SongLike> getFavoriteSongs(Long userId, PageQuery pageQuery);

       
                  
      
                         
                   
       
    Integer getFavoriteCount(Long userId);

       
                    
      
                          
                            
                           
       
    java.util.Map<Long, java.util.Map<String, Boolean>> getBatchSongStatus(Long userId, List<Long> songIds);

       
                                       
      
                         
                            
                        
       
    java.util.Set<Long> getFavoriteSongIdsBatch(Long userId, List<Long> songIds);
}
