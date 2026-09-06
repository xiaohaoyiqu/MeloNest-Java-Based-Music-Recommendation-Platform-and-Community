   
                      
                        
   

package com.haoran.music.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.common.result.Result;
import com.haoran.music.entity.ResourcePurchase;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

   
           
                    
   
public interface ResourcePurchaseService {

       
             
      
                         
                             
                                              
                           
                                               
                         
                     
       
    Result<Long> createPurchase(Long userId, Long resourceId, String resourceType,
                                Long ownerId, String ownerType, BigDecimal amount);

       
                    
      
                               
                                   
                   
       
    Result<Void> confirmPurchase(Long purchaseId, Long paymentOrderId);

       
         
      
                               
                         
                   
       
    Result<Void> refundPurchase(Long purchaseId, String reason);

       
             
      
                               
                     
       
    Result<ResourcePurchase> getPurchase(Long purchaseId);

       
                  
      
                         
                             
                               
                    
       
    Boolean hasPurchased(Long userId, Long resourceId, String resourceType);

       
                
      
                         
                          
                     
       
    Result<IPage<ResourcePurchase>> getUserPurchases(Long userId, PageQuery query);

       
                 
      
                           
                          
                     
       
    Result<IPage<ResourcePurchase>> getCreatorSales(Long ownerId, PageQuery query);

       
                
      
                           
                     
       
    Result<Map<String, Object>> getCreatorEarnings(Long ownerId);

       
               
      
                            
                          
                     
       
    Result<Map<String, Object>> getPlatformEarnings(String startDate, String endDate);

       
               
      
                             
                               
                     
       
    Result<Map<String, Object>> getResourceSales(Long resourceId, String resourceType);
}
