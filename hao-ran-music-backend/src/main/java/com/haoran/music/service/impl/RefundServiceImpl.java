package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.haoran.music.common.context.UserContext;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.PaymentOrderStatusUtil;
import com.haoran.music.common.util.SecurityCheckUtil;
import com.haoran.music.entity.PaymentOrder;
import com.haoran.music.entity.RefundCredit;
import com.haoran.music.entity.RefundRecord;
import com.haoran.music.mapper.PaymentOrderMapper;
import com.haoran.music.mapper.RefundCreditMapper;
import com.haoran.music.mapper.RefundRecordMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.PermissionService;
import com.haoran.music.service.PaidEntitlementLedgerService;
import com.haoran.music.service.RefundService;
import com.haoran.music.service.UserVipRefundService;
import com.haoran.music.service.StoreEntitlementRefundService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;





@Slf4j
@Service
public class RefundServiceImpl implements RefundService {

    @Autowired
    private PaymentOrderMapper paymentOrderMapper;

    @Autowired
    private RefundRecordMapper refundRecordMapper;

    @Autowired
    private RefundCreditMapper refundCreditMapper;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PaidEntitlementLedgerService entitlementLedgerService;

    @Autowired
    private UserVipRefundService userVipRefundService;

    @Autowired
    private StoreEntitlementRefundService storeEntitlementRefundService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> applyRefund(Long userId, Long orderId, String orderType,
                                            String reason, String description) {
        String safeReason = sanitizeRefundText(reason);
        String safeDescription = sanitizeRefundText(description);

        PaymentOrder order = paymentOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }


        if (!order.getUserId().equals(userId)) {
            throw new BusinessException("无权操作此订单");
        }


        if (!PaymentOrderStatusUtil.isPaidStatus(order.getStatus())) {
            throw new BusinessException("当前订单状态不可申请退款");
        }

        if (userMapper.selectByIdForUpdate(userId) == null) {
            throw new BusinessException("用户不存在");
        }


        Integer monthCount = getMonthRefundCount(userId);
        if (monthCount >= 3) {
            throw new BusinessException("本月退款次数已达上限");
        }


        RefundRecord refundRecord = new RefundRecord();
        refundRecord.setUserId(userId);
        refundRecord.setOrderId(orderId);
        refundRecord.setOriginalOrderStatus(order.getStatus());
        refundRecord.setOrderType(order.getBusinessType());
        refundRecord.setAmount(order.getAmount());
        refundRecord.setReason(safeReason);
        refundRecord.setDescription(safeDescription);
        refundRecord.setStatus("pending");
        refundRecord.setCreateTime(LocalDateTime.now());


        if (paymentOrderMapper.transitionStatus(orderId, order.getStatus(), "refunding") != 1) {
            throw new BusinessException("订单状态已变化，请刷新后重试");
        }
        if (refundRecordMapper.insert(refundRecord) != 1) {
            throw new BusinessException("退款申请创建失败");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("refundId", refundRecord.getId());
        result.put("orderId", orderId);
        result.put("status", "refunding");
        result.put("message", "退款申请已提交，等待审核");

        log.info("event=refund_requested userId={} orderId={} businessType={}",
                userId, orderId, order.getBusinessType());

        return result;
    }

    @Override
    public Map<String, Object> getMyRefundRecords(Long userId, String status,
                                                  Integer page, Integer size) {
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<RefundRecord> pageParam =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(safePage, safeSize);

        LambdaQueryWrapper<RefundRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RefundRecord::getUserId, userId);

        if (ObjectUtils.isNotEmpty(status)) {
            wrapper.eq(RefundRecord::getStatus, status);
        }

        wrapper.orderByDesc(RefundRecord::getCreateTime);

        com.baomidou.mybatisplus.core.metadata.IPage<RefundRecord> resultPage =
                refundRecordMapper.selectPage(pageParam, wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("list", resultPage.getRecords());
        result.put("total", resultPage.getTotal());
        result.put("page", safePage);
        result.put("size", safeSize);

        return result;
    }

