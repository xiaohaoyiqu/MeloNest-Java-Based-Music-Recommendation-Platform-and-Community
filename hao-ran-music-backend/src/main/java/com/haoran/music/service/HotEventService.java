   
                      
                        
   

package com.haoran.music.service;

import com.haoran.music.dto.curated.CuratedBannerRequest;
import com.haoran.music.entity.HotEvent;

import java.util.List;
import java.util.Map;

   
           
   
public interface HotEventService {

       
               
      
                        
                   
       
    List<Object> getFeaturedEvents(Integer limit);

       
             
      
                          
                   
  
    Map<String, Object> getEventDetail(Long eventId, String viewerKey);

       
              
      
                          
                   
       
    Boolean incrementViewCount(Long eventId);

       
                  
       
    List<HotEvent> listAdminFeaturedEvents();

       
               
       
    Long createAdminFeaturedEvent(CuratedBannerRequest request, Long operatorId);

       
               
       
    boolean updateAdminFeaturedEvent(Long eventId, CuratedBannerRequest request, Long operatorId);

       
            
       
    boolean reviewAdminFeaturedEvent(Long eventId, boolean approved, String remark, Long reviewerId);

       
            
       
    boolean disableAdminFeaturedEvent(Long eventId, Long operatorId);
}
