


package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.haoran.music.common.config.VipConfig;
import com.haoran.music.common.config.PaymentConfig;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.entity.GiftOrder;
import com.haoran.music.entity.DecorationConfig;
import com.haoran.music.entity.EmojiPackage;
import com.haoran.music.entity.PaidResource;
import com.haoran.music.entity.PaymentOrder;
import com.haoran.music.entity.PlaylistSubscribeOrder;
import com.haoran.music.entity.RewardRecord;
import com.haoran.music.entity.UserEmoji;
import com.haoran.music.entity.UserDecoration;
import com.haoran.music.entity.User;
import com.haoran.music.mapper.EmojiPackageMapper;
import com.haoran.music.mapper.DecorationConfigMapper;
import com.haoran.music.mapper.GiftOrderMapper;
import com.haoran.music.mapper.PaidResourceMapper;
import com.haoran.music.mapper.PaymentOrderMapper;
import com.haoran.music.mapper.PlaylistSubscribeOrderMapper;
import com.haoran.music.mapper.RewardRecordMapper;
import com.haoran.music.mapper.UserEmojiMapper;
import com.haoran.music.mapper.UserDecorationMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.StoreProductPolicyService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Locale;




@Service
public class PaymentProductCatalogService {

    private static final String CURRENCY_CNY = "CNY";

    private final VipConfig vipConfig;
    private final PaidResourceMapper paidResourceMapper;
    private final RewardRecordMapper rewardRecordMapper;
    private final GiftOrderMapper giftOrderMapper;
    private final PlaylistSubscribeOrderMapper playlistSubscribeOrderMapper;
    private final EmojiPackageMapper emojiPackageMapper;
    private final UserEmojiMapper userEmojiMapper;
    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentConfig paymentConfig;
    private final UserMapper userMapper;
    private final DecorationConfigMapper decorationConfigMapper;
    private final UserDecorationMapper userDecorationMapper;
    private final StoreProductPolicyService storeProductPolicyService;

    public PaymentProductCatalogService(VipConfig vipConfig,
                                        PaidResourceMapper paidResourceMapper,
                                        RewardRecordMapper rewardRecordMapper,
                                        GiftOrderMapper giftOrderMapper,
                                        PlaylistSubscribeOrderMapper playlistSubscribeOrderMapper,
                                        EmojiPackageMapper emojiPackageMapper,
                                        UserEmojiMapper userEmojiMapper,
                                        PaymentOrderMapper paymentOrderMapper,
                                        PaymentConfig paymentConfig,
                                        UserMapper userMapper,
                                        DecorationConfigMapper decorationConfigMapper,
                                        UserDecorationMapper userDecorationMapper,
                                        StoreProductPolicyService storeProductPolicyService) {
        this.vipConfig = vipConfig;
        this.paidResourceMapper = paidResourceMapper;
        this.rewardRecordMapper = rewardRecordMapper;
        this.giftOrderMapper = giftOrderMapper;
        this.playlistSubscribeOrderMapper = playlistSubscribeOrderMapper;
        this.emojiPackageMapper = emojiPackageMapper;
        this.userEmojiMapper = userEmojiMapper;
        this.paymentOrderMapper = paymentOrderMapper;
        this.paymentConfig = paymentConfig;
        this.userMapper = userMapper;
        this.decorationConfigMapper = decorationConfigMapper;
        this.userDecorationMapper = userDecorationMapper;
        this.storeProductPolicyService = storeProductPolicyService;
    }

