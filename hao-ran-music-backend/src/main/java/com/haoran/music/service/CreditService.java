




package com.haoran.music.service;

import java.util.Map;





public interface CreditService {







    Integer getUserCredit(Long userId);







    Boolean initUserCredit(Long userId);











    Integer addCreditRecord(Long userId, String creditType, Integer score,
                           String reason, Long operatorId);








    boolean hasReceivedTodayReward(Long userId, String creditType);








    Integer getTodayRewardCount(Long userId, String creditType);










    Map<String, Object> getCreditRecords(Long userId, String creditType,
                                        Integer page, Integer size);










    Integer updateReportCredit(Long userId, Long reportId, Integer score, String reason);










    Integer updateRefundCredit(Long userId, Long refundId, Integer score, String reason);






    Integer resetAllCredits();







    String getCreditLevel(Integer credit);







    Boolean isBelowThreshold(Long userId);






    String getCurrentPeriod();






    java.time.LocalDateTime getNextResetTime();








    Boolean handleCreditDeduction(Long userId, String creditType);











    Boolean adjustCredit(Long userId, String creditType, Integer score,
                        String reason, Long operatorId);






    Map<String, Object> getCreditStatistics();
}
