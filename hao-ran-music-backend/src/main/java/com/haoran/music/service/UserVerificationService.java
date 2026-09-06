




package com.haoran.music.service;

import com.haoran.music.common.result.Result;
import com.haoran.music.entity.UserVerification;





public interface UserVerificationService {









    Result<Void> sendVerificationCode(String target, Integer type, String clientIp);









    Result<Boolean> verifyCode(String target, Integer type, String code);









    Result<Boolean> verifyCodeByUserId(Long userId, Integer type, String code);







    void invalidateCode(String target, Integer type);




    void cleanExpiredCodes();








    Boolean canSendCode(String target, Integer type);









    java.util.List<UserVerification> getUserVerifications(Long userId, Integer type, Integer limit);
}