    public PaymentProductSnapshot resolve(Long userId,
                                          String businessType,
                                          Long businessId,
                                          BigDecimal requestedAmount,
                                          Long requestedPayeeId,
                                          String userRemark) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(businessId)) {
            throw new BusinessException("支付商品信息不完整");
        }

        switch (businessType) {
            case "vip":
                return resolveVip(businessId);
            case "purchase":
                return resolvePurchase(userId, businessId, userRemark);
            case "reward":
                return resolveReward(userId, businessId);
            case "gift_vip":
            case "gift_marketplace":
                return resolveGift(userId, businessType, businessId);
            case "subscribe":
                return resolveSubscribe(userId, businessId);
            case "emoji_package":
                return resolveEmojiPackage(userId, businessId);
            case "decoration":
                return resolveDecoration(userId, businessId);
            default:
                throw new BusinessException("不支持的支付商品");
        }
    }

    private PaymentProductSnapshot resolveDecoration(Long userId, Long decorationConfigId) {
        storeProductPolicyService.requirePurchasable("decoration", decorationConfigId);
        DecorationConfig decoration = decorationConfigMapper.selectById(decorationConfigId);
        boolean creatorDecoration = decoration != null && "custom".equals(decoration.getSourceType());
        boolean approvedCreatorDecoration = creatorDecoration
                && "approved".equals(decoration.getReviewStatus())
                && isActiveCreator(decoration.getCreatorId());
        if (decoration == null
                || Integer.valueOf(1).equals(decoration.getDeleted())
                || !Integer.valueOf(1).equals(decoration.getIsEnabled())
                || (creatorDecoration && !approvedCreatorDecoration)
                || !"cash".equals(decoration.getObtainType())
                || ObjectUtils.isEmpty(decoration.getDecorationId())
                || decoration.getCashPrice() == null
                || decoration.getCashPrice() <= 0) {
            throw new BusinessException("装饰不存在或未设置为人民币商品");
        }
        if (userId.equals(decoration.getCreatorId())) {
            throw new BusinessException("不能购买自己的装饰");
        }
        long ownedCount = userDecorationMapper.selectCount(new LambdaQueryWrapper<UserDecoration>()
                .eq(UserDecoration::getUserId, userId)
                .eq(UserDecoration::getDecorationId, decoration.getDecorationId())
                .eq(UserDecoration::getDeleted, 0));
        if (ownedCount > 0) {
            throw new BusinessException("已拥有该装饰");
        }
        long activeOrderCount = paymentOrderMapper.selectCount(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getUserId, userId)
                .eq(PaymentOrder::getBusinessType, "decoration")
                .eq(PaymentOrder::getBusinessId, decorationConfigId)
                .in(PaymentOrder::getStatus, "pending", "submitted", "paid", "success", "completed"));
        if (activeOrderCount > 0) {
            throw new BusinessException("该装饰已有待处理订单");
        }

        Long payeeId = approvedCreatorDecoration ? decoration.getCreatorId() : null;
        PaymentProductSnapshot snapshot = baseSnapshot(
                "decoration:" + decorationConfigId,
                creatorDecoration ? "创作者装饰：" + decoration.getDecorationName() : "官方装饰：" + decoration.getDecorationName(),
                "decoration",
                decorationConfigId,
                BigDecimal.valueOf(decoration.getCashPrice(), 2),
                payeeId)
                .entitlement("decorationId", decoration.getDecorationId())
                .entitlement("decorationType", decoration.getDecorationType())
                .entitlement("contentVersion", decoration.getContentVersion());
        if (payeeId != null) {
            BigDecimal amount = BigDecimal.valueOf(decoration.getCashPrice(), 2);
            snapshot.entitlement("platformFeeRate", paymentConfig.getPlatformFeeRate())
                    .entitlement("platformFee", paymentConfig.calculatePlatformFee(amount))
                    .entitlement("creatorEarnings", paymentConfig.calculateCreatorEarnings(amount));
        }
        return snapshot;
    }

    private PaymentProductSnapshot resolveEmojiPackage(Long userId, Long packageId) {
        storeProductPolicyService.requirePurchasable("emoji_package", packageId);
        EmojiPackage emojiPackage = emojiPackageMapper.selectById(packageId);
        boolean systemPackage = emojiPackage != null && "system".equals(emojiPackage.getType());
        boolean approvedCreatorPackage = emojiPackage != null
                && "custom".equals(emojiPackage.getType())
                && "approved".equals(emojiPackage.getReviewStatus())
                && isActiveCreator(emojiPackage.getCreatorId());
        if (emojiPackage == null
                || !Integer.valueOf(1).equals(emojiPackage.getStatus())
                || (!systemPackage && !approvedCreatorPackage)
                || !"cash".equals(emojiPackage.getPurchaseMode())
                || !Integer.valueOf(0).equals(emojiPackage.getIsFree())
                || emojiPackage.getCashPrice() == null
                || emojiPackage.getCashPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("表情包不存在或未设置为人民币商品");
        }
        if (userId.equals(emojiPackage.getCreatorId())) {
            throw new BusinessException("不能购买自己的表情包");
        }

        long ownedCount = userEmojiMapper.selectCount(new LambdaQueryWrapper<UserEmoji>()
                .eq(UserEmoji::getUserId, userId)
                .eq(UserEmoji::getEmojiPackageId, packageId)
                .eq(UserEmoji::getIsPurchased, 1));
        if (ownedCount > 0) {
            throw new BusinessException("已拥有该表情包");
        }

        long activeOrderCount = paymentOrderMapper.selectCount(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getUserId, userId)
                .eq(PaymentOrder::getBusinessType, "emoji_package")
                .eq(PaymentOrder::getBusinessId, packageId)
                .in(PaymentOrder::getStatus, "pending", "submitted", "paid", "success", "completed"));
        if (activeOrderCount > 0) {
            throw new BusinessException("该表情包已有待处理订单");
        }

        Long payeeId = approvedCreatorPackage ? emojiPackage.getCreatorId() : null;
        PaymentProductSnapshot snapshot = baseSnapshot(
                "emoji-package:" + packageId,
                systemPackage ? "官方表情包：" + emojiPackage.getName() : "创作者表情包：" + emojiPackage.getName(),
                "emoji_package",
                packageId,
                emojiPackage.getCashPrice(),
                payeeId)
                .entitlement("emojiPackageId", packageId)
                .entitlement("purchaseMode", "cash");
        if (payeeId != null) {
            snapshot.entitlement("platformFeeRate", paymentConfig.getPlatformFeeRate())
                    .entitlement("platformFee", paymentConfig.calculatePlatformFee(emojiPackage.getCashPrice()))
                    .entitlement("creatorEarnings", paymentConfig.calculateCreatorEarnings(emojiPackage.getCashPrice()));
        }
        return snapshot;
    }

    private boolean isActiveCreator(Long creatorId) {
        if (creatorId == null) {
            return false;
        }
        User creator = userMapper.selectById(creatorId);
        return creator != null
                && Integer.valueOf(1).equals(creator.getStatus())
                && !Integer.valueOf(1).equals(creator.getDeleted())
                && !Integer.valueOf(1).equals(creator.getIsBanned())
                && Integer.valueOf(1).equals(creator.getIsCreator())
                && "active".equalsIgnoreCase(creator.getCreatorStatus());
    }

    private PaymentProductSnapshot resolveVip(Long productId) {
        String vipType;
        if (Long.valueOf(1L).equals(productId)) {
            vipType = "month";
        } else if (Long.valueOf(3L).equals(productId)) {
            vipType = "quarter";
        } else if (Long.valueOf(12L).equals(productId)) {
            vipType = "year";
        } else {
            throw new BusinessException("不支持的VIP商品");
        }

        VipConfig.VipPrice price = vipConfig.getPrice(vipType);
        BigDecimal amount = vipConfig.getPriceInYuan(vipType);
        if (price == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0
                || price.getDays() == null || price.getDays() <= 0) {
            throw new BusinessException("VIP商品配置异常");
        }

        PaymentProductSnapshot snapshot = baseSnapshot(
                "vip:" + vipType, price.getName(), "vip", productId, amount, null);
        return snapshot.entitlement("vipType", vipType)
                .entitlement("days", price.getDays());
    }

    private PaymentProductSnapshot resolvePurchase(Long userId, Long resourceId, String userRemark) {
        Long paidResourceId = remarkLong(userRemark, "paidResourceId");
        if (paidResourceId == null) {
            throw new BusinessException("请通过付费资源接口创建订单");
        }

        PaidResource resource = paidResourceMapper.selectById(paidResourceId);
        if (resource == null
                || !resourceId.equals(resource.getResourceId())
                || !Integer.valueOf(1).equals(resource.getIsEnabled())
                || !"approved".equals(resource.getStatus())
                || ObjectUtils.isEmpty(resource.getResourceType())
                || ObjectUtils.isEmpty(resource.getOwnerId())
                || resource.getPrice() == null
                || resource.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("资源不存在或未设置为付费");
        }
        if (userId.equals(resource.getOwnerId())) {
            throw new BusinessException("不能购买自己的资源");
        }

        PaymentProductSnapshot snapshot = baseSnapshot(
                "paid-resource:" + paidResourceId,
                "付费资源 " + resource.getResourceType(),
                "purchase",
                resourceId,
                resource.getPrice(),
                resource.getOwnerId());
        return snapshot.entitlement("paidResourceId", paidResourceId)
                .entitlement("resourceType", resource.getResourceType())
                .entitlement("resourceId", resource.getResourceId())
                .entitlement("subscribePeriod", resource.getSubscribePeriod());
    }

    private PaymentProductSnapshot resolveReward(Long userId, Long rewardId) {
        RewardRecord reward = rewardRecordMapper.selectById(rewardId);
        if (reward == null
                || !userId.equals(reward.getUserId())
                || !"pending".equals(reward.getStatus())
                || reward.getPaymentOrderId() != null
                || ObjectUtils.isEmpty(reward.getCreatorId())
                || reward.getAmount() == null
                || reward.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("打赏业务单不存在或状态异常");
        }

        PaymentProductSnapshot snapshot = baseSnapshot(
                "reward:" + rewardId,
                "创作者打赏",
                "reward",
                rewardId,
                reward.getAmount(),
                reward.getCreatorId());
        return snapshot.entitlement("creatorId", reward.getCreatorId())
                .entitlement("resourceType", reward.getResourceType())
                .entitlement("resourceId", reward.getResourceId())
                .entitlement("message", reward.getMessage())
                .entitlement("anonymous", Integer.valueOf(1).equals(reward.getIsAnonymous()));
    }

    private PaymentProductSnapshot resolveGift(Long userId, String businessType, Long giftOrderId) {
        GiftOrder giftOrder = giftOrderMapper.selectById(giftOrderId);
        String expectedGiftType = "gift_vip".equals(businessType) ? "vip" : "marketplace_item";
        if (giftOrder == null
                || !userId.equals(giftOrder.getGiverId())
                || !expectedGiftType.equals(giftOrder.getGiftType())
                || !"pending_payment".equals(giftOrder.getStatus())
                || giftOrder.getPaymentOrderId() != null
                || giftOrder.getAmount() == null
                || giftOrder.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("赠礼业务单不存在或状态异常");
        }

        Long payeeId = "gift_marketplace".equals(businessType) ? giftOrder.getSellerId() : null;
        PaymentProductSnapshot snapshot = baseSnapshot(
                "gift:" + giftOrderId,
                ObjectUtils.isEmpty(giftOrder.getTargetName()) ? "赠礼" : giftOrder.getTargetName(),
                businessType,
                giftOrderId,
                giftOrder.getAmount(),
                payeeId);
        return snapshot.entitlement("giftType", giftOrder.getGiftType())
                .entitlement("receiverId", giftOrder.getReceiverId())
                .entitlement("targetType", giftOrder.getTargetType())
                .entitlement("targetId", giftOrder.getTargetId())
                .entitlement("vipType", giftOrder.getVipType())
                .entitlement("vipDays", giftOrder.getVipDays());
    }

    private PaymentProductSnapshot resolveSubscribe(Long userId, Long subscribeOrderId) {
        PlaylistSubscribeOrder order = playlistSubscribeOrderMapper.selectById(subscribeOrderId);
        if (order == null
                || !userId.equals(order.getUserId())
                || !"pending_payment".equals(order.getStatus())
                || order.getPaymentOrderId() != null
                || order.getAmount() == null
                || order.getAmount().compareTo(BigDecimal.ZERO) <= 0
                || order.getDays() == null
                || order.getDays() <= 0
                || ObjectUtils.isEmpty(order.getCreatorId())) {
            throw new BusinessException("订阅业务单不存在或状态异常");
        }

        PaymentProductSnapshot snapshot = baseSnapshot(
                "playlist-subscribe:" + subscribeOrderId,
                "歌单付费订阅",
                "subscribe",
                subscribeOrderId,
                order.getAmount(),
                order.getCreatorId());
        return snapshot.entitlement("playlistId", order.getPlaylistId())
                .entitlement("subscribeType", order.getSubscribeType())
                .entitlement("days", order.getDays())
                .entitlement("autoRenew", Integer.valueOf(1).equals(order.getAutoRenew()));
    }

    private PaymentProductSnapshot baseSnapshot(String productCode,
                                                String productName,
                                                String businessType,
                                                Long businessId,
                                                BigDecimal amount,
                                                Long payeeId) {
        PaymentProductSnapshot snapshot = new PaymentProductSnapshot();
        snapshot.setVersion(PaymentProductSnapshot.CURRENT_VERSION);
        snapshot.setProductCode(productCode);
        snapshot.setProductName(productName);
        snapshot.setBusinessType(businessType);
        snapshot.setBusinessId(businessId);
        snapshot.setAmount(amount);
        snapshot.setCurrency(CURRENCY_CNY);
        snapshot.setPayeeId(payeeId);
        return snapshot;
    }

    private Long remarkLong(String remark, String key) {
        String value = remarkValue(remark, key);
        if (ObjectUtils.isEmpty(value)) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            throw new BusinessException("支付商品标识格式错误");
        }
    }

    private String remarkValue(String remark, String key) {
        if (ObjectUtils.isEmpty(remark)) {
            return null;
        }
        String prefix = key.toLowerCase(Locale.ROOT) + "=";
        for (String part : remark.split(";")) {
            String normalized = part == null ? "" : part.trim();
            if (normalized.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                return normalized.substring(prefix.length()).trim();
            }
        }
        return null;
    }
}
