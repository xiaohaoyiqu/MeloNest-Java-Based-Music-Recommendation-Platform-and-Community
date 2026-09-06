   
                      
   
package com.haoran.music.service;

import com.haoran.music.entity.User;

import java.math.BigDecimal;

   
                                                                               
   
public interface CreatorEligibilityService {

    boolean isEligible(Long userId);

    void requireEligible(Long userId, String action);

       
                                                                                       
       
    User activate(Long userId, String creatorType, Long operatorId, String reason, BigDecimal feeRate);

       
                                                                
       
    User changeStatus(Long userId, String status, Long operatorId, String reason);

       
                                                                      
       
    User remove(Long userId, Long operatorId, String reason);
}
