package com.haoran.music.service;

import com.haoran.music.entity.WithdrawApply;

import java.math.BigDecimal;
import java.util.List;












public interface RefundWithdrawService {












    Integer freezeCreatorWithdrawals(Long creatorId, Long refundId, BigDecimal refundAmount);











    Long recordWithdrawAsDebt(Long creatorId, Long refundId, BigDecimal refundAmount);









    Integer unfreezeCreatorWithdrawals(Long refundId);







    List<WithdrawApply> getFrozenWithdrawals(Long creatorId);







    boolean hasPendingWithdrawals(Long creatorId);







    boolean hasCompletedWithdrawals(Long creatorId);







    WithdrawApply getLastCompletedWithdrawal(Long creatorId);








    boolean isWithdrawAmountSufficient(Long creatorId, BigDecimal refundAmount);
}
