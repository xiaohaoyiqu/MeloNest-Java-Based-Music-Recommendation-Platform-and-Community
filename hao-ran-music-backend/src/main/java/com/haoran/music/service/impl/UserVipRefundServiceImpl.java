package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.haoran.music.common.config.VipConfig;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.enums.VipLevel;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.PaymentOrderStatusUtil;
import com.haoran.music.entity.PaymentOrder;
import com.haoran.music.entity.User;
import com.haoran.music.entity.UserVip;
import com.haoran.music.entity.VipChangeRecord;
import com.haoran.music.entity.VipPurchaseRecord;
import com.haoran.music.mapper.PaymentOrderMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.mapper.UserVipMapper;
import com.haoran.music.mapper.VipChangeRecordMapper;
import com.haoran.music.mapper.VipPurchaseRecordMapper;
import com.haoran.music.service.UserVipRefundService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

   
                
  
                      
   
@Slf4j
@Service
public class UserVipRefundServiceImpl implements UserVipRefundService {

    private final UserMapper userMapper;
    private final UserVipMapper userVipMapper;
    private final PaymentOrderMapper paymentOrderMapper;
    private final VipPurchaseRecordMapper vipPurchaseRecordMapper;
    private final VipChangeRecordMapper vipChangeRecordMapper;


    private final VipConfig vipConfig;

    public UserVipRefundServiceImpl(UserMapper userMapper,
                                     UserVipMapper userVipMapper,
                                     VipChangeRecordMapper vipChangeRecordMapper,
                                     PaymentOrderMapper paymentOrderMapper,
                                     VipPurchaseRecordMapper vipPurchaseRecordMapper,
                                     VipConfig vipConfig) {
        this.userMapper = userMapper;
        this.userVipMapper = userVipMapper;
        this.vipChangeRecordMapper = vipChangeRecordMapper;
        this.paymentOrderMapper = paymentOrderMapper;
        this.vipPurchaseRecordMapper = vipPurchaseRecordMapper;
        this.vipConfig = vipConfig;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer processVipRefund(Long userId, Long orderId, BigDecimal refundAmount) {
        PaymentOrder order = requireRefundableVipOrder(userId, orderId, refundAmount);

        Long existingRecordCount = vipChangeRecordMapper.selectCount(new LambdaQueryWrapper<VipChangeRecord>()
                .eq(VipChangeRecord::getChangeType, "refund")
                .eq(VipChangeRecord::getRelatedId, orderId));
        if (existingRecordCount != null && existingRecordCount > 0) {
            log.info("event=vip_refund_entitlement_replay_skipped userId={} orderId={}", userId, orderId);
            return 0;
        }

                  
        Integer deductDays = calculateDeductDays(order, refundAmount);
        if (deductDays == null || deductDays <= 0) {
            throw new BusinessException("无法计算VIP退款扣减天数");
        }

                  
        VipDeductionResult deduction = deductVipDaysInternal(userId, deductDays);
        if (deduction == null) {
            throw new BusinessException("用户当前没有可扣减的VIP权益");
        }

                  
        insertVipChange(userId, "refund", -deductDays, orderId, "VIP退款",
                deduction.vipLevel, deduction.oldExpireTime, deduction.newExpireTime);

        log.info("event=vip_refund_entitlement_deducted userId={} orderId={}", userId, orderId);

        return deductDays;
    }

    @Override
    public Integer calculateDeductDays(Long orderId, BigDecimal refundAmount) {
        if (ObjectUtils.isEmpty(orderId) || refundAmount == null
                || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }

        PaymentOrder order = paymentOrderMapper.selectById(orderId);
        if (order == null || !"vip".equalsIgnoreCase(order.getBusinessType())
                || !PaymentOrderStatusUtil.isPaymentHandled(order.getStatus())) {
            return 0;
        }

        return calculateDeductDays(order, refundAmount);
    }

    private Integer calculateDeductDays(PaymentOrder order, BigDecimal refundAmount) {
        if (order == null || refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }

        BigDecimal orderAmount = order.getAmount();
        if (orderAmount == null || orderAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }

                 
        if (refundAmount.compareTo(orderAmount) > 0) {
            return 0;
        }
        BigDecimal refundRatio = refundAmount.divide(orderAmount, 8, RoundingMode.HALF_UP);

                    
        Integer orderDays = getDaysFromPurchaseRecord(order.getId(), order.getUserId());
        if (orderDays == null) {
            orderDays = getDaysByAmount(orderAmount);
        }

        if (orderDays == null || orderDays <= 0) {
            log.warn("event=vip_refund_order_duration_unresolved orderId={}", order.getId());
            return 0;
        }

                    
        Integer deductDays = new BigDecimal(orderDays)
                .multiply(refundRatio)
                .setScale(0, RoundingMode.UP)
                .intValue();

        log.info("event=vip_refund_deduction_calculated orderId={}", order.getId());

        return deductDays;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LocalDateTime deductVipDays(Long userId, Integer deductDays) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(deductDays) || deductDays <= 0) {
            return null;
        }
        VipDeductionResult result = deductVipDaysInternal(userId, deductDays);
        return result == null ? null : result.newExpireTime;
    }

