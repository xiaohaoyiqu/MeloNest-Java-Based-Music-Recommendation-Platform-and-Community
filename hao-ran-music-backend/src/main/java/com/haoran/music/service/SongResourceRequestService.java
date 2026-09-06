package com.haoran.music.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.vo.song.SongResourceRequestVO;
import com.haoran.music.dto.SongResourceRequestAddDTO;
import com.haoran.music.dto.SongResourceRequestHandleDTO;
import org.springframework.web.multipart.MultipartFile;

   
                      
                             
   
public interface SongResourceRequestService {

       
               
       
    Long createRequest(Long userId, String userNickname, SongResourceRequestAddDTO dto);

       
                
       
    Long createRequestWithFile(Long userId, String userNickname, String songName, String artistName, String albumName, String versionInfo, String sourceDescription, String remark, MultipartFile file);

       
                  
       
    IPage<SongResourceRequestVO> getUserRequests(Long userId, PageQuery pageQuery, String status);

       
                 
       
    boolean hasRequestedSong(Long userId, String songName, String artistName);

       
                
       
    int getTodayRequestCount(Long userId);

       
                     
       
    IPage<SongResourceRequestVO> getAllRequests(PageQuery pageQuery, String status);

       
           
       
    void handleRequest(Long handlerId, String handlerName, SongResourceRequestHandleDTO dto);

       
              
       
    long getPendingCount();
}
