package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.entity.CreatorDebt;
import com.haoran.music.entity.CreatorEarnings;
import com.haoran.music.entity.User;
import com.haoran.music.mapper.CreatorDebtMapper;
import com.haoran.music.mapper.CreatorEarningsMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.CreatorEarningsDeductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;






@Slf4j
@Service
public class CreatorEarningsDeductServiceImpl implements CreatorEarningsDeductService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_PARTIAL = "partial";

    private final CreatorEarningsMapper creatorEarningsMapper;
    private final CreatorDebtMapper creatorDebtMapper;
    private final UserMapper userMapper;

    public CreatorEarningsDeductServiceImpl(CreatorEarningsMapper creatorEarningsMapper,
                                            CreatorDebtMapper creatorDebtMapper,
                                            UserMapper userMapper) {
        this.creatorEarningsMapper = creatorEarningsMapper;
        this.creatorDebtMapper = creatorDebtMapper;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal deductCreatorEarnings(Long creatorId, Long refundId, BigDecimal refundAmount,
                                            Long workId, String workType) {
        if (ObjectUtils.isEmpty(creatorId) || refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal normalizedRefund = normalizeMoney(refundAmount);
        BigDecimal availableEarnings = getAvailableEarnings(creatorId);
        BigDecimal actualDeduct = availableEarnings.min(normalizedRefund);
        BigDecimal debtAmount = normalizedRefund.subtract(actualDeduct);


        recordEarningsDeduction(creatorId, refundId, normalizedRefund.negate(), workId, workType);
        applyRefundToCreatorSummary(creatorId, normalizedRefund, actualDeduct);

        if (debtAmount.compareTo(BigDecimal.ZERO) > 0) {
            recordCreatorDebt(creatorId, refundId, null, debtAmount, "退款金额超过当前可用收益");
            log.info("创作者收益不足，已记录债务明细: creatorId={}, refundId={}, debtAmount={}",
                    creatorId, refundId, debtAmount);
        }

        log.info("扣除创作者收益: creatorId={}, refundId={}, refundAmount={}, actualDeduct={}, debtAmount={}",
                creatorId, refundId, normalizedRefund, actualDeduct, debtAmount);

        return actualDeduct;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long recordEarningsDeduction(Long creatorId, Long refundId, BigDecimal deductAmount,
                                        Long workId, String workType) {
        if (ObjectUtils.isEmpty(creatorId) || deductAmount == null || deductAmount.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        CreatorEarnings earnings = new CreatorEarnings();
        earnings.setUserId(creatorId);
        earnings.setWorkId(workId != null ? workId : 0L);
        earnings.setWorkType(workType != null ? workType : "refund");
        earnings.setEarningsType("refund");
        earnings.setEarningsAmount(toCents(deductAmount));
        earnings.setCreateTime(LocalDateTime.now());
        earnings.setDeleted(CommonConstants.NOT_DELETED);

        creatorEarningsMapper.insert(earnings);

        log.info("记录收益扣除: creatorId={}, refundId={}, deductAmount={}",
                creatorId, refundId, deductAmount);

        return earnings.getId();
    }

    @Override
    public BigDecimal getTotalEarnings(Long creatorId) {
        if (ObjectUtils.isEmpty(creatorId)) {
            return BigDecimal.ZERO;
        }

        LambdaQueryWrapper<CreatorEarnings> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CreatorEarnings::getUserId, creatorId)
                .eq(CreatorEarnings::getDeleted, CommonConstants.NOT_DELETED);

        return sumEarnings(creatorEarningsMapper.selectList(wrapper));
    }

    @Override
    public BigDecimal getEarningsByType(Long creatorId, String earningsType) {
        if (ObjectUtils.isEmpty(creatorId) || ObjectUtils.isEmpty(earningsType)) {
            return BigDecimal.ZERO;
        }

        LambdaQueryWrapper<CreatorEarnings> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CreatorEarnings::getUserId, creatorId)
                .eq(CreatorEarnings::getEarningsType, earningsType)
                .eq(CreatorEarnings::getDeleted, CommonConstants.NOT_DELETED);

        return sumEarnings(creatorEarningsMapper.selectList(wrapper));
    }

    @Override
    public List<CreatorEarnings> getEarningsHistory(Long creatorId, Integer limit) {
        if (ObjectUtils.isEmpty(creatorId)) {
            return Collections.emptyList();
        }

        int actualLimit = limit != null && limit > 0 ? limit : 50;

        LambdaQueryWrapper<CreatorEarnings> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CreatorEarnings::getUserId, creatorId)
                .eq(CreatorEarnings::getDeleted, CommonConstants.NOT_DELETED)
                .orderByDesc(CreatorEarnings::getCreateTime)
                .last("LIMIT " + actualLimit);

        return creatorEarningsMapper.selectList(wrapper);
    }

    @Override
    public List<CreatorEarnings> getWorkEarnings(Long creatorId, Long workId) {
        if (ObjectUtils.isEmpty(creatorId) || ObjectUtils.isEmpty(workId)) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<CreatorEarnings> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CreatorEarnings::getUserId, creatorId)
                .eq(CreatorEarnings::getWorkId, workId)
                .eq(CreatorEarnings::getDeleted, CommonConstants.NOT_DELETED)
                .orderByDesc(CreatorEarnings::getCreateTime);

        return creatorEarningsMapper.selectList(wrapper);
    }

    @Override
    public boolean isEarningsSufficient(Long creatorId, BigDecimal amount) {
        if (ObjectUtils.isEmpty(creatorId) || amount == null) {
            return false;
        }

        return getAvailableEarnings(creatorId).compareTo(normalizeMoney(amount)) >= 0;
    }

    @Override
    public BigDecimal getAvailableEarnings(Long creatorId) {
        if (ObjectUtils.isEmpty(creatorId)) {
            return BigDecimal.ZERO;
        }

        User creator = userMapper.selectById(creatorId);
        if (creator == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal pendingEarnings = safeMoney(creator.getPendingEarnings());
        BigDecimal outstandingDebt = getOutstandingDebt(creatorId);
        BigDecimal available = pendingEarnings.subtract(outstandingDebt);
        return available.compareTo(BigDecimal.ZERO) > 0 ? normalizeMoney(available) : BigDecimal.ZERO;
    }

    private Long recordCreatorDebt(Long creatorId, Long refundId, Long withdrawId,
                                   BigDecimal debtAmount, String reason) {
        if (ObjectUtils.isEmpty(creatorId) || ObjectUtils.isEmpty(refundId)
                || debtAmount == null || debtAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        LambdaQueryWrapper<CreatorDebt> existingWrapper = new LambdaQueryWrapper<>();
        existingWrapper.eq(CreatorDebt::getCreatorId, creatorId)
                .eq(CreatorDebt::getRefundId, refundId)
                .eq(CreatorDebt::getDeleted, CommonConstants.NOT_DELETED)
                .last("LIMIT 1");
        CreatorDebt existing = creatorDebtMapper.selectOne(existingWrapper);
        if (existing != null) {
            return existing.getId();
        }

        CreatorDebt debt = new CreatorDebt();
        debt.setCreatorId(creatorId);
        debt.setRefundId(refundId);
        debt.setWithdrawId(withdrawId);
        debt.setDebtAmount(normalizeMoney(debtAmount));
        debt.setPaidAmount(BigDecimal.ZERO);
        debt.setRemainingAmount(normalizeMoney(debtAmount));
        debt.setStatus(STATUS_PENDING);
        debt.setReason(reason);
        debt.setDeleted(CommonConstants.NOT_DELETED);
        debt.setCreateTime(LocalDateTime.now());

        creatorDebtMapper.insert(debt);
        return debt.getId();
    }

    private BigDecimal getOutstandingDebt(Long creatorId) {
        LambdaQueryWrapper<CreatorDebt> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CreatorDebt::getCreatorId, creatorId)
                .in(CreatorDebt::getStatus, STATUS_PENDING, STATUS_PARTIAL)
                .eq(CreatorDebt::getDeleted, CommonConstants.NOT_DELETED);

        return creatorDebtMapper.selectList(wrapper).stream()
                .map(CreatorDebt::getRemainingAmount)
                .map(this::safeMoney)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void applyRefundToCreatorSummary(Long creatorId, BigDecimal refundAmount, BigDecimal actualDeduct) {
        User creator = userMapper.selectById(creatorId);
        if (creator == null) {
            return;
        }

        BigDecimal totalEarnings = safeMoney(creator.getTotalEarnings()).subtract(refundAmount);
        BigDecimal pendingEarnings = safeMoney(creator.getPendingEarnings()).subtract(actualDeduct);
        if (pendingEarnings.compareTo(BigDecimal.ZERO) < 0) {
            pendingEarnings = BigDecimal.ZERO;
        }

        creator.setTotalEarnings(normalizeMoney(totalEarnings));
        creator.setPendingEarnings(normalizeMoney(pendingEarnings));
        userMapper.updateById(creator);
    }

    private BigDecimal sumEarnings(List<CreatorEarnings> earningsList) {
        return earningsList.stream()
                .map(CreatorEarnings::getEarningsAmount)
                .map(this::fromCents)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Long toCents(BigDecimal amount) {
        return normalizeMoney(amount).multiply(ONE_HUNDRED).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    private BigDecimal fromCents(Long amount) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(amount).divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal safeMoney(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : normalizeMoney(amount);
    }

    private BigDecimal normalizeMoney(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
