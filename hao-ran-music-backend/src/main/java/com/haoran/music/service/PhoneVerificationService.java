



package com.haoran.music.service;

import java.util.Map;




public interface PhoneVerificationService {




    Map<String, Object> sendCode(String phone, String scene, String clientIp, String userAgent);




    void verifyCode(String phone, String scene, String code);
}