




package com.haoran.music.service;

import java.math.BigDecimal;
import java.util.Map;





public interface RefundService {











    Map<String, Object> applyRefund(Long userId, Long orderId, String orderType,
                                    String reason, String description);










    Map<String, Object> getMyRefundRecords(Long userId, String status,
                                          Integer page, Integer size);








    Map<String, Object> getPendingRefunds(Integer page, Integer size);












    Map<String, Object> reviewRefund(Long refundId, Long reviewerId,
                                    Boolean approved, String reviewReason,
                                    Boolean isUnreasonable, String unreasonableReason);








    Boolean completeRefund(Long refundId, Long operatorId);







    Map<String, Object> getRefundDetail(Long refundId);








    Map<String, Object> getRefundDetail(Long refundId, Long viewerId);








    Boolean cancelRefund(Long refundId, Long userId);








    Map<String, Object> checkRefundEligible(Long userId, Long orderId);







    Integer getMonthRefundCount(Long userId);







    Integer getRefundCredit(Long userId);










    Integer deductRefundCredit(Long userId, Long refundId, Integer score, String reason);






    Integer resetRefundCredit();







    Boolean checkRemoveCreator(Long userId);







    Map<String, Object> getRefundStatistics(Long userId);












    Map<String, Object> approveRefund(Long refundId, Long adminId, String notes);










    Map<String, Object> rejectRefund(Long refundId, Long adminId, String reason);








    Map<String, Object> getRefundInfo(Long refundId);










    Map<String, Object> getUserRefunds(Long userId, Integer page, Integer size);
}
