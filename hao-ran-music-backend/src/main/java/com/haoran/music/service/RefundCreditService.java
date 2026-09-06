package com.haoran.music.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.haoran.music.entity.RefundCreditLog;

import java.time.LocalDateTime;
import java.util.List;

   
              
  
        
                       
                          
               
              
  
                      
   
public interface RefundCreditService extends IService<RefundCreditLog> {

       
                
      
                         
                           
                                     
                         
                     
       
    Integer recordRefundCredit(Long userId, Long refundId, Integer score, String reason);

       
                  
      
                         
                    
       
    Integer getCurrentRefundCredit(Long userId);

       
                       
      
                         
                                    
                      
       
    List<RefundCreditLog> getRefundCreditByPeriod(Long userId, String period);

       
                  
      
                         
                          
                      
       
    List<RefundCreditLog> getRefundCreditHistory(Long userId, Integer limit);

       
               
      
                                
       
    String getCurrentPeriod();

       
                 
      
                                   
       
    LocalDateTime getCurrentPeriodStartTime();

       
                 
      
                                 
       
    LocalDateTime getCurrentPeriodEndTime();

       
                      
      
                         
                              
       
    Integer getCurrentMonthCreditChange(Long userId);

       
                   
      
                         
                                   
       
    boolean hasDeductThisMonth(Long userId);

       
                          
      
                         
                       
       
    Integer resetRefundCredit(Long userId);
}
