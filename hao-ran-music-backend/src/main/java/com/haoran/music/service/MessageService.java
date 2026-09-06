   
                      
                      
   

package com.haoran.music.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.entity.Conversation;
import com.haoran.music.entity.Message;

import java.util.List;
import java.util.Map;

   
         
   
public interface MessageService {

       
           
      
                              
                              
                              
                             
                                 
                                       
                   
       
    Long sendMessage(Long senderId, Long receiverId, String messageType,
                    String content, Long resourceId, String resourceData);

       
             
      
                              
                              
                             
                   
       
    Long sendTextMessage(Long senderId, Long receiverId, String content);

       
             
      
                              
                              
                             
                   
       
    Long sendEmojiMessage(Long senderId, Long receiverId, String emojiCode);

       
             
      
                              
                              
                              
                   
       
    Long sendImageMessage(Long senderId, Long receiverId, String imageUrl);

       
                       
      
                            
                              
                                        
                   
       
    Long sendImageMessage(Long senderId, Long receiverId, Long attachmentAssetId);

       
               
      
                              
                              
                             
                                 
                   
       
    Long sendSongMessage(Long senderId, Long receiverId, Long songId, String songData);

       
               
      
                              
                              
                             
                                 
                   
       
    Long sendAlbumMessage(Long senderId, Long receiverId, Long albumId, String albumData);

       
                
       
    Long sendMvMessage(Long senderId, Long receiverId, Long mvId, String mvData);

       
               
      
                              
                              
                             
                                   
                   
       
    Long sendPlaylistMessage(Long senderId, Long receiverId, Long playlistId, String playlistData);

       
                   
      
                                  
                                  
                                
                   
       
    IPage<Message> getChatMessages(Long currentUserId, Long otherUserId, PageQuery pageQuery);

       
                     
      
                                  
                                  
                                
                       
       
    IPage<Map<String, Object>> getChatMessageViews(Long currentUserId, Long otherUserId, PageQuery pageQuery);

       
             
      
                         
                                
       
    List<Map<String, Object>> getConversations(Long userId);

       
              
      
                            
                            
                   
       
    boolean markAsRead(Long messageId, Long userId);

       
                      
      
                                  
                                  
                      
       
    int markAllAsRead(Long currentUserId, Long otherUserId);

       
                       
      
                            
                              
                   
       
    boolean recallMessage(Long messageId, Long userId);

       
                    
      
                            
                              
                   
       
    boolean deleteMessage(Long messageId, Long userId);

                                    
    int batchDeleteMessages(List<Long> messageIds, Long userId);

                                
    List<Long> forwardMessages(List<Long> messageIds, Long senderId, Long receiverId);

       
               
      
                         
                     
       
    int getTotalUnreadCount(Long userId);

       
                    
      
                           
                                
                     
       
    int getUnreadCountFromUser(Long userId, Long otherUserId);

       
                
      
                                  
                                  
                                
                   
       
    boolean setConversationPinned(Long currentUserId, Long otherUserId, boolean pinned);

       
                  
      
                                  
                                  
                                
                   
       
    boolean setUserBlocked(Long currentUserId, Long otherUserId, boolean blocked);

       
                   
      
                                  
                                  
                      
       
    int clearChatHistory(Long currentUserId, Long otherUserId);

       
             
      
                            
                           
                            
                   
       
    IPage<Message> searchMessages(Long userId, String keyword, PageQuery pageQuery);

       
               
      
                            
                           
                            
                       
       
    IPage<Map<String, Object>> searchMessageViews(Long userId, String keyword, PageQuery pageQuery);
}