    @Override
    public List<Object> getUserVipPurchases(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return Collections.emptyList();
        }

                       
        LambdaQueryWrapper<PaymentOrder> wrapper = PaymentOrderStatusUtil.applyPaidOrderFilter(new LambdaQueryWrapper<>());
        wrapper.eq(PaymentOrder::getUserId, userId)
                .eq(PaymentOrder::getBusinessType, "vip")
                .orderByDesc(PaymentOrder::getCreateTime);

        return paymentOrderMapper.selectList(wrapper).stream()
                .map(order -> {
                    Map<String, Object> info = new HashMap<>();
                    info.put("orderId", order.getId());
                    info.put("amount", order.getAmount());
                    info.put("createTime", order.getCreateTime());
                    info.put("status", order.getStatus());
                    return info;
                })
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public Object getUserVipInfo(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return null;
        }

        if (userMapper.selectById(userId) == null) {
            return null;
        }

        UserVip vip = userVipMapper.selectActiveVip(userId, LocalDateTime.now());

        Map<String, Object> vipInfo = new HashMap<>();
        vipInfo.put("userId", userId);
        vipInfo.put("vipLevel", vip == null ? VipLevel.FREE.name() : VipLevel.fromCode(vip.getVipLevel()).name());
        vipInfo.put("vipExpireTime", vip == null ? null : vip.getVipExpireTime());
        vipInfo.put("isVip", vip != null);
        vipInfo.put("remainingDays", calculateRemainingDays(vip));

