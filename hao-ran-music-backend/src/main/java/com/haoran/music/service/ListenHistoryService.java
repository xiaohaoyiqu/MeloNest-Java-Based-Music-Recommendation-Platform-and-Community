package com.haoran.music.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.haoran.music.entity.ListenHistory;
import com.haoran.music.vo.song.ListenHistoryVO;
import com.haoran.music.dto.song.SongVO;

import java.util.List;

   
                      
                        
   
public interface ListenHistoryService extends IService<ListenHistory> {

       
             
      
                            
                            
                               
                            
       
    void addListenRecord(Long userId, String songId, Integer progress, String quality, Integer isLocal);

       
                      
       
    void addListenRecord(Long userId, String songId, Integer progress, String quality,
                         Integer isLocal, String eventId);

       
                               
       
    boolean updateListenProgress(Long userId, String songId, Integer progress, String quality, Integer isLocal);

       
                    
      
                         
                         
                   
       
    List<SongVO> getRecentSongs(Long userId, Integer limit);

       
                   
      
                         
                       
                         
                     
       
    List<ListenHistory> getUserHistory(Long userId, Integer page, Integer size);

       
                          
      
                         
                       
                         
                             
       
    List<ListenHistoryVO> getUserHistoryWithDetails(Long userId, Integer page, Integer size);

       
               
      
                         
       
    void clearUserHistory(Long userId);

       
                   
      
                         
                   
       
    Long getUserHistoryCount(Long userId);

       
               
      
                          
                                   
                   
       
    int importHistory(Long userId, List<Long> songIds);

       
                 
      
                              
                                    
                     
       
    boolean deleteHistory(Long historyId, Long userId);
}
