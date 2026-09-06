package com.haoran.music.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;












public interface UserVipRefundService {









    Integer processVipRefund(Long userId, Long orderId, BigDecimal refundAmount);










    Integer calculateDeductDays(Long orderId, BigDecimal refundAmount);








    LocalDateTime deductVipDays(Long userId, Integer deductDays);







    List<Object> getUserVipPurchases(Long userId);







    Object getUserVipInfo(Long userId);







    boolean isUserVip(Long userId);







    Integer getVipRemainingDays(Long userId);










    void recordVipChange(Long userId, String changeType, Integer changeDays,
                        Long relatedId, String reason);
}
