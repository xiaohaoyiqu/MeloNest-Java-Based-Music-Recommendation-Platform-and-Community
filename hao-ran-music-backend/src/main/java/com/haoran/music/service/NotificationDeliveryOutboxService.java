package com.haoran.music.service;

import com.haoran.music.entity.Notification;

import java.util.List;
import java.util.Map;

   
                    
  
                      
   
public interface NotificationDeliveryOutboxService {

       
                           
      
                               
                                 
                     
       
    String record(Notification notification, boolean unreadDelta);

       
                   
      
                          
                      
       
    boolean dispatchEvent(String eventId);

       
                 
      
                        
                      
       
    int retryDueEvents(int limit);

       
                                
      
                          
                       
       
    boolean retryFailedEvent(String eventId);

       
                 
      
                           
       
    Map<String, Object> getStatusSummary();

       
                        
      
                        
                     
       
    List<Map<String, Object>> getRecentFailures(int limit);
}
