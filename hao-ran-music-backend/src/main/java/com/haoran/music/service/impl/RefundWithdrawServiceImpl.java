package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.entity.CreatorDebt;
import com.haoran.music.entity.WithdrawApply;
import com.haoran.music.entity.WithdrawFreeze;
import com.haoran.music.mapper.CreatorDebtMapper;
import com.haoran.music.mapper.WithdrawApplyMapper;
import com.haoran.music.mapper.WithdrawFreezeMapper;
import com.haoran.music.service.RefundWithdrawService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;






@Slf4j
@Service
public class RefundWithdrawServiceImpl implements RefundWithdrawService {

    private final WithdrawApplyMapper withdrawApplyMapper;
    private final WithdrawFreezeMapper withdrawFreezeMapper;
    private final CreatorDebtMapper creatorDebtMapper;

    public RefundWithdrawServiceImpl(WithdrawApplyMapper withdrawApplyMapper,
                                     WithdrawFreezeMapper withdrawFreezeMapper,
                                     CreatorDebtMapper creatorDebtMapper) {
        this.withdrawApplyMapper = withdrawApplyMapper;
        this.withdrawFreezeMapper = withdrawFreezeMapper;
        this.creatorDebtMapper = creatorDebtMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer freezeCreatorWithdrawals(Long creatorId, Long refundId, BigDecimal refundAmount) {
        if (ObjectUtils.isEmpty(creatorId) || ObjectUtils.isEmpty(refundId)) {
            return 0;
        }


        LambdaQueryWrapper<WithdrawApply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WithdrawApply::getCreatorId, creatorId)
                .in(WithdrawApply::getStatus, "pending", "processing");

        List<WithdrawApply> pendingWithdraws = withdrawApplyMapper.selectList(wrapper);

        if (pendingWithdraws.isEmpty()) {
            log.info("创作者无处理中的提现申请: creatorId={}", creatorId);
            return 0;
        }

        int frozenCount = 0;
        for (WithdrawApply withdraw : pendingWithdraws) {

            WithdrawFreeze freeze = new WithdrawFreeze();
            freeze.setWithdrawId(withdraw.getId());
            freeze.setCreatorId(creatorId);
            freeze.setRefundId(refundId);
            freeze.setFreezeAmount(refundAmount);
            freeze.setStatus("frozen");
            freeze.setReason("用户退款，冻结提现申请");
            freeze.setCreateTime(LocalDateTime.now());
            freeze.setDeleted(CommonConstants.NOT_DELETED);

            withdrawFreezeMapper.insert(freeze);


            withdraw.setStatus("frozen");
            withdrawApplyMapper.updateById(withdraw);

            frozenCount++;

            log.info("冻结提现申请: withdrawId={}, refundId={}, amount={}",
                    withdraw.getId(), refundId, refundAmount);
        }

        log.info("冻结创作者提现申请完成: creatorId={}, refundId={}, frozenCount={}",
                creatorId, refundId, frozenCount);

        return frozenCount;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long recordWithdrawAsDebt(Long creatorId, Long refundId, BigDecimal refundAmount) {
        if (ObjectUtils.isEmpty(creatorId) || ObjectUtils.isEmpty(refundId)) {
            return null;
        }


        WithdrawApply completedWithdraw = getLastCompletedWithdrawal(creatorId);
        if (completedWithdraw == null) {
            log.warn("创作者无已完成的提现记录: creatorId={}", creatorId);
            return null;
        }


        CreatorDebt debt = new CreatorDebt();
        debt.setCreatorId(creatorId);
        debt.setRefundId(refundId);
        debt.setWithdrawId(completedWithdraw.getId());
        debt.setDebtAmount(refundAmount);
        debt.setPaidAmount(BigDecimal.ZERO);
        debt.setRemainingAmount(refundAmount);
        debt.setStatus("pending");
        debt.setReason("用户退款需要追回已提现金额");
        debt.setCreateTime(LocalDateTime.now());
        debt.setDeleted(CommonConstants.NOT_DELETED);

        creatorDebtMapper.insert(debt);

        log.info("记录创作者欠款: creatorId={}, refundId={}, withdrawId={}, amount={}",
                creatorId, refundId, completedWithdraw.getId(), refundAmount);

        return debt.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer unfreezeCreatorWithdrawals(Long refundId) {
        if (ObjectUtils.isEmpty(refundId)) {
            return 0;
        }


        LambdaQueryWrapper<WithdrawFreeze> freezeWrapper = new LambdaQueryWrapper<>();
        freezeWrapper.eq(WithdrawFreeze::getRefundId, refundId)
                .eq(WithdrawFreeze::getStatus, "frozen")
                .eq(WithdrawFreeze::getDeleted, CommonConstants.NOT_DELETED);

        List<WithdrawFreeze> freezes = withdrawFreezeMapper.selectList(freezeWrapper);

        if (freezes.isEmpty()) {
            log.info("无需要解冻的提现申请: refundId={}", refundId);
            return 0;
        }

        int unfrozenCount = 0;
        for (WithdrawFreeze freeze : freezes) {

            freeze.setStatus("unfrozen");
            freeze.setUpdateTime(LocalDateTime.now());
            withdrawFreezeMapper.updateById(freeze);


            WithdrawApply withdraw = withdrawApplyMapper.selectById(freeze.getWithdrawId());
            if (withdraw != null && "frozen".equals(withdraw.getStatus())) {
                withdraw.setStatus("pending");
                withdrawApplyMapper.updateById(withdraw);
            }

            unfrozenCount++;

            log.info("解冻提现申请: withdrawId={}, refundId={}",
                    freeze.getWithdrawId(), refundId);
        }

        log.info("解冻提现申请完成: refundId={}, unfrozenCount={}", refundId, unfrozenCount);

        return unfrozenCount;
    }

    @Override
    public List<WithdrawApply> getFrozenWithdrawals(Long creatorId) {
        if (ObjectUtils.isEmpty(creatorId)) {
            return new ArrayList<>();
        }


        LambdaQueryWrapper<WithdrawFreeze> freezeWrapper = new LambdaQueryWrapper<>();
        freezeWrapper.eq(WithdrawFreeze::getCreatorId, creatorId)
                .eq(WithdrawFreeze::getStatus, "frozen")
                .eq(WithdrawFreeze::getDeleted, CommonConstants.NOT_DELETED);

        List<WithdrawFreeze> freezes = withdrawFreezeMapper.selectList(freezeWrapper);

        if (freezes.isEmpty()) {
            return new ArrayList<>();
        }


        List<Long> withdrawIds = freezes.stream()
                .map(WithdrawFreeze::getWithdrawId)
                .collect(Collectors.toList());

        return withdrawIds.isEmpty() ? new ArrayList<>() :
                withdrawApplyMapper.selectBatchIds(withdrawIds);
    }

    @Override
    public boolean hasPendingWithdrawals(Long creatorId) {
        if (ObjectUtils.isEmpty(creatorId)) {
            return false;
        }

        LambdaQueryWrapper<WithdrawApply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WithdrawApply::getCreatorId, creatorId)
                .in(WithdrawApply::getStatus, "pending", "processing");

        Long count = withdrawApplyMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    @Override
    public boolean hasCompletedWithdrawals(Long creatorId) {
        if (ObjectUtils.isEmpty(creatorId)) {
            return false;
        }

        LambdaQueryWrapper<WithdrawApply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WithdrawApply::getCreatorId, creatorId)
                .eq(WithdrawApply::getStatus, "completed");

        Long count = withdrawApplyMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    @Override
    public WithdrawApply getLastCompletedWithdrawal(Long creatorId) {
        if (ObjectUtils.isEmpty(creatorId)) {
            return null;
        }

        LambdaQueryWrapper<WithdrawApply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WithdrawApply::getCreatorId, creatorId)
                .eq(WithdrawApply::getStatus, "completed")
                .orderByDesc(WithdrawApply::getCompletedTime)
                .last("LIMIT 1");

        return withdrawApplyMapper.selectOne(wrapper);
    }

    @Override
    public boolean isWithdrawAmountSufficient(Long creatorId, BigDecimal refundAmount) {
        if (ObjectUtils.isEmpty(creatorId) || refundAmount == null) {
            return false;
        }


        WithdrawApply completedWithdraw = getLastCompletedWithdrawal(creatorId);
        if (completedWithdraw == null) {
            return false;
        }


        BigDecimal withdrawAmount = completedWithdraw.getAmount();
        return withdrawAmount != null && withdrawAmount.compareTo(refundAmount) >= 0;
    }
}


