   
                      
                                                        
   
package com.haoran.music.service;

import java.util.Map;

   
                                                                                        
   
public interface EmailVerificationService {

    Map<String, Object> sendCode(String email, String scene, String clientIp, String userAgent);

    void verifyCode(String email, String scene, String code);
}