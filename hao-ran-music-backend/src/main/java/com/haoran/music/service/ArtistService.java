package com.haoran.music.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.haoran.music.vo.album.AlbumVO;
import com.baomidou.mybatisplus.extension.service.IService;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.entity.Artist;
import com.haoran.music.vo.artist.ArtistVO;

import java.util.List;

   
                      
                      
   
public interface ArtistService extends IService<Artist> {

       
                 
      
                           
                                        
                   
       
    ArtistVO getArtistById(Long artistId, Long userId);

       
               
      
                            
                           
                               
                               
                                           
                             
                     
       
    IPage<ArtistVO> pageArtists(PageQuery pageQuery, String area, String keyword, String initial, String sortBy, Long userId);

       
                 
      
                                     
                             
                   
       
    List<ArtistVO> getArtistsByLetter(String letter, Long userId);

       
             
      
                        
                             
                     
       
    List<ArtistVO> getHotArtists(Integer limit, Long userId);

       
           
      
                         
                            
                               
                     
       
    IPage<ArtistVO> searchArtists(String keyword, PageQuery pageQuery, Long userId);

       
           
      
                           
                           
                   
       
    Boolean followArtist(Long userId, Long artistId);

       
             
      
                           
                           
                   
       
    Boolean unfollowArtist(Long userId, Long artistId);

       
                
      
                    
       
    List<String> getArtistLetters();

       
                
      
                           
                           
                               
                   
       
    List<AlbumVO.SongSimpleVO> getArtistHotSongs(Long artistId, Integer limit, Long userId);

       
                   
      
                                                             
                             
                   
       
    List<ArtistVO> getArtistList(String area, Long userId);

       
                       
                          
      
                           
       
    void updateArtistCount(Long artistId);

       
                         
      
                              
       
    void batchUpdateArtistCount(List<Long> artistIds);
       
                                                                        
       
    void syncAllArtistCount();

       
                
      
                                                                    
                        
                   
       
    List<ArtistVO> getArtistsByCategory(String category, Integer limit);

       
                 
      
                        
                             
                    
       
    List<ArtistVO> getHotCreators(Integer limit, Long userId);

       
                 
      
                        
                    
       
    List<ArtistVO> getNewCreators(Integer limit);

       
                 
      
                        
                    
       
    List<ArtistVO> getActiveCreators(Integer limit);

       
               
                       
      
                           
                           
                     
       
    List<ArtistVO> getSimilarArtists(Long artistId, Integer limit);
}
