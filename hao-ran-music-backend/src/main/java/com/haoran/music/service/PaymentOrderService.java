   
                      
                        
   

package com.haoran.music.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

   
           
                       
   
public interface PaymentOrderService {

       
             
      
                         
                                                                           
                             
                       
                                    
                             
                   
       
    Map<String, Object> createOrder(Long userId, String businessType, Long businessId,
                                    BigDecimal amount, Long payeeId, String userRemark);

    Map<String, Object> createOrder(Long userId, String businessType, Long businessId,
                                    BigDecimal amount, Long payeeId, String userRemark,
                                    String idempotencyKey);

       
              
      
                          
                             
       
    Map<String, Object> getOrderQrCode(Long orderId, Long userId);

       
             
      
                          
                              
                            
                   
       
    Map<String, Object> submitPayment(Long orderId, Long userId, String proofUrl, String verifyCode);

       
                                       
       
    Map<String, Object> bindPaymentProof(Long orderId, Long userId, String proofReference);

       
                                  
       
    String getPaymentProofReference(Long orderId, Long viewerId);

       
           
      
                          
                              
                           
                               
                   
       
    Map<String, Object> reviewOrder(Long orderId, Long reviewerId,
                                    Boolean approved, String reviewReason);

       
               
      
                         
                         
                                 
                     
                       
                   
       
    Map<String, Object> getMyOrders(Long userId, String status, String businessType,
                                    Integer page, Integer size);

       
                
      
                                         
                     
                       
                      
       
    Map<String, Object> getPendingOrders(Long payeeId, Integer page, Integer size);

       
                         
      
                     
                       
                      
       
    Map<String, Object> getPendingCompletionOrders(Integer page, Integer size);

       
           
      
                          
                               
                   
       
    Boolean cancelOrder(Long orderId, Long userId);

       
             
      
                      
       
    Integer handleExpiredOrders();

       
                 
      
                                             
                      
       
    Integer cleanOldCancelledOrders(LocalDateTime cutoffDate);

       
             
      
                          
                   
       
    Map<String, Object> getOrderDetail(Long orderId, Long userId);

       
                
      
                         
                   
       
    Map<String, Object> getOrderByNo(String orderNo, Long userId);

       
                
      
                          
                        
       
    Map<String, Object> getOrderStatus(Long orderId, Long userId);

       
                
      
                       
       
    Map<String, Object> getOrderStatistics();

       
                    
      
                          
                   
       
    Boolean completeOrder(Long orderId);

       
                                
      
                          
                                
                   
       
    Boolean retryOrderCompletion(Long orderId, Long operatorId);

       
                          
      
                                    
                                      
       
    PaymentCompletionRecoverySummary recoverPendingCompletions(String workerId);

       
           
      
                          
                         
                              
                   
       
    Boolean refundOrder(Long orderId, String reason, Long operatorId);
}
