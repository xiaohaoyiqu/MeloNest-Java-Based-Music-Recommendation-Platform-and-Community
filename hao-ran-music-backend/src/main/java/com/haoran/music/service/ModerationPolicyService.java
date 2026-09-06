package com.haoran.music.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.haoran.music.entity.ModerationPolicy;

   
                      
                          
   
public interface ModerationPolicyService extends IService<ModerationPolicy> {

       
             
                             
                             
                                
                              
                                      
       
    void updatePolicy(String policyCode, String policyName, String policyContent,
                     String affectScope, Boolean reauditRequired);

       
             
                         
       
    void triggerReaudit(ModerationPolicy policy);
}
