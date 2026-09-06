package com.haoran.music.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.haoran.music.dto.NotificationDetailVO;
import com.haoran.music.entity.Notification;

import java.math.BigDecimal;
import java.util.List;

   
                      
                      
   
public interface NotificationService extends IService<Notification> {

       
               
      
                                 
                                         
                               
                               
       
    void sendApprovalNotification(Long userId, String contentType, String contentName, Long relatedId);

       
               
      
                                 
                               
                               
                               
       
    void sendRejectionNotification(Long userId, String contentType, String reason, Long relatedId);

       
                  
      
                           
       
    void sendCreatorApprovedNotification(Long userId);

       
                  
      
                           
                         
       
    void sendCreatorRejectedNotification(Long userId, String reason);

       
             
      
                            
                          
                          
                          
       
    void sendSystemNotification(Long userId, String title, String content, String link);

       
                      
      
                           
                      
                        
                       
                               
       
    void sendSystemNotificationOnce(Long userId, String title, String content,
                                    String link, String businessKey);

       
                 
      
                         
                   
       
    Long getUnreadCount(Long userId);

       
               
      
                         
                         
                   
       
    List<Notification> getUserNotifications(Long userId, Integer limit);

       
              
      
                                 
                                         
                   
       
    boolean markAsRead(Long notificationId, Long userId);

       
                
      
                         
                    
       
    int markAllAsRead(Long userId);

       
           
      
                                 
                                         
                   
       
    boolean deleteNotification(Long notificationId, Long userId);

       
                     
      
                            
                   
       
    int deleteReadNotifications(Long userId);

                                                         

       
                   
      
                                     
                                     
                                
                                
                               
                               
                               
                               
       
    void sendRewardNotification(Long creatorId, Long senderId, String senderName, String senderAvatar,
                               BigDecimal amount, Long resourceId, String resourceType, String resourceName);

    void sendRewardNotificationOnce(Long rewardId, Long creatorId, Long senderId,
                                    String senderName, String senderAvatar, BigDecimal amount,
                                    Long resourceId, String resourceType);

    void sendModerationResultNotificationOnce(Long userId, String contentType, String contentName,
                                              Long relatedId, boolean approved, String reason,
                                              String businessKey);

    void sendCreatorEligibilityNotificationOnce(Long userId, String status, String reason,
                                                String businessKey);

       
                   
      
                                       
                                  
                                  
                                 
                                 
                                 
                                 
       
    void sendSubscribeNotification(Long creatorId, Long subscriberId, String subscriberName,
                                  Long playlistId, String playlistName, String subscribeType, BigDecimal amount);

       
                   
      
                                     
                                   
                                   
                                  
                                  
                                  
       
    void sendLikeNotification(Long resourceOwnerId, Long likerId, String likerName,
                             String resourceType, Long resourceId, String resourceName);

    void sendCommentNotification(Long recipientId, Long senderId, String senderName,
                                 Long commentId, String resourceType, Long resourceId,
                                 String resourceName, boolean reply);

    void sendCommentLikeNotification(Long recipientId, Long likerId, String likerName,
                                     Long commentId, String resourceType, Long resourceId,
                                     String resourceName);

       
                   
      
                                
                               
                                
       
    void sendFollowNotification(Long followeeId, Long followerId, String followerName);

       
               
      
                             
                            
                            
       
    void sendRevenueNotification(Long creatorId, BigDecimal amount, String source);

       
               
      
                               
                               
                               
       
    void sendSubscriptionExpiringNotification(Long userId, String playlistName, int daysLeft);

       
               
      
                                
                               
                               
                                
       
    void sendContentUpdateNotification(Long subscriberId, Long playlistId, String playlistName, int songCount);

       
                  
      
                           
                         
       
    List<NotificationDetailVO> getNotificationGroupDetails(String groupId, Long recipientId);

       
                 
      
                              
                             
                                                      
                             
                                      
       
    void sendReportResultNotification(Long reporterId, Long reportId, String result, String reason, Integer reward);

       
                 
      
                                
                              
                                                       
                               
       
    void sendFeedbackResultNotification(Long feedbackerId, Long feedbackId, String result, String reply);

                                                         

       
                    
      
                                 
                                 
                                
                   
       
    boolean recallNotificationByAdmin(Long notificationId, Long adminId, String reason);

       
                
      
                         
                                         
                   
       
    int batchRecallUserNotifications(Long userId, String type);

       
                       
      
                         
                         
                   
       
    int batchRecallByType(String type, String reason);

       
             
      
                                     
                                   
                                  
                   
       
    boolean revokeLikeNotification(Long resourceOwnerId, Long likerId, Long relatedId);

       
               
                                     
      
                            
                   
       
    int revokeCommentNotifications(Long commentId);

       
             
                   
      
                               
                              
                   
       
    boolean revokeFollowNotification(Long followeeId, Long followerId);

       
                           
      
                                 
                                 
                   
       
    int revokeNotificationsBySender(Long userId, Long senderId);
}