    @Override
    public Map<String, Object> getPendingRefunds(Integer page, Integer size) {
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<RefundRecord> pageParam =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(safePage, safeSize);

        LambdaQueryWrapper<RefundRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RefundRecord::getStatus, "pending")
                .orderByAsc(RefundRecord::getCreateTime);

        com.baomidou.mybatisplus.core.metadata.IPage<RefundRecord> resultPage =
                refundRecordMapper.selectPage(pageParam, wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("list", resultPage.getRecords());
        result.put("total", resultPage.getTotal());
        result.put("page", safePage);
        result.put("size", safeSize);

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reviewRefund(Long refundId, Long reviewerId,
                                     Boolean approved, String reviewReason,
                                     Boolean isUnreasonable, String unreasonableReason) {
        String safeReviewReason = sanitizeRefundText(reviewReason);
        String safeUnreasonableReason = sanitizeRefundText(unreasonableReason);
        RefundRecord refundRecord = refundRecordMapper.selectById(refundId);
        if (refundRecord == null) {
            throw new BusinessException("退款记录不存在");
        }

        if (!"pending".equals(refundRecord.getStatus())) {
            throw new BusinessException("退款已处理");
        }

        Map<String, Object> result = new HashMap<>();

        if (approved) {

            int unreasonable = isUnreasonable != null && isUnreasonable ? 1 : 0;
            if (refundRecordMapper.reviewPending(refundId, "approved", reviewerId,
                    LocalDateTime.now(), safeReviewReason, unreasonable, safeUnreasonableReason) != 1) {
                throw new BusinessException("退款已被其他操作处理");
            }


            if (isUnreasonable != null && isUnreasonable) {
                deductRefundCredit(refundRecord.getUserId(), refundId, 10, "不合理退款");
            }

            result.put("status", "approved");
            result.put("message", "退款已批准");

            log.info("event=refund_reviewed refundId={} reviewerId={} approved=true",
                    refundId, reviewerId);

        } else {

            if (refundRecordMapper.reviewPending(refundId, "rejected", reviewerId,
                    LocalDateTime.now(), safeReviewReason, 0, null) != 1) {
                throw new BusinessException("退款已被其他操作处理");
            }
            restoreOrderAfterRejectedOrCancelled(refundRecord);

            result.put("status", "rejected");
            result.put("message", "退款已拒绝");
            result.put("reason", safeReviewReason);

            log.info("event=refund_reviewed refundId={} reviewerId={} approved=false",
                    refundId, reviewerId);
        }

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean completeRefund(Long refundId, Long operatorId) {
        RefundRecord refundRecord = refundRecordMapper.selectById(refundId);
        if (refundRecord == null) {
            return false;
        }

        if (!"approved".equals(refundRecord.getStatus())) {
            return false;
        }

        PaymentOrder order = paymentOrderMapper.selectById(refundRecord.getOrderId());
        if (order == null || !refundRecord.getUserId().equals(order.getUserId())) {
            throw new BusinessException("退款订单身份不一致");
        }
        if (userMapper.selectByIdForUpdate(refundRecord.getUserId()) == null) {
            throw new BusinessException("退款权益用户不存在");
        }
        String businessType = order.getBusinessType() == null
                ? "" : order.getBusinessType().trim().toLowerCase(java.util.Locale.ROOT);
        if (!"purchase".equals(businessType) && !"subscribe".equals(businessType)
                && !"vip".equals(businessType) && !"emoji_package".equals(businessType)
                && !"decoration".equals(businessType)) {
            throw new BusinessException("该业务类型尚未接入可核对的退款权益补偿，不能完成退款");
        }

        if ("vip".equals(businessType)) {
            userVipRefundService.processVipRefund(
                    refundRecord.getUserId(), order.getId(), refundRecord.getAmount());
        }


        if (refundRecordMapper.completeApproved(refundId, LocalDateTime.now()) != 1) {
            return false;
        }


        if (paymentOrderMapper.transitionStatus(refundRecord.getOrderId(), "refunding", "refunded") != 1) {
            throw new BusinessException("订单退款状态已变化");
        }
        if ("emoji_package".equals(businessType) || "decoration".equals(businessType)) {
            if (!storeEntitlementRefundService.revoke(order, refundId)) {
                throw new BusinessException("商店订单权益撤回失败");
            }
        } else if (!"vip".equals(businessType)
                && !entitlementLedgerService.revokeByRefund(order.getId(), refundId)) {
            throw new BusinessException("退款订单缺少可核对的权益授予事实");
        }

        log.info("event=refund_completed refundId={} orderId={} businessType={} operatorId={}",
                refundId, order.getId(), businessType, operatorId);
        return true;
    }

    @Override
    public Map<String, Object> getRefundDetail(Long refundId) {
        return getRefundDetail(refundId, UserContext.getCurrentUserId());
    }

    @Override
    public Map<String, Object> getRefundDetail(Long refundId, Long viewerId) {
        RefundRecord refundRecord = refundRecordMapper.selectById(refundId);
        if (refundRecord == null) {
            throw new BusinessException("退款记录不存在");
        }

        requireRefundVisible(refundRecord, viewerId);
        return buildRefundDetail(refundRecord);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean cancelRefund(Long refundId, Long userId) {
        RefundRecord refundRecord = refundRecordMapper.selectById(refundId);
        if (refundRecord == null) {
            throw new BusinessException("退款记录不存在");
        }

        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }

        if (!userId.equals(refundRecord.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作此退款申请");
        }

        if (!"pending".equals(refundRecord.getStatus())) {
            throw new BusinessException("当前退款状态不可取消");
        }

        if (refundRecordMapper.cancelPending(refundId, userId) != 1) {
            throw new BusinessException("退款已被其他操作处理");
        }
        restoreOrderAfterRejectedOrCancelled(refundRecord);

        log.info("event=refund_cancelled refundId={} userId={}", refundId, userId);
        return true;
    }

    private void restoreOrderAfterRejectedOrCancelled(RefundRecord refundRecord) {
        String originalStatus = PaymentOrderStatusUtil.isPaidStatus(refundRecord.getOriginalOrderStatus())
                ? refundRecord.getOriginalOrderStatus()
                : PaymentOrderStatusUtil.STATUS_PAID;
        if (paymentOrderMapper.transitionStatus(
                refundRecord.getOrderId(), PaymentOrderStatusUtil.STATUS_REFUNDING, originalStatus) != 1) {
            throw new BusinessException("订单退款状态已变化");
        }
    }

    private String sanitizeRefundText(String text) {
        if (ObjectUtils.isEmpty(text)) {
            return text;
        }
        SecurityCheckUtil.CheckResult check = SecurityCheckUtil.checkDescription(text);
        if (!check.isSafe()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, check.getMessage());
        }
        return SecurityCheckUtil.escapeHtml(check.getCleanedValue());
    }

    private void requireRefundVisible(RefundRecord refundRecord, Long viewerId) {
        if (viewerId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        if (viewerId.equals(refundRecord.getUserId())) {
            return;
        }
        if (permissionService != null && permissionService.isAdmin(viewerId)) {
            return;
        }
        throw new BusinessException(ResultCode.FORBIDDEN, "无权查看此退款记录");
    }

    private Map<String, Object> buildRefundDetail(RefundRecord refundRecord) {
        Map<String, Object> result = new HashMap<>();
        result.put("refundId", refundRecord.getId());
        result.put("userId", refundRecord.getUserId());
        result.put("orderId", refundRecord.getOrderId());
        result.put("orderType", refundRecord.getOrderType());
        result.put("refundAmount", refundRecord.getAmount());
        result.put("reason", refundRecord.getReason());
        result.put("description", refundRecord.getDescription());
        result.put("status", refundRecord.getStatus());
        result.put("createTime", refundRecord.getCreateTime());
        result.put("reviewTime", refundRecord.getReviewTime());
        result.put("reviewReason", refundRecord.getReviewReason());

        return result;
    }

    @Override
    public Map<String, Object> checkRefundEligible(Long userId, Long orderId) {
        PaymentOrder order = paymentOrderMapper.selectById(orderId);
        if (order == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("eligible", false);
            result.put("reason", "订单不存在");
            return result;
        }

        if (!order.getUserId().equals(userId)) {
            Map<String, Object> result = new HashMap<>();
            result.put("eligible", false);
            result.put("reason", "无权操作此订单");
            return result;
        }

        if (!PaymentOrderStatusUtil.isPaidStatus(order.getStatus())) {
            Map<String, Object> result = new HashMap<>();
            result.put("eligible", false);
            result.put("reason", "当前订单状态不可申请退款");
            return result;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("eligible", true);
        result.put("reason", "可以申请退款");
        return result;
    }

    @Override
    public Integer getMonthRefundCount(Long userId) {
        YearMonth currentMonth = YearMonth.now();
        LocalDateTime startOfMonth = currentMonth.atDay(1).atStartOfDay();

        LambdaQueryWrapper<RefundRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RefundRecord::getUserId, userId)
                .ge(RefundRecord::getCreateTime, startOfMonth);

        return Math.toIntExact(refundRecordMapper.selectCount(wrapper));
    }

    @Override
    public Integer getRefundCredit(Long userId) {
        LambdaQueryWrapper<RefundCredit> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RefundCredit::getUserId, userId)
                .eq(RefundCredit::getDeleted, 0);

        RefundCredit refundCredit = refundCreditMapper.selectOne(wrapper);

        return refundCredit != null ? refundCredit.getCreditScore() : 100;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer deductRefundCredit(Long userId, Long refundId, Integer score, String reason) {
        if (userMapper.selectByIdForUpdate(userId) == null) {
            throw new BusinessException("用户不存在");
        }
        LambdaQueryWrapper<RefundCredit> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RefundCredit::getUserId, userId)
                .eq(RefundCredit::getDeleted, 0);

        RefundCredit refundCredit = refundCreditMapper.selectOne(wrapper);
        boolean existingCredit = refundCredit != null;

        if (refundCredit == null) {
            refundCredit = new RefundCredit();
            refundCredit.setUserId(userId);
            refundCredit.setCreditScore(100);
            refundCredit.setTotalRefundCount(0);
            refundCredit.setUnreasonableRefundCount(0);
            refundCredit.setDeleted(0);
        }

        Integer currentScore = refundCredit.getCreditScore() != null ?
                refundCredit.getCreditScore() : 100;
        refundCredit.setCreditScore(Math.max(0, currentScore - score));
        refundCredit.setTotalRefundCount((refundCredit.getTotalRefundCount() != null ?
                refundCredit.getTotalRefundCount() : 0) + 1);
        refundCredit.setLastUpdateTime(LocalDateTime.now());

        if ("不合理退款".equals(reason)) {
            refundCredit.setUnreasonableRefundCount((refundCredit.getUnreasonableRefundCount() != null ?
                    refundCredit.getUnreasonableRefundCount() : 0) + 1);
        }

        if (existingCredit) {
            if (refundCreditMapper.updateById(refundCredit) != 1) {
                throw new BusinessException("退款信用更新失败");
            }
        } else {
            if (refundCreditMapper.insert(refundCredit) != 1) {
                throw new BusinessException("退款信用创建失败");
            }
        }

        log.info("event=refund_credit_deducted userId={} refundId={}", userId, refundId);

        return refundCredit.getCreditScore();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer resetRefundCredit() {
        log.info("event=refund_credit_reset_started");

        int resetCount = 0;

        try {

            LambdaQueryWrapper<RefundCredit> wrapper = new LambdaQueryWrapper<>();
            wrapper.gt(RefundCredit::getUnreasonableRefundCount, 0);

            List<RefundCredit> refundCredits = refundCreditMapper.selectList(wrapper);


            for (RefundCredit refundCredit : refundCredits) {
                Integer oldCount = refundCredit.getUnreasonableRefundCount();
                if (oldCount != null && oldCount > 0) {
                    refundCredit.setUnreasonableRefundCount(0);
                    if (refundCreditMapper.updateById(refundCredit) != 1) {
                        log.warn("event=refund_credit_reset_skipped userId={}", refundCredit.getUserId());
                        continue;
                    }
                    resetCount++;

                    log.info("event=refund_credit_reset userId={}", refundCredit.getUserId());
                }
            }

            log.info("event=refund_credit_reset_completed resetCount={}", resetCount);
            return resetCount;

        } catch (Exception e) {
            log.error("event=refund_credit_reset_failed errorType={}",
                    e.getClass().getSimpleName());
            return resetCount;
        }
    }
    @Override
    public Boolean checkRemoveCreator(Long userId) {
        LambdaQueryWrapper<RefundCredit> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RefundCredit::getUserId, userId)
                .eq(RefundCredit::getDeleted, 0);

        RefundCredit refundCredit = refundCreditMapper.selectOne(wrapper);

        if (refundCredit == null) {
            return false;
        }


        Integer creditScore = refundCredit.getCreditScore();
        Integer unreasonableCount = refundCredit.getUnreasonableRefundCount() != null ?
                refundCredit.getUnreasonableRefundCount() : 0;

        return creditScore < 60 || unreasonableCount >= 3;
    }

    @Override
    public Map<String, Object> getRefundStatistics(Long userId) {

        Map<String, Object> result = new HashMap<>();


        LambdaQueryWrapper<RefundRecord> totalWrapper = new LambdaQueryWrapper<>();
        totalWrapper.eq(RefundRecord::getUserId, userId);
        Integer totalCount = Math.toIntExact(refundRecordMapper.selectCount(totalWrapper));


        LambdaQueryWrapper<RefundRecord> successWrapper = new LambdaQueryWrapper<>();
        successWrapper.eq(RefundRecord::getUserId, userId)
                .eq(RefundRecord::getStatus, "completed");
        Integer successCount = Math.toIntExact(refundRecordMapper.selectCount(successWrapper));


        LambdaQueryWrapper<RefundRecord> rejectWrapper = new LambdaQueryWrapper<>();
        rejectWrapper.eq(RefundRecord::getUserId, userId)
                .eq(RefundRecord::getStatus, "rejected");
        Integer rejectCount = Math.toIntExact(refundRecordMapper.selectCount(rejectWrapper));


        Integer unreasonableCount = 0;
        LambdaQueryWrapper<RefundCredit> creditWrapper = new LambdaQueryWrapper<>();
        creditWrapper.eq(RefundCredit::getUserId, userId)
                .eq(RefundCredit::getDeleted, 0);
        RefundCredit refundCredit = refundCreditMapper.selectOne(creditWrapper);
        if (refundCredit != null && refundCredit.getUnreasonableRefundCount() != null) {
            unreasonableCount = refundCredit.getUnreasonableRefundCount();
        }


        Integer creditScore = getRefundCredit(userId);

        result.put("totalCount", totalCount);
        result.put("successCount", successCount);
        result.put("rejectCount", rejectCount);
        result.put("unreasonableCount", unreasonableCount);
        result.put("creditScore", creditScore);

        return result;
    }










    @SuppressWarnings("unused")
    public Map<String, Object> approveRefund(Long refundId, Long adminId, String notes) {
        return reviewRefund(refundId, adminId, true, notes, false, null);
    }

    public Map<String, Object> rejectRefund(Long refundId, Long adminId, String reason) {
        return reviewRefund(refundId, adminId, false, reason, false, null);
    }





    public Map<String, Object> getRefundInfo(Long refundId) {
        return getRefundDetail(refundId);
    }

    public Map<String, Object> getUserRefunds(Long userId, Integer page, Integer size) {
        return getMyRefundRecords(userId, null, page, size);
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
}
