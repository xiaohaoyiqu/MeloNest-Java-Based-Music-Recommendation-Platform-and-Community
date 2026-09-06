package com.haoran.music.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.entity.CreatorAlbum;
import com.haoran.music.entity.CreatorAlbumSong;

import java.util.List;

   
                      
                         
   
public interface CreatorAlbumService extends IService<CreatorAlbum> {

       
           
                         
                        
                   
       
    Long createAlbum(Long userId, CreatorAlbum album);

       
             
                          
                         
                        
       
    void updateAlbum(Long albumId, Long userId, CreatorAlbum album);

       
           
                          
                         
       
    void publishAlbum(Long albumId, Long userId);

       
           
                          
                         
       
    void deleteAlbum(Long albumId, Long userId);

       
             
                          
                   
       
    CreatorAlbum getAlbumDetail(Long albumId, Long viewerId);

       
               
                         
                            
                   
       
    IPage<CreatorAlbum> getMyAlbums(Long userId, PageQuery pageQuery);

       
              
                          
                         
                            
       
    void addSongsToAlbum(Long albumId, Long userId, List<Long> songIds);

       
              
                          
                         
                         
       
    void removeSongFromAlbum(Long albumId, Long songId, Long userId);

       
                 
                          
                         
                             
                         
       
    void updateSongPosition(Long albumId, Long songId, Integer newPosition, Long userId);

       
                
                          
                   
       
    List<CreatorAlbumSong> getAlbumSongs(Long albumId, Long viewerId);

       
                     
                          
                         
                            
                     
       
    Long uploadSongToAlbum(Long albumId, Long userId, CreatorAlbumSong albumSong);
}
