package com.haoran.music.service;

import com.haoran.music.common.dto.PageResult;
import com.haoran.music.vo.playlist.PlaylistVO;
import com.haoran.music.vo.playlist.CollaboratorVO;
import com.haoran.music.vo.playlist.PlaylistOperationLogVO;

import java.util.List;
import java.util.Map;

   
                      
                             
   
public interface PlaylistCollaborationService {

       
             
       
    void enableCollaboration(Long playlistId);

       
                           
      
                             
  
    void disableCollaboration(Long playlistId);

       
            
       
    void inviteCollaborator(Long playlistId, Long userId, String role);

       
             
       
    void acceptInvitation(Long playlistId);

       
             
       
    void declineInvitation(Long playlistId);

       
            
       
    void removeCollaborator(Long playlistId, Long userId);

    void leaveCollaboration(Long playlistId);

       
              
       
    List<CollaboratorVO> getCollaborators(Long playlistId, Long viewerId);

       
              
       
    void updateCollaboratorPermission(Long playlistId, Long userId, Boolean canAdd, Boolean canRemove, Boolean canEdit);

       
             
       
    PageResult<PlaylistOperationLogVO> getOperationLogs(
            Long playlistId, Integer page, Integer size, Long viewerId);

       
                      
      
                             
                        
                              
                   
       
    List<Map<String, Object>> getAuditEvents(Long playlistId, Integer limit, Long viewerId);

       
               
       
    List<PlaylistVO> getMyCollaborativePlaylists();

                                    
    List<PlaylistVO> getPublicCollaborativePlaylists(Long userId, Integer limit);

       
                 
       
    List<Map<String, Object>> getPendingInvitations();

       
                 
      
                         
                         
                               
                   
       
    String getCollaborativeRecommendReason(Long userId, Long songId, Long playlistId);

       
                   
      
                         
                        
                            
       
    java.util.Map<String, Object> getCollaborativePlaylistRecommend(Long userId, Integer limit);
}
