




package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.haoran.music.common.config.PaymentConfig;
import com.haoran.music.common.context.UserContext;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.entity.*;
import com.haoran.music.mapper.*;
import com.haoran.music.service.PermissionService;
import com.haoran.music.service.WithdrawService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;




@Slf4j
@Service
public class WithdrawServiceImpl implements WithdrawService {

    @javax.annotation.Resource
    private com.haoran.music.service.CreatorEligibilityService creatorEligibilityService;

    private final WithdrawApplyMapper withdrawApplyMapper;
    private final UserMapper userMapper;
    private final CreatorDebtMapper creatorDebtMapper;
    private final WithdrawFreezeMapper withdrawFreezeMapper;
    private final PaymentConfig paymentConfig;
    private final PermissionService permissionService;

    public WithdrawServiceImpl(WithdrawApplyMapper withdrawApplyMapper,
                              UserMapper userMapper,
                              CreatorDebtMapper creatorDebtMapper,
                              WithdrawFreezeMapper withdrawFreezeMapper,
                              PaymentConfig paymentConfig,
                              PermissionService permissionService) {
        this.withdrawApplyMapper = withdrawApplyMapper;
        this.userMapper = userMapper;
        this.creatorDebtMapper = creatorDebtMapper;
        this.withdrawFreezeMapper = withdrawFreezeMapper;
        this.paymentConfig = paymentConfig;
        this.permissionService = permissionService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> applyWithdraw(Long creatorId, BigDecimal amount,
                                             String withdrawType, String withdrawAccount,
                                             String withdrawName) {

        creatorEligibilityService.requireEligible(creatorId, "申请提现");
        User creator = userMapper.selectByIdForUpdate(creatorId);
        if (creator == null) {
            throw new BusinessException("创作者不存在");
        }


        if (!checkWithdrawCondition(creator, amount)) {
            throw new BusinessException("不满足提现条件");
        }


        if (!isValidWithdrawType(withdrawType)) {
            throw new BusinessException("不支持的提现类型");
        }


        WithdrawApply apply = new WithdrawApply();
        apply.setCreatorId(creatorId);
        apply.setAmount(amount);
        apply.setWithdrawType(withdrawType);
        apply.setWithdrawAccount(withdrawAccount);
        apply.setWithdrawName(withdrawName);
        apply.setStatus("pending");

        if (withdrawApplyMapper.insert(apply) != 1) {
            throw new BusinessException("提现申请创建失败");
        }


        BigDecimal pendingEarnings = creator.getPendingEarnings() != null ?
                creator.getPendingEarnings() : BigDecimal.ZERO;
        creator.setPendingEarnings(pendingEarnings.subtract(amount));
        if (userMapper.updateById(creator) != 1) {
            throw new BusinessException("提现余额冻结失败");
        }

        log.info("event=withdraw_requested creatorId={}", creatorId);

        Map<String, Object> result = new HashMap<>();
        result.put("withdrawId", apply.getId());
        result.put("amount", amount);
        result.put("status", "pending");
        result.put("message", "提现申请已提交，等待审核");

        return result;
    }

    @Override
    public Map<String, Object> getMyWithdrawRecords(Long creatorId, String status,
                                                   Integer page, Integer size) {
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        Page<WithdrawApply> pageParam = new Page<>(safePage, safeSize);

        LambdaQueryWrapper<WithdrawApply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WithdrawApply::getCreatorId, creatorId);

        if (ObjectUtils.isNotEmpty(status)) {
            wrapper.eq(WithdrawApply::getStatus, status);
        }

        wrapper.orderByDesc(WithdrawApply::getCreateTime);

        Page<WithdrawApply> resultPage = withdrawApplyMapper.selectPage(pageParam, wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("list", resultPage.getRecords());
        result.put("total", resultPage.getTotal());
        result.put("page", safePage);
        result.put("size", safeSize);

        return result;
    }

    @Override
    public Map<String, Object> getPendingWithdraws(Integer page, Integer size) {
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        Page<WithdrawApply> pageParam = new Page<>(safePage, safeSize);

        LambdaQueryWrapper<WithdrawApply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WithdrawApply::getStatus, "pending")
                .orderByAsc(WithdrawApply::getCreateTime);

        Page<WithdrawApply> resultPage = withdrawApplyMapper.selectPage(pageParam, wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("list", resultPage.getRecords());
        result.put("total", resultPage.getTotal());
        result.put("page", safePage);
        result.put("size", safeSize);

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reviewWithdraw(Long withdrawId, Long reviewerId,
                                             Boolean approved, String reviewReason) {
        WithdrawApply apply = withdrawApplyMapper.selectById(withdrawId);
        if (apply == null) {
            throw new BusinessException("提现申请不存在");
        }

        if (!"pending".equals(apply.getStatus())) {
            throw new BusinessException("申请已处理");
        }

        Map<String, Object> result = new HashMap<>();

        LocalDateTime reviewTime = LocalDateTime.now();
        apply.setStatus(approved ? "processing" : "rejected");
        apply.setReviewerId(reviewerId);
        apply.setReviewTime(reviewTime);
        apply.setReviewReason(reviewReason);
        if (withdrawApplyMapper.reviewPending(apply) != 1) {
            throw new BusinessException("申请已被其他审核人处理");
        }

        if (approved) {

            result.put("status", "processing");
            result.put("message", "提现申请已通过，等待打款");

            log.info("event=withdraw_reviewed withdrawId={} approved=true", withdrawId);

        } else {


            User creator = userMapper.selectByIdForUpdate(apply.getCreatorId());
            if (creator == null) {
                throw new BusinessException("创作者不存在，无法返还提现冻结金额");
            }
            BigDecimal pendingEarnings = creator.getPendingEarnings() != null ?
                    creator.getPendingEarnings() : BigDecimal.ZERO;
            creator.setPendingEarnings(pendingEarnings.add(apply.getAmount()));
            if (userMapper.updateById(creator) != 1) {
                throw new BusinessException("提现冻结金额返还失败");
            }

            result.put("status", "rejected");
            result.put("message", "提现申请已拒绝");
            result.put("reason", reviewReason);

            log.info("event=withdraw_reviewed withdrawId={} approved=false", withdrawId);
        }

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean completeWithdraw(Long withdrawId, String transactionId, Long operatorId) {
        if (transactionId == null || transactionId.trim().isEmpty() || transactionId.trim().length() > 128) {
            throw new BusinessException("交易流水号不能为空且不能超过128字符");
        }
        WithdrawApply apply = withdrawApplyMapper.selectById(withdrawId);
        if (apply == null) {
            throw new BusinessException("提现申请不存在");
        }

        if (!"processing".equals(apply.getStatus())) {
            throw new BusinessException("申请状态不正确");
        }


        apply.setStatus("completed");
        apply.setTransactionId(transactionId.trim());
        apply.setCompletedTime(LocalDateTime.now());
        if (withdrawApplyMapper.completeProcessing(apply) != 1) {
            throw new BusinessException("提现申请已被处理");
        }


        User creator = userMapper.selectByIdForUpdate(apply.getCreatorId());
        if (creator == null) {
            throw new BusinessException("创作者不存在，无法累计已提现金额");
        }
        BigDecimal withdrawnEarnings = creator.getWithdrawnEarnings() != null ?
                creator.getWithdrawnEarnings() : BigDecimal.ZERO;
        creator.setWithdrawnEarnings(withdrawnEarnings.add(apply.getAmount()));
        if (userMapper.updateById(creator) != 1) {
            throw new BusinessException("已提现金额更新失败");
        }

        log.info("event=withdraw_completed withdrawId={}", withdrawId);
        return true;
    }

    @Override
    public Map<String, Object> getWithdrawDetail(Long withdrawId) {
        return getWithdrawDetail(withdrawId, UserContext.getCurrentUserId());
    }

    @Override
    public Map<String, Object> getWithdrawDetail(Long withdrawId, Long viewerId) {
        WithdrawApply apply = withdrawApplyMapper.selectById(withdrawId);
        if (apply == null) {
            throw new BusinessException("提现申请不存在");
        }

        requireWithdrawVisible(apply, viewerId);
        return buildWithdrawDetail(apply);
    }

    private void requireWithdrawVisible(WithdrawApply apply, Long viewerId) {
        if (viewerId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        if (viewerId.equals(apply.getCreatorId())) {
            return;
        }
        if (permissionService != null && permissionService.isAdmin(viewerId)) {
            return;
        }
        throw new BusinessException(ResultCode.FORBIDDEN, "无权查看此提现申请");
    }

    private Map<String, Object> buildWithdrawDetail(WithdrawApply apply) {
        Map<String, Object> result = new HashMap<>();
        result.put("withdrawId", apply.getId());
        result.put("creatorId", apply.getCreatorId());
        result.put("amount", apply.getAmount());
        result.put("withdrawType", apply.getWithdrawType());
        result.put("withdrawAccount", maskAccount(apply.getWithdrawAccount()));
        result.put("withdrawName", apply.getWithdrawName());
        result.put("status", apply.getStatus());
        result.put("reviewReason", apply.getReviewReason());
        result.put("transactionId", apply.getTransactionId());
        result.put("createTime", apply.getCreateTime());
        result.put("reviewTime", apply.getReviewTime());
        result.put("completedTime", apply.getCompletedTime());

        return result;
    }

    @Override
    public BigDecimal getAvailableAmount(Long creatorId) {
        User creator = userMapper.selectById(creatorId);
        if (creator == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal pendingEarnings = creator.getPendingEarnings() != null ?
                creator.getPendingEarnings() : BigDecimal.ZERO;


        LambdaQueryWrapper<CreatorDebt> debtWrapper = new LambdaQueryWrapper<>();
        debtWrapper.eq(CreatorDebt::getCreatorId, creatorId)
                .in(CreatorDebt::getStatus, "pending", "partial")
                .eq(CreatorDebt::getDeleted, 0);
        List<CreatorDebt> debts = creatorDebtMapper.selectList(debtWrapper);

        BigDecimal totalDebt = BigDecimal.ZERO;
        for (CreatorDebt debt : debts) {
            totalDebt = totalDebt.add(debt.getRemainingAmount());
        }

        return pendingEarnings.subtract(totalDebt);
    }

    @Override
    public Boolean checkWithdrawCondition(Long creatorId, BigDecimal amount) {
        if (creatorId == null) {
            return false;
        }
        User creator = userMapper.selectById(creatorId);
        return creator != null && checkWithdrawCondition(creator, amount);
    }

    private Boolean checkWithdrawCondition(User creator, BigDecimal amount) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0
                || amount.compareTo(paymentConfig.getWithdrawMinAmount()) < 0) {
            return false;
        }


        BigDecimal pendingEarnings = creator.getPendingEarnings() == null
                ? BigDecimal.ZERO : creator.getPendingEarnings();
        BigDecimal available = pendingEarnings.subtract(getOutstandingDebt(creator.getId()));
        return available.compareTo(amount) >= 0;
    }

    private BigDecimal getOutstandingDebt(Long creatorId) {
        LambdaQueryWrapper<CreatorDebt> debtWrapper = new LambdaQueryWrapper<>();
        debtWrapper.eq(CreatorDebt::getCreatorId, creatorId)
                .in(CreatorDebt::getStatus, "pending", "partial")
                .eq(CreatorDebt::getDeleted, 0);
        List<CreatorDebt> debts = creatorDebtMapper.selectList(debtWrapper);
        BigDecimal totalDebt = BigDecimal.ZERO;
        if (debts != null) {
            for (CreatorDebt debt : debts) {
                if (debt != null && debt.getRemainingAmount() != null) {
                    totalDebt = totalDebt.add(debt.getRemainingAmount());
                }
            }
        }
        return totalDebt;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean freezeWithdrawAmount(Long withdrawId, Long refundId, BigDecimal freezeAmount) {
        if (withdrawId == null || refundId == null || freezeAmount == null
                || freezeAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("提现冻结参数不正确");
        }
        WithdrawApply withdraw = withdrawApplyMapper.selectById(withdrawId);
        if (withdraw == null) {
            throw new BusinessException("提现申请不存在");
        }
        if (withdraw.getAmount() != null && freezeAmount.compareTo(withdraw.getAmount()) > 0) {
            throw new BusinessException("冻结金额不能超过提现金额");
        }

        WithdrawFreeze existing = findFreeze(withdrawId, refundId);
        if (existing != null) {
            ensureSameFreeze(existing, withdraw.getCreatorId(), freezeAmount);
            return true;
        }

        WithdrawFreeze freeze = new WithdrawFreeze();
        freeze.setWithdrawId(withdrawId);
        freeze.setCreatorId(withdraw.getCreatorId());
        freeze.setRefundId(refundId);
        freeze.setFreezeAmount(freezeAmount);
        freeze.setStatus("frozen");
        freeze.setReason("退款审核中冻结");
        freeze.setHandleTime(LocalDateTime.now());
        freeze.setDeleted(0);

        try {
            if (withdrawFreezeMapper.insert(freeze) != 1) {
                throw new BusinessException("提现冻结记录创建失败");
            }
        } catch (DuplicateKeyException exception) {
            WithdrawFreeze concurrent = findFreeze(withdrawId, refundId);
            if (concurrent == null) {
                throw exception;
            }
            ensureSameFreeze(concurrent, withdraw.getCreatorId(), freezeAmount);
        }

        log.info("event=withdraw_amount_frozen withdrawId={} refundId={}",
                withdrawId, refundId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean handleWithdrawFreeze(Long freezeId, Boolean deduct) {
        if (freezeId == null || deduct == null) {
            throw new BusinessException("提现冻结处理参数不正确");
        }
        WithdrawFreeze freeze = withdrawFreezeMapper.selectById(freezeId);
        if (freeze == null) {
            return false;
        }

        String targetStatus = deduct ? "deducted" : "released";
        if (targetStatus.equals(freeze.getStatus())) {
            return true;
        }
        if (!"frozen".equals(freeze.getStatus())) {
            throw new BusinessException("提现冻结记录已按其他结果处理");
        }
        if (withdrawFreezeMapper.transitionFrozen(freezeId, targetStatus) != 1) {
            WithdrawFreeze concurrent = withdrawFreezeMapper.selectById(freezeId);
            if (concurrent != null && targetStatus.equals(concurrent.getStatus())) {
                return true;
            }
            throw new BusinessException("提现冻结状态已变化，请刷新后重试");
        }

        if (deduct) {

            recordCreatorDebt(freeze.getCreatorId(), freeze.getRefundId(),
                    freeze.getWithdrawId(), freeze.getFreezeAmount());
        }

        log.info("event=withdraw_freeze_processed freezeId={}", freezeId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean recordCreatorDebt(Long creatorId, Long refundId, Long withdrawId,
                                    BigDecimal debtAmount) {
        if (creatorId == null || refundId == null || debtAmount == null
                || debtAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("创作者欠款参数不正确");
        }
        CreatorDebt existing = findDebt(refundId, withdrawId);
        if (existing != null) {
            if (!creatorId.equals(existing.getCreatorId())
                    || existing.getDebtAmount() == null
                    || existing.getDebtAmount().compareTo(debtAmount) != 0) {
                throw new BusinessException("退款欠款事实不一致");
            }
            return true;
        }

        CreatorDebt debt = new CreatorDebt();
        debt.setCreatorId(creatorId);
        debt.setRefundId(refundId);
        debt.setWithdrawId(withdrawId);
        debt.setDebtAmount(debtAmount);
        debt.setPaidAmount(BigDecimal.ZERO);
        debt.setRemainingAmount(debtAmount);
        debt.setStatus("pending");
        debt.setReason("提现后发生退款");
        debt.setDeleted(0);

        try {
            if (creatorDebtMapper.insert(debt) != 1) {
                throw new BusinessException("创作者欠款记录创建失败");
            }
        } catch (DuplicateKeyException exception) {
            CreatorDebt concurrent = findDebt(refundId, withdrawId);
            if (concurrent == null) {
                throw exception;
            }
            if (!creatorId.equals(concurrent.getCreatorId())
                    || concurrent.getDebtAmount() == null
                    || concurrent.getDebtAmount().compareTo(debtAmount) != 0) {
                throw new BusinessException("退款欠款事实不一致");
            }
        }

        log.info("event=creator_debt_recorded creatorId={}", creatorId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal deductDebtFromEarnings(Long creatorId) {

        LambdaQueryWrapper<CreatorDebt> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CreatorDebt::getCreatorId, creatorId)
                .in(CreatorDebt::getStatus, "pending", "partial")
                .eq(CreatorDebt::getDeleted, 0)
                .orderByAsc(CreatorDebt::getCreateTime);

        List<CreatorDebt> debts = creatorDebtMapper.selectList(wrapper);
        if (debts == null || debts.isEmpty()) {
            return BigDecimal.ZERO;
        }

        User creator = userMapper.selectByIdForUpdate(creatorId);
        if (creator == null) {
            throw new BusinessException("创作者不存在");
        }
        BigDecimal pendingEarnings = creator.getPendingEarnings() != null
                ? creator.getPendingEarnings() : BigDecimal.ZERO;

        BigDecimal totalDeducted = BigDecimal.ZERO;

        for (CreatorDebt debt : debts) {
            if (debt.getRemainingAmount().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            if (pendingEarnings.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }


            BigDecimal deductAmount = pendingEarnings.min(debt.getRemainingAmount());


            pendingEarnings = pendingEarnings.subtract(deductAmount);


            debt.setPaidAmount(debt.getPaidAmount().add(deductAmount));
            debt.setRemainingAmount(debt.getRemainingAmount().subtract(deductAmount));

            if (debt.getRemainingAmount().compareTo(BigDecimal.ZERO) <= 0) {
                debt.setStatus("cleared");
                debt.setClearedTime(LocalDateTime.now());
            } else {
                debt.setStatus("partial");
            }

            if (creatorDebtMapper.updateById(debt) != 1) {
                throw new BusinessException("创作者欠款状态更新失败");
            }

            totalDeducted = totalDeducted.add(deductAmount);
            log.info("event=creator_debt_deducted creatorId={}", creatorId);
        }

        if (totalDeducted.compareTo(BigDecimal.ZERO) > 0) {
            creator.setPendingEarnings(pendingEarnings);
            if (userMapper.updateById(creator) != 1) {
                throw new BusinessException("创作者收益扣除失败");
            }
        }

        return totalDeducted;
    }

    private WithdrawFreeze findFreeze(Long withdrawId, Long refundId) {
        return withdrawFreezeMapper.selectOne(new LambdaQueryWrapper<WithdrawFreeze>()
                .eq(WithdrawFreeze::getWithdrawId, withdrawId)
                .eq(WithdrawFreeze::getRefundId, refundId)
                .eq(WithdrawFreeze::getDeleted, 0)
                .last("LIMIT 1"));
    }

    private void ensureSameFreeze(WithdrawFreeze freeze, Long creatorId, BigDecimal amount) {
        if (!creatorId.equals(freeze.getCreatorId()) || freeze.getFreezeAmount() == null
                || freeze.getFreezeAmount().compareTo(amount) != 0) {
            throw new BusinessException("退款冻结事实不一致");
        }
    }

    private CreatorDebt findDebt(Long refundId, Long withdrawId) {
        LambdaQueryWrapper<CreatorDebt> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CreatorDebt::getRefundId, refundId)
                .eq(CreatorDebt::getDeleted, 0);
        if (withdrawId == null) {
            wrapper.isNull(CreatorDebt::getWithdrawId);
        } else {
            wrapper.eq(CreatorDebt::getWithdrawId, withdrawId);
        }
        return creatorDebtMapper.selectOne(wrapper.last("LIMIT 1"));
    }

    @Override
    public Map<String, Object> getWithdrawStatistics(Long creatorId) {
        User creator = userMapper.selectById(creatorId);
        if (creator == null) {
            throw new BusinessException("创作者不存在");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalEarnings", creator.getTotalEarnings() != null ?
                creator.getTotalEarnings() : BigDecimal.ZERO);
        result.put("withdrawnEarnings", creator.getWithdrawnEarnings() != null ?
                creator.getWithdrawnEarnings() : BigDecimal.ZERO);
        result.put("pendingEarnings", creator.getPendingEarnings() != null ?
                creator.getPendingEarnings() : BigDecimal.ZERO);
        result.put("availableEarnings", getAvailableAmount(creatorId));

        return result;
    }



    private int normalizePage(Integer page) {
        return ObjectUtils.isEmpty(page) || page < 1 ? 1 : page;
    }

    private int normalizeSize(Integer size) {
        if (ObjectUtils.isEmpty(size) || size < 1) {
            return 20;
        }
        return Math.min(size, 100);
    }




    private Boolean isValidWithdrawType(String withdrawType) {
        return "alipay".equals(withdrawType)
                || "wechat".equals(withdrawType)
                || "bank".equals(withdrawType);
    }




    private String maskAccount(String account) {
        if (account == null || account.length() < 4) {
            return account;
        }
        return account.substring(0, 4) + "****";
    }
}


