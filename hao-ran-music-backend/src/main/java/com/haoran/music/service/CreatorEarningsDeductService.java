package com.haoran.music.service;

import com.haoran.music.entity.CreatorEarnings;

import java.math.BigDecimal;
import java.util.List;












public interface CreatorEarningsDeductService {











    BigDecimal deductCreatorEarnings(Long creatorId, Long refundId, BigDecimal refundAmount,
                                     Long workId, String workType);











    Long recordEarningsDeduction(Long creatorId, Long refundId, BigDecimal deductAmount,
                                  Long workId, String workType);







    BigDecimal getTotalEarnings(Long creatorId);








    BigDecimal getEarningsByType(Long creatorId, String earningsType);








    List<CreatorEarnings> getEarningsHistory(Long creatorId, Integer limit);








    List<CreatorEarnings> getWorkEarnings(Long creatorId, Long workId);








    boolean isEarningsSufficient(Long creatorId, BigDecimal amount);







    BigDecimal getAvailableEarnings(Long creatorId);
}
