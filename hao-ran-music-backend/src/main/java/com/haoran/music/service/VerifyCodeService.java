



package com.haoran.music.service;

import java.util.Map;








public interface VerifyCodeService {









    Map<String, Object> generateVerifyCode(String type, String target, String scene);










    boolean verifyCode(String type, String target, String scene, String code);








    boolean needVerifyCode(String target, String scene);







    void requireVerifyCode(String target, String scene);







    void clearVerifyCodeRequirement(String target, String scene);








    Map<String, Object> generateImageCode(String target, String scene);








    Map<String, Object> generateSlideCode(String target, String scene);
}
