package com.haoran.music.service;

import com.haoran.music.common.dto.PageResult;
import com.haoran.music.vo.user.PublicUserVO;
import com.haoran.music.vo.user.VerifiedInfoVO;

import java.util.Map;

   
                      
                         
   
public interface VerifiedService {

       
               
      
                         
                   
       
    VerifiedInfoVO getUserVerifiedInfo(Long userId);

       
             
      
                          
                          
                      
                        
                     
       
    PageResult<PublicUserVO> getVerifiedList(String type, String level, Integer page, Integer size);

       
             
      
                                 
                                
                                
                                
                                
       
    void reviewVerified(Long creatorId, Boolean approved, String verifiedType, String verifiedLevel,
                        String reason, Long operatorId);

       
               
      
                         
                         
       
    void updateVerifiedInfo(Long userId, Map<String, Object> params, Long operatorId);
}
