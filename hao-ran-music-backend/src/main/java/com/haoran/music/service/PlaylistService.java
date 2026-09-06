package com.haoran.music.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.dto.playlist.PlaylistCreateDTO;
import com.haoran.music.dto.playlist.PlaylistCopyMoveResult;
import com.haoran.music.dto.playlist.PlaylistUpdateDTO;
import com.haoran.music.entity.Playlist;
import com.haoran.music.vo.playlist.PlaylistVO;

import java.util.List;

   
                      
                      
   
public interface PlaylistService extends IService<Playlist> {

       
                       
      
                             
                                          
                                     
                                   
                   
       
    PlaylistVO getPlaylistById(Long playlistId, Long userId, Integer page, Integer size);

    PlaylistVO getPlaylistById(Long playlistId, Long userId, Integer page, Integer size,
                               String keyword, String language, String sortBy);

       
                     
      
                            
                           
                           
                      
                             
                     
       
    IPage<PlaylistVO> pagePlaylists(PageQuery pageQuery, String keyword, String language,
                                    List<String> languages, String languageMode, String tag, String category,
                                    Integer minSongCount, Integer maxSongCount, String paymentType, Long userId);

    default IPage<PlaylistVO> pagePlaylists(PageQuery pageQuery, String keyword, String language,
                                            String tag, Long userId) {
        return pagePlaylists(pageQuery, keyword, language, null, "any", tag, null,
                null, null, null, userId);
    }

       
                
      
                         
                   
       
    List<PlaylistVO> getUserPlaylists(Long userId);

       
                        
      
                         
                   
       
    PlaylistVO getFavoritePlaylist(Long userId);

       
           
      
                         
                         
                   
       
    Long createPlaylist(Long userId, PlaylistCreateDTO dto);

       
           
      
                           
                           
                   
       
    Boolean updatePlaylist(Long userId, PlaylistUpdateDTO dto);

       
           
      
                             
                             
                   
       
    Boolean deletePlaylist(Long userId, Long playlistId);

       
              
      
                             
                             
                               
                      
       
    Integer addSongsToPlaylist(Long userId, Long playlistId, List<Long> songIds);

       
              
      
                             
                             
                               
                      
       
    Integer removeSongsFromPlaylist(Long userId, Long playlistId, List<Long> songIds);

       
               
      
                             
                             
                                  
                                              
                   
       
    Boolean updateSongOrders(Long userId, Long playlistId, List<Long> songIds, String expectedOrderVersion);

       
           
      
                             
                             
                   
       
    Boolean favoritePlaylist(Long userId, Long playlistId);

       
             
      
                             
                             
                   
       
    Boolean unfavoritePlaylist(Long userId, Long playlistId);

       
             
      
                          
                        
                             
                     
       
    List<PlaylistVO> getHotPlaylists(String type, Integer limit, Long userId);

       
                 
      
                               
                                    
                                     
                                     
                                     
       
    PlaylistCopyMoveResult copySongsToPlaylist(Long userId, Long sourcePlaylistId, List<Long> songIds, Long targetPlaylistId);

       
                                 
      
                               
                                    
                                     
                                     
                                     
       
    PlaylistCopyMoveResult moveSongsToPlaylist(Long userId, Long sourcePlaylistId, List<Long> songIds, Long targetPlaylistId);

       
                           
      
                         
                      
       
    List<PlaylistVO> getFavoritePlaylists(Long userId);

       
                           
      
                         
                              
       
    List<PlaylistVO> getAllUserPlaylists(Long userId);

       
             
      
                        
                     
       
    List<PlaylistVO> getFeaturedPlaylists(Integer limit);

       
                 
      
                         
                        
                   
       
    List<PlaylistVO> getPlaylistsByCategory(String category, Integer limit);

       
               
      
                   
       
    List<String> getPlaylistCategories();

       
           
      
                         
                            
                   
       
    IPage<PlaylistVO> searchPlaylists(String keyword, PageQuery pageQuery);

       
             
      
                        
                     
       
    List<PlaylistVO> getLatestPlaylists(Integer limit);

       
                
      
                        
                        
       
    List<PlaylistVO> getUserCreatedPlaylists(Integer limit);

       
               
      
                             
                         
                   
       
    com.haoran.music.vo.playlist.PlaylistSubscriptionDataVO getSubscriptionData(Long playlistId, Long userId);

       
                
      
                             
                         
                        
                    
       
    java.util.List<com.haoran.music.vo.playlist.PlaylistSubscriberVO> getSubscribers(Long playlistId, Long userId, Integer limit);

       
               
      
                         
                      
                   
       
    Boolean updatePaidSettings(Long userId, com.haoran.music.dto.playlist.PlaylistPaidSettingsDTO dto);

       
             
      
                         
                      
                   
       
    Boolean setPlaylistPaid(Long userId, com.haoran.music.dto.playlist.PlaylistPaidSettingsDTO dto);

       
                     
                                                
                                                              
      
                         
                         
                                 
       
    Boolean checkSongIsFavorited(Long userId, Long songId);

       
                
                     
      
                            
                                         
                       
       
    
      
                 
       
                     
      
                         
                     
       
    Integer getCollaboratePlaylistCount(Long userId);

    java.util.List<com.haoran.music.entity.Playlist> filterPlaylistsByLanguage(
            java.util.List<com.haoran.music.entity.Playlist> playlists, String language);
}
