package com.haoran.music.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.entity.LocalMusic;
import com.haoran.music.vo.song.LocalMusicVO;

import java.util.List;

   
                      
                        
   
public interface LocalMusicService extends IService<LocalMusic> {

       
                   
      
                                          
                                     
                            
                                         
                   
       
    IPage<LocalMusicVO> scanSongsByPath(String path, Long userId, PageQuery pageQuery, String nginxUrlPrefix);

       
                         
      
                         
                            
                                         
                        
       
    List<LocalMusicVO> addBySongIds(Long userId, List<Long> songIds, String nginxUrlPrefix);

       
                           
      
              
                                           
                                               
      
                          
                                         
                          
                          
                          
                                         
                       
       
    LocalMusicVO addLocalMusic(Long userId, String filePath, String name, String artist, String album, String nginxUrlPrefix);

       
                 
      
                            
                                              
                                         
                        
       
    List<LocalMusicVO> addBatchLocalMusic(Long userId, List<String> filePaths, String nginxUrlPrefix);

       
                     
      
                            
                            
                                         
                     
       
    IPage<LocalMusicVO> getUserLocalMusic(Long userId, PageQuery pageQuery, String nginxUrlPrefix, Integer resourceType);

       
               
      
                          
                         
                                         
                     
       
    LocalMusicVO getLocalMusicDetail(Long id, Long userId, String nginxUrlPrefix);

       
             
      
                          
                         
                   
       
    Boolean deleteLocalMusic(Long id, Long userId);

       
               
      
                       
                         
                     
                             
                                
                       
       
    LocalMusicVO updateLocalMusic(Long id, Long userId, String name, String artistName, String albumName, String versionType, String versionName, String nginxUrlPrefix);

       
               
      
                           
                         
                         
                   
       
    Boolean updateLocalMusicLyric(Long id, Long userId, String lyric);


       
               
      
                            
                         
                   
       
    Integer batchDeleteLocalMusic(List<Long> ids, Long userId);

       
               
      
                         
                   
       
    Boolean clearUserLocalMusic(Long userId);

       
             
      
                           
                       
                   
       
    Boolean incrementPlayCount(Long userId, Long id);

       
                   
      
                                    
                                     
                            
                                         
                   
       
    IPage<LocalMusicVO> scanMVsByPath(String path, Long userId, PageQuery pageQuery, String nginxUrlPrefix);

       
                          
      
                         
                           
                                         
                        
       
    List<LocalMusicVO> addMVsByMvIds(Long userId, List<Long> mvIds, String nginxUrlPrefix);

       
               
      
                         
                       
                         
                       
                             
                                         
                                  
                               
                       
       
    LocalMusicVO addManualMV(Long userId, String name, String artist, String url, String cover, String nginxUrlPrefix, Long fileSize, Integer duration, Integer quality);

       
             
      
                         
                       
                         
                       
                             
                                         
                                  
                               
                       
       
    LocalMusicVO addManualSong(Long userId, String name, String artist, String url, String cover, String nginxUrlPrefix, Long fileSize, Integer duration, Integer quality);

       
                      
      
                                         
                                 
                         
                            
                                         
                     
       
    IPage<LocalMusicVO> searchBySongName(String songName, Long excludeId, Long userId, PageQuery pageQuery, String nginxUrlPrefix);
}