        return vipInfo;
    }

    @Override
    public boolean isUserVip(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return false;
        }

        return userVipMapper.selectActiveVip(userId, LocalDateTime.now()) != null;
    }

    @Override
    public Integer getVipRemainingDays(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return 0;
        }

        return calculateRemainingDays(userVipMapper.selectActiveVip(userId, LocalDateTime.now()));
    }

    @Override
    public void recordVipChange(Long userId, String changeType, Integer changeDays,
                                Long relatedId, String reason) {
        if (ObjectUtils.isEmpty(userId)) {
            return;
        }
        UserVip vip = userVipMapper.selectActiveVip(userId, LocalDateTime.now());
        LocalDateTime expireTime = vip == null ? null : vip.getVipExpireTime();
        Integer vipLevel = vip == null ? VipLevel.FREE.getCode() : vip.getVipLevel();
        insertVipChange(userId, changeType, changeDays, relatedId, reason,
                vipLevel, expireTime, expireTime);
    }

       
                  
       
    private Integer getDaysByAmount(BigDecimal amount) {
        if (amount == null) {
            return null;
        }

        vipConfig.initDefaultPrices();
        List<VipConfig.VipPrice> prices = new ArrayList<>(vipConfig.getPrices().values());
        prices.sort((left, right) -> Integer.compare(right.getActualPrice(), left.getActualPrice()));

        for (VipConfig.VipPrice price : prices) {
            if (ObjectUtils.isEmpty(price) || ObjectUtils.isEmpty(price.getPrice()) || ObjectUtils.isEmpty(price.getDays())) {
                continue;
            }
            BigDecimal priceInYuan = BigDecimal.valueOf(price.getActualPrice())
                    .movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
            if (amount.compareTo(priceInYuan) == 0) {
                return price.getDays();
            }
        }

        return null;
    }

    private Integer getDaysFromPurchaseRecord(Long orderId, Long userId) {
        VipPurchaseRecord record = vipPurchaseRecordMapper.selectOne(new LambdaQueryWrapper<VipPurchaseRecord>()
                .eq(VipPurchaseRecord::getOrderId, orderId)
                .eq(VipPurchaseRecord::getUserId, userId)
                .orderByDesc(VipPurchaseRecord::getCreateTime)
                .last("LIMIT 1"));
        return record == null ? null : record.getVipDays();
    }

    private PaymentOrder requireRefundableVipOrder(Long userId, Long orderId, BigDecimal refundAmount) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(orderId)
                || refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("退款参数不合法");
        }
        PaymentOrder order = paymentOrderMapper.selectById(orderId);
        if (order == null || !userId.equals(order.getUserId())) {
            throw new BusinessException("VIP订单不存在或不属于当前用户");
        }
        if (!"vip".equalsIgnoreCase(order.getBusinessType())
                || !PaymentOrderStatusUtil.isPaymentHandled(order.getStatus())) {
            throw new BusinessException("订单不是可退款的已支付VIP订单");
        }
        if (order.getAmount() == null || refundAmount.compareTo(order.getAmount()) > 0) {
            throw new BusinessException("退款金额不能超过订单金额");
        }
        return order;
    }

    private VipDeductionResult deductVipDaysInternal(Long userId, Integer deductDays) {
        LocalDateTime now = LocalDateTime.now();
        UserVip vip = userVipMapper.selectActiveVip(userId, now);
        if (vip == null || vip.getVipExpireTime() == null) {
            return null;
        }

        LocalDateTime oldExpireTime = vip.getVipExpireTime();
        LocalDateTime newExpireTime = oldExpireTime.minusDays(deductDays);
        if (newExpireTime.isBefore(now)) {
            newExpireTime = now;
        }
        int newStatus = newExpireTime.isAfter(now) ? 1 : 2;
        int updated = userVipMapper.update(null, new UpdateWrapper<UserVip>()
                .eq("id", vip.getId())
                .eq("vip_status", 1)
                .eq("vip_expire_time", oldExpireTime)
                .eq("deleted", CommonConstants.NOT_DELETED)
                .set("vip_expire_time", newExpireTime)
                .set("vip_status", newStatus));
        if (updated != 1) {
            throw new BusinessException("VIP权益发生变化，请重试退款处理");
        }

        log.info("event=vip_entitlement_period_deducted userId={}", userId);
        return new VipDeductionResult(vip.getVipLevel(), oldExpireTime, newExpireTime);
    }

    private void insertVipChange(Long userId, String changeType, Integer changeDays,
                                 Long relatedId, String reason, Integer vipLevel,
                                 LocalDateTime beforeExpireTime, LocalDateTime afterExpireTime) {
        VipChangeRecord record = new VipChangeRecord();
        record.setUserId(userId);
        record.setChangeType(changeType);
        record.setChangeDays(changeDays == null ? 0 : changeDays);
        record.setBeforeExpireTime(beforeExpireTime);
        record.setAfterExpireTime(afterExpireTime);
        record.setRelatedId(relatedId);
        record.setReason(reason);
        record.setVipLevelBefore(levelName(vipLevel, beforeExpireTime));
        record.setVipLevelAfter(levelName(vipLevel, afterExpireTime));
        record.setOperator("system");
        vipChangeRecordMapper.insert(record);
    }

    private String levelName(Integer vipLevel, LocalDateTime expireTime) {
        if (expireTime == null || !expireTime.isAfter(LocalDateTime.now())) {
            return VipLevel.FREE.name();
        }
        return VipLevel.fromCode(vipLevel).name();
    }

    private Integer calculateRemainingDays(UserVip vip) {
        if (vip == null || vip.getVipExpireTime() == null) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        if (!vip.getVipExpireTime().isAfter(now)) {
            return 0;
        }
        return Math.toIntExact(ChronoUnit.DAYS.between(now.toLocalDate(),
                vip.getVipExpireTime().toLocalDate()) + 1);
    }

    private static final class VipDeductionResult {
        private final Integer vipLevel;
        private final LocalDateTime oldExpireTime;
        private final LocalDateTime newExpireTime;

        private VipDeductionResult(Integer vipLevel, LocalDateTime oldExpireTime,
                                   LocalDateTime newExpireTime) {
            this.vipLevel = vipLevel;
            this.oldExpireTime = oldExpireTime;
            this.newExpireTime = newExpireTime;
        }
    }
}
