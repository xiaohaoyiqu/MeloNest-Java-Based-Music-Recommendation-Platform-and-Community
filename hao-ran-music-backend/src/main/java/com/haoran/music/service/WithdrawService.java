   
                      
                      
   

package com.haoran.music.service;

import java.math.BigDecimal;
import java.util.Map;

   
         
                     
   
public interface WithdrawService {

       
           
      
                             
                         
                                                             
                                  
                                
                   
       
    Map<String, Object> applyWithdraw(Long creatorId, BigDecimal amount,
                                      String withdrawType, String withdrawAccount,
                                      String withdrawName);

       
               
      
                             
                         
                     
                       
                     
       
    Map<String, Object> getMyWithdrawRecords(Long creatorId, String status,
                                             Integer page, Integer size);

       
                     
      
                     
                       
                    
       
    Map<String, Object> getPendingWithdraws(Integer page, Integer size);

       
             
      
                               
                              
                           
                               
                   
       
    Map<String, Object> reviewWithdraw(Long withdrawId, Long reviewerId,
                                      Boolean approved, String reviewReason);

       
             
      
                               
                                 
                              
                   
       
    Boolean completeWithdraw(Long withdrawId, String transactionId, Long operatorId);

       
             
      
                               
                   
       
    Map<String, Object> getWithdrawDetail(Long withdrawId);

       
             
      
                               
                               
                   
       
    Map<String, Object> getWithdrawDetail(Long withdrawId, Long viewerId);

       
                 
      
                             
                    
       
    BigDecimal getAvailableAmount(Long creatorId);

       
                 
      
                             
                         
                   
       
    Boolean checkWithdrawCondition(Long creatorId, BigDecimal amount);

       
                       
      
                               
                           
                               
                   
       
    Boolean freezeWithdrawAmount(Long withdrawId, Long refundId, java.math.BigDecimal freezeAmount);

       
                    
      
                             
                         
                   
       
    Boolean handleWithdrawFreeze(Long freezeId, Boolean deduct);

       
                     
      
                             
                           
                               
                             
                   
       
    Boolean recordCreatorDebt(Long creatorId, Long refundId, Long withdrawId,
                             java.math.BigDecimal debtAmount);

       
               
      
                             
                   
       
    BigDecimal deductDebtFromEarnings(Long creatorId);

       
             
      
                             
                                
       
    Map<String, Object> getWithdrawStatistics(Long creatorId);
}
