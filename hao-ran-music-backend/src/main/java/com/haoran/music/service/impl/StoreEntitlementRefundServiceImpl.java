   
                      
   

package com.haoran.music.service.impl;

import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.entity.DecorationPurchaseRecord;
import com.haoran.music.entity.EmojiPackagePurchaseRecord;
import com.haoran.music.entity.PaymentOrder;
import com.haoran.music.mapper.DecorationPurchaseRecordMapper;
import com.haoran.music.mapper.EmojiPackagePurchaseRecordMapper;
import com.haoran.music.mapper.UserDecorationMapper;
import com.haoran.music.mapper.UserEmojiMapper;
import com.haoran.music.service.CreatorEarningsDeductService;
import com.haoran.music.service.StoreEntitlementRefundService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class StoreEntitlementRefundServiceImpl implements StoreEntitlementRefundService {
    private final EmojiPackagePurchaseRecordMapper emojiRecordMapper;
    private final DecorationPurchaseRecordMapper decorationRecordMapper;
    private final UserEmojiMapper userEmojiMapper;
    private final UserDecorationMapper userDecorationMapper;
    private final CreatorEarningsDeductService earningsDeductService;

    public StoreEntitlementRefundServiceImpl(EmojiPackagePurchaseRecordMapper emojiRecordMapper,
                                             DecorationPurchaseRecordMapper decorationRecordMapper,
                                             UserEmojiMapper userEmojiMapper,
                                             UserDecorationMapper userDecorationMapper,
                                             CreatorEarningsDeductService earningsDeductService) {
        this.emojiRecordMapper = emojiRecordMapper;
        this.decorationRecordMapper = decorationRecordMapper;
        this.userEmojiMapper = userEmojiMapper;
        this.userDecorationMapper = userDecorationMapper;
        this.earningsDeductService = earningsDeductService;
    }

    @Override
    public boolean revoke(PaymentOrder order, Long refundId) {
        if (order == null || refundId == null) {
            return false;
        }
        if ("emoji_package".equals(order.getBusinessType())) {
            return revokeEmoji(order, refundId);
        }
        if ("decoration".equals(order.getBusinessType())) {
            return revokeDecoration(order, refundId);
        }
        return false;
    }

    private boolean revokeEmoji(PaymentOrder order, Long refundId) {
        EmojiPackagePurchaseRecord record = emojiRecordMapper.selectByOrderIdForUpdate(order.getId());
        if (record == null || !order.getUserId().equals(record.getUserId())
                || !order.getBusinessId().equals(record.getEmojiPackageId())) {
            throw new BusinessException("退款订单缺少匹配的表情包购买事实");
        }
        if ("refunded".equals(record.getStatus())) {
            return true;
        }
        if (!"active".equals(record.getStatus()) || emojiRecordMapper.markRefunded(order.getId(), refundId) != 1) {
            return false;
        }
        if (userEmojiMapper.revokePurchasedPackage(record.getUserId(), record.getEmojiPackageId()) != 1) {
            throw new BusinessException("表情包权益状态与购买事实不一致");
        }
        BigDecimal creatorEarnings = record.getCreatorEarnings();
        if (order.getPayeeId() != null && creatorEarnings != null
                && creatorEarnings.compareTo(BigDecimal.ZERO) > 0) {
            earningsDeductService.deductCreatorEarnings(order.getPayeeId(), refundId,
                    creatorEarnings, order.getId(), "emoji_package_order");
        }
        return true;
    }

    private boolean revokeDecoration(PaymentOrder order, Long refundId) {
        DecorationPurchaseRecord record = decorationRecordMapper.selectByOrderIdForUpdate(order.getId());
        if (record == null || !order.getUserId().equals(record.getUserId())
                || !order.getBusinessId().equals(record.getDecorationConfigId())) {
            throw new BusinessException("退款订单缺少匹配的装饰购买事实");
        }
        if ("refunded".equals(record.getStatus())) {
            return true;
        }
        if (!"active".equals(record.getStatus()) || decorationRecordMapper.markRefunded(order.getId(), refundId) != 1) {
            return false;
        }
        if (userDecorationMapper.deletePaymentEntitlement(record.getUserId(), record.getDecorationId()) != 1) {
            throw new BusinessException("装饰权益状态与购买事实不一致");
        }
        BigDecimal creatorEarnings = record.getCreatorEarnings();
        if (order.getPayeeId() != null && creatorEarnings != null
                && creatorEarnings.compareTo(BigDecimal.ZERO) > 0) {
            earningsDeductService.deductCreatorEarnings(order.getPayeeId(), refundId,
                    creatorEarnings, order.getId(), "decoration_order");
        }
        return true;
    }
}
