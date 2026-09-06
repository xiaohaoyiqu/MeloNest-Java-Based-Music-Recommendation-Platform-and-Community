




package com.haoran.music.service;

import java.util.Map;





public interface PaymentCodeService {










    Map<String, Object> uploadPaymentCode(Long userId, String paymentType,
                                         String imageUrl, Long operatorId);








    Map<String, Object> getPaymentConfig(Long userId, String paymentType);







    Map<String, Object> getPlatformPaymentCode(String paymentType);








    Map<String, Object> getCreatorPaymentCode(Long creatorId, String paymentType);







    Map<String, Object> scanPaymentCodes(String scanPath);








    Boolean verifyCode(Long configId, String verifyCode);








    Map<String, Object> refreshVerifyCode(Long configId, Long operatorId);









    Boolean disablePaymentCode(Long configId, Long operatorId, String reason);








    Boolean enablePaymentCode(Long configId, Long operatorId);







    Boolean resetDailyLimit(Long configId);








    Boolean checkDailyLimit(Long configId, java.math.BigDecimal amount);








    Boolean addTodayReceived(Long configId, java.math.BigDecimal amount);









    Map<String, Object> getPaymentCodeLogs(Long configId, Integer page, Integer size);








    String compositeVerifyCode(String originalUrl, String verifyCode);







    String calculateMd5(String imageUrl);
}
