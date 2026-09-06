package com.haoran.music.service;

import com.haoran.music.vo.push.PushNotificationVO;
import com.haoran.music.dto.curated.CuratedNewsRequest;
import com.haoran.music.entity.PushNotification;

import java.util.List;

   
                      
                        
   
public interface PushNotificationService {

       
                    
      
                     
       
    List<PushNotificationVO> getActivePushNotifications();

       
                  
      
                       
                     
       
    List<PushNotificationVO> getPushNotificationsByType(String type);

       
                    
      
                         
                      
       
    String createPushNotification(PushNotificationVO push);

       
                    
      
                         
                   
       
    boolean deletePushNotification(String pushId);

       
                  
       
    List<PushNotification> listAdminNews();

       
               
       
    String createAdminNews(CuratedNewsRequest request, Long operatorId);

       
               
       
    boolean updateAdminNews(String pushId, CuratedNewsRequest request, Long operatorId);

       
            
       
    boolean reviewAdminNews(String pushId, boolean approved, String remark, Long reviewerId);

       
            
       
    boolean disableAdminNews(String pushId, Long operatorId);
}
