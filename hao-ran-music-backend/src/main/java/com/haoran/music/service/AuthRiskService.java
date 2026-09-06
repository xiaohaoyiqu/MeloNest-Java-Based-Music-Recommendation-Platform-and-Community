   
                      
                                            
   
package com.haoran.music.service;

import java.util.Map;

   
                                                                                             
   
public interface AuthRiskService {

    boolean needCaptcha(String account, String clientIp, String userAgent);

    boolean isCaptchaEnforced();

    boolean isLoginTemporarilyBlocked(String account);

    void recordLoginFailure(String account, String clientIp);

    void recordLoginSuccess(Long userId, String account, String clientIp, String userAgent);

    void recordLogout(Long userId, String clientIp);

    Map<String, Object> getRiskStatus(String account, String clientIp, String userAgent);
}
