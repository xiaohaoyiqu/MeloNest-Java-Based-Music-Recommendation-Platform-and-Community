   
                      
                        
   

package com.haoran.music.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.haoran.music.entity.MarketplaceItem;

import java.util.Map;
import java.util.List;

   
           
   
public interface MarketplaceService extends IService<MarketplaceItem> {

       
               
      
                                                         
                                                              
                                                             
                                               
                                            
                            
                              
                   
       
    IPage<Object> getItems(String category, String condition, String sortBy, String keyword,
                          Long currentUserId, Integer page, Integer size);

       
             
      
                                 
                                             
                   
       
    Map<String, Object> getItemDetail(Long itemId, Long currentUserId, String viewerKey);

       
           
      
                                 
                                 
                               
                               
                               
                               
                               
                               
                                   
                                   
                                   
                                   
                                
                                 
                   
       
    Long createItem(Long sellerId, String title, String category, String condition,
                   java.math.BigDecimal price, java.math.BigDecimal originalPrice,
                   String description, String images, String resourceType,
                   Long resourceId, String resourceName, String resourceCover,
                   String location, String deliveryMethod);

       
             
      
                            
                            
                          
                   
       
    Boolean updateItemStatus(Long itemId, Long sellerId, String status);

       
           
      
                            
                            
                   
       
    Boolean deleteItem(Long itemId, Long sellerId);

       
           
      
                         
                         
                   
       
    Boolean favoriteItem(Long itemId, Long userId);

       
             
      
                         
                         
                   
       
    Boolean unfavoriteItem(Long itemId, Long userId);

       
               
      
                            
                            
                          
                            
                   
       
    IPage<Object> getMyItems(Long sellerId, String status, Integer page, Integer size);

       
                 
      
                         
                       
                         
                   
       
    IPage<Object> getMyFavorites(Long userId, Integer page, Integer size);

                                
    List<Object> getPublicSellerItems(Long sellerId, Long viewerId, Integer limit);

       
              
      
                         
       
    void incrementViewCount(Long itemId);

       
                   
      
                         
                         
                    
       
    Boolean isFavorited(Long itemId, Long userId);

       
                    
      
                                 
                                 
                                 
                               
                               
                               
                               
                               
                               
                                   
                                   
                                   
                                   
                                
                                 
                   
       
    Boolean updateItem(Long itemId, Long sellerId, String title, String category, String condition,
                       java.math.BigDecimal price, java.math.BigDecimal originalPrice,
                       String description, String images, String resourceType,
                       Long resourceId, String resourceName, String resourceCover,
                       String location, String deliveryMethod);

       
                       
      
                         
                             
                   
       
    java.util.Map<String, Object> getOperationStats(Long userId, Integer hours);
}
