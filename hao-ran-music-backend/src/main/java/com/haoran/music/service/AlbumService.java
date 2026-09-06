package com.haoran.music.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.entity.Album;
import com.haoran.music.vo.album.AlbumVO;

import java.util.List;

   
                      
                      
   
public interface AlbumService extends IService<Album> {

       
                 
      
                          
                                       
                   
       
    AlbumVO getAlbumById(Long albumId, Long userId);

       
                        
      
                            
                                
                           
                       
                        
                                         
                             
                     
       
    IPage<AlbumVO> pageAlbums(PageQuery pageQuery, String keyword, String language, String area, String genre, String sortBy, Long userId);

       
               
      
                           
                               
                   
       
    List<AlbumVO> getAlbumsByArtist(Long artistId, Long userId);

       
              
      
                            
                               
                      
       
    IPage<AlbumVO> getNewAlbums(PageQuery pageQuery, Long userId);

       
             
      
                          
                        
                             
                     
       
    List<AlbumVO> getHotAlbums(String type, Integer limit, Long userId);

       
           
      
                          
                          
                   
       
    Boolean favoriteAlbum(Long userId, Long albumId);

       
             
      
                          
                          
                   
       
    Boolean unfavoriteAlbum(Long userId, Long albumId);

       
                  
      
                         
                      
       
    List<AlbumVO> getUserFavoriteAlbums(Long userId);

       
               
      
                          
                               
                   
       
    List<com.haoran.music.dto.song.SongVO> getAlbumSongs(Long albumId, Long userId);

       
                   
      
                        
                    
       
    List<AlbumVO> getNewAlbums(Integer limit);

       
               
                            
      
                          
                          
                     
       
    List<AlbumVO> getSimilarAlbums(Long albumId, Integer limit);

       
                   
      
                          
                          
                        
       
    List<AlbumVO> getArtistOtherAlbums(Long albumId, Integer limit);
}
