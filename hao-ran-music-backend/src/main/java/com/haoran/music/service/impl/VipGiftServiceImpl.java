package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.haoran.music.common.config.VipConfig;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.PaymentOrderStatusUtil;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.dto.gift.MarketplaceGiftCreateDTO;
import com.haoran.music.dto.gift.VipGiftCreateDTO;
import com.haoran.music.entity.GiftOrder;
import com.haoran.music.entity.MarketplaceGiftRecord;
import com.haoran.music.entity.MarketplaceItem;
import com.haoran.music.entity.PaymentOrder;
import com.haoran.music.entity.User;
import com.haoran.music.entity.VipGiftRecord;
import com.haoran.music.mapper.GiftOrderMapper;
import com.haoran.music.mapper.MarketplaceGiftRecordMapper;
import com.haoran.music.mapper.MarketplaceItemMapper;
import com.haoran.music.mapper.PaymentOrderMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.mapper.VipGiftRecordMapper;
import com.haoran.music.service.PaymentOrderService;
import com.haoran.music.service.UserVipService;
import com.haoran.music.service.VipGiftService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

   
                      
                         
   
@Service
@Slf4j
public class VipGiftServiceImpl implements VipGiftService {

    private static final String GIFT_TYPE_VIP = "vip";
    private static final String GIFT_TYPE_MARKETPLACE_ITEM = "marketplace_item";
    private static final String BUSINESS_TYPE_GIFT_VIP = "gift_vip";
    private static final String BUSINESS_TYPE_GIFT_MARKETPLACE = "gift_marketplace";
    private static final String TARGET_TYPE_VIP = "vip";
    private static final String TARGET_TYPE_MARKETPLACE_ITEM = "marketplace_item";
    private static final String STATUS_PENDING_PAYMENT = "pending_payment";
    private static final String STATUS_PAID = "paid";
    private static final String STATUS_COMPLETED = "completed";
    private static final String STATUS_BLOCKED = "blocked";
    private static final String STATUS_COMPENSATION_REQUIRED = "compensation_required";
    private static final String STATUS_FAILED = "failed";
    private static final String RECORD_STATUS_APPLIED = "applied";
    private static final String RECORD_STATUS_BLOCKED = "blocked";
    private static final String RECORD_STATUS_FAILED = "failed";
    private static final String CURRENCY_CNY = "CNY";

    private final GiftOrderMapper giftOrderMapper;
    private final VipGiftRecordMapper vipGiftRecordMapper;
    private final MarketplaceGiftRecordMapper marketplaceGiftRecordMapper;
    private final MarketplaceItemMapper marketplaceItemMapper;
    private final UserMapper userMapper;
    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentOrderService paymentOrderService;
    private final UserVipService userVipService;
    private final VipConfig vipConfig;

    public VipGiftServiceImpl(GiftOrderMapper giftOrderMapper,
                              VipGiftRecordMapper vipGiftRecordMapper,
                              MarketplaceGiftRecordMapper marketplaceGiftRecordMapper,
                              MarketplaceItemMapper marketplaceItemMapper,
                              UserMapper userMapper,
                              PaymentOrderMapper paymentOrderMapper,
                              @Lazy PaymentOrderService paymentOrderService,
                              UserVipService userVipService,
                              VipConfig vipConfig) {
        this.giftOrderMapper = giftOrderMapper;
        this.vipGiftRecordMapper = vipGiftRecordMapper;
        this.marketplaceGiftRecordMapper = marketplaceGiftRecordMapper;
        this.marketplaceItemMapper = marketplaceItemMapper;
        this.userMapper = userMapper;
        this.paymentOrderMapper = paymentOrderMapper;
        this.paymentOrderService = paymentOrderService;
        this.userVipService = userVipService;
        this.vipConfig = vipConfig;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createVipGift(Long giverId, VipGiftCreateDTO dto) {
        if (ObjectUtils.isEmpty(giverId)) {
            throw new BusinessException("请先登录");
        }
        if (ObjectUtils.isEmpty(dto) || ObjectUtils.isEmpty(dto.getReceiverId())) {
            throw new BusinessException("收礼用户不能为空");
        }
        User giver = userMapper.selectById(giverId);
        UserAccountStatusUtil.requireCanInteract(giver, "赠送VIP");

        User receiver = userMapper.selectById(dto.getReceiverId());
        if (ObjectUtils.isEmpty(receiver) || ObjectUtils.isNotEmpty(receiver.getDeleted()) && receiver.getDeleted() != 0) {
            throw new BusinessException("收礼用户不存在");
        }
        if (giverId.equals(dto.getReceiverId())) {
            throw new BusinessException("不能给自己赠送VIP");
        }

        String vipType = normalizeVipType(dto.getVipType());
        VipConfig.VipPrice price = vipConfig.getPrice(vipType);
        if (ObjectUtils.isEmpty(price)) {
            throw new BusinessException("不支持的VIP类型");
        }
        String message = normalizeMessage(dto.getGiftMessage());

        GiftOrder giftOrder = new GiftOrder();
        giftOrder.setGiftNo(generateGiftNo());
        giftOrder.setGiftType(GIFT_TYPE_VIP);
        giftOrder.setGiverId(giverId);
        giftOrder.setReceiverId(dto.getReceiverId());
        giftOrder.setTargetType(TARGET_TYPE_VIP);
        giftOrder.setTargetName(price.getName());
        giftOrder.setVipType(vipType);
        giftOrder.setVipLevel(resolveVipLevel(vipType));
        giftOrder.setVipDays(price.getDays());
        giftOrder.setAmount(vipConfig.getPriceInYuan(vipType));
        giftOrder.setCurrency(CURRENCY_CNY);
        giftOrder.setGiftMessage(message);
        giftOrder.setStatus(STATUS_PENDING_PAYMENT);
        giftOrder.setDeleted(0);
        giftOrderMapper.insert(giftOrder);

        Map<String, Object> payment = paymentOrderService.createOrder(
                giverId,
                BUSINESS_TYPE_GIFT_VIP,
                giftOrder.getId(),
                giftOrder.getAmount(),
                null,
                buildPaymentRemark(giftOrder),
                "gift-vip:" + giftOrder.getId());
        Long paymentOrderId = resolvePaymentOrderId(payment);
        if (ObjectUtils.isEmpty(paymentOrderId)) {
            throw new BusinessException("赠礼支付订单创建失败");
        }
        giftOrder.setPaymentOrderId(paymentOrderId);
        giftOrderMapper.updateById(giftOrder);

        Map<String, Object> result = toGiftMap(giftOrder);
        result.put("paymentOrder", payment);
        result.put("receiverAccountAvailable", UserAccountStatusUtil.canInteract(receiver));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createMarketplaceGift(Long giverId, MarketplaceGiftCreateDTO dto) {
        if (ObjectUtils.isEmpty(giverId)) {
            throw new BusinessException("请先登录");
        }
        if (ObjectUtils.isEmpty(dto) || ObjectUtils.isEmpty(dto.getReceiverId())) {
            throw new BusinessException("收礼用户不能为空");
        }
        if (ObjectUtils.isEmpty(dto.getItemId())) {
            throw new BusinessException("商品不能为空");
        }

        User giver = userMapper.selectById(giverId);
        UserAccountStatusUtil.requireCanInteract(giver, "赠送商城商品");

        User receiver = userMapper.selectById(dto.getReceiverId());
        if (ObjectUtils.isEmpty(receiver) || ObjectUtils.isNotEmpty(receiver.getDeleted()) && receiver.getDeleted() != 0) {
            throw new BusinessException("收礼用户不存在");
        }
        if (giverId.equals(dto.getReceiverId())) {
            throw new BusinessException("不能给自己赠送商品");
        }

        MarketplaceItem item = marketplaceItemMapper.selectById(dto.getItemId());
        if (ObjectUtils.isEmpty(item) || Boolean.TRUE.equals(item.getIsDeleted())) {
            throw new BusinessException("商品不存在");
        }
        if (!"available".equals(item.getStatus())) {
            throw new BusinessException("商品当前不可赠送");
        }
        if (ObjectUtils.isEmpty(item.getSellerId())) {
            throw new BusinessException("商品卖家信息缺失");
        }
        if (giverId.equals(item.getSellerId())) {
            throw new BusinessException("不能购买自己发布的商品作为赠礼");
        }
        UserAccountStatusUtil.requireCanInteract(item.getSellerId(), userMapper::selectById, "接收商城赠礼款项");
        if (ObjectUtils.isEmpty(item.getPrice()) || item.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("商品价格异常，无法赠送");
        }

        String message = normalizeMessage(dto.getGiftMessage());
        GiftOrder giftOrder = new GiftOrder();
        giftOrder.setGiftNo(generateGiftNo());
        giftOrder.setGiftType(GIFT_TYPE_MARKETPLACE_ITEM);
        giftOrder.setGiverId(giverId);
        giftOrder.setReceiverId(dto.getReceiverId());
        giftOrder.setTargetType(TARGET_TYPE_MARKETPLACE_ITEM);
        giftOrder.setTargetId(item.getId());
        giftOrder.setTargetName(item.getTitle());
        giftOrder.setSellerId(item.getSellerId());
        giftOrder.setAmount(item.getPrice());
        giftOrder.setCurrency(CURRENCY_CNY);
        giftOrder.setGiftMessage(message);
        giftOrder.setStatus(STATUS_PENDING_PAYMENT);
        giftOrder.setDeleted(0);
        giftOrderMapper.insert(giftOrder);

        Map<String, Object> payment = paymentOrderService.createOrder(
                giverId,
                BUSINESS_TYPE_GIFT_MARKETPLACE,
                giftOrder.getId(),
                giftOrder.getAmount(),
                item.getSellerId(),
                buildMarketplacePaymentRemark(giftOrder),
                "gift-marketplace:" + giftOrder.getId());
        Long paymentOrderId = resolvePaymentOrderId(payment);
        if (ObjectUtils.isEmpty(paymentOrderId)) {
            throw new BusinessException("赠礼支付订单创建失败");
        }
        giftOrder.setPaymentOrderId(paymentOrderId);
        giftOrderMapper.updateById(giftOrder);

        Map<String, Object> result = toGiftMap(giftOrder);
        result.put("paymentOrder", payment);
        result.put("receiverAccountAvailable", UserAccountStatusUtil.canInteract(receiver));
        return result;
    }

    @Override
    public Map<String, Object> getGiftDetail(Long userId, Long giftOrderId) {
        GiftOrder order = findGiftOrder(giftOrderId);
        ensureGiftVisible(order, userId);
        return toGiftMap(order);
    }

    @Override
    public Map<String, Object> getSentGifts(Long userId, String status, Integer page, Integer size) {
        return queryGiftPage(userId, true, status, page, size);
    }

    @Override
    public Map<String, Object> getReceivedGifts(Long userId, String status, Integer page, Integer size) {
        return queryGiftPage(userId, false, status, page, size);
    }

    @Override
    public Map<String, Object> getAdminGiftOrders(String status, Integer page, Integer size) {
        Page<GiftOrder> pageParam = new Page<>(safePage(page), safeSize(size));
        LambdaQueryWrapper<GiftOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GiftOrder::getDeleted, 0);
        if (ObjectUtils.isNotEmpty(status)) {
            wrapper.eq(GiftOrder::getStatus, status.trim());
        }
        wrapper.orderByDesc(GiftOrder::getCreateTime);
        Page<GiftOrder> resultPage = giftOrderMapper.selectPage(pageParam, wrapper);
        return toPageMap(resultPage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean completeVipGiftByPaymentOrder(Long paymentOrderId) {
        GiftOrder giftOrder = findGiftOrderByPaymentOrder(paymentOrderId);
        if (ObjectUtils.isEmpty(giftOrder)) {
            return false;
        }
        if (!GIFT_TYPE_VIP.equals(giftOrder.getGiftType())) {
            return false;
        }
        if (hasAppliedVipGiftByPaymentOrder(paymentOrderId)) {
            markGiftCompleted(giftOrder, null);
            markPaymentCompletion(giftOrder.getPaymentOrderId(), true, null);
            return true;
        }
        markGiftPaid(giftOrder);

        User receiver = userMapper.selectById(giftOrder.getReceiverId());
        if (!UserAccountStatusUtil.canInteract(receiver)) {
            String reason = UserAccountStatusUtil.targetUnavailableMessage(receiver) + "，VIP赠礼权益暂缓发放";
            upsertGiftRecord(giftOrder, RECORD_STATUS_BLOCKED, null, null, null, reason);
            markGiftBlocked(giftOrder, reason);
            markPaymentCompletion(giftOrder.getPaymentOrderId(), false, reason);
            return false;
        }

        LocalDateTime startTime = LocalDateTime.now();
        LocalDateTime endTime = startTime.plusDays(giftOrder.getVipDays());
        try {
            userVipService.grantVip(giftOrder.getReceiverId(), giftOrder.getVipLevel(),
                    giftOrder.getVipDays(), "gift");
            upsertGiftRecord(giftOrder, RECORD_STATUS_APPLIED, startTime, endTime, null, null);
            markGiftCompleted(giftOrder, null);
            markPaymentCompletion(giftOrder.getPaymentOrderId(), true, null);
            return true;
        } catch (Exception e) {
            log.error("VIP赠礼权益发放失败，回滚后等待重试: giftOrderId={}, error={}",
                    giftOrder.getId(), e.getClass().getSimpleName());
            throw new BusinessException("VIP赠礼权益发放失败，请稍后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean completeMarketplaceGiftByPaymentOrder(Long paymentOrderId) {
        GiftOrder giftOrder = findGiftOrderByPaymentOrder(paymentOrderId);
        if (ObjectUtils.isEmpty(giftOrder)) {
            return false;
        }
        if (!GIFT_TYPE_MARKETPLACE_ITEM.equals(giftOrder.getGiftType())) {
            return false;
        }
        if (hasAppliedMarketplaceGiftByPaymentOrder(paymentOrderId)) {
            markGiftCompleted(giftOrder, null);
            markPaymentCompletion(giftOrder.getPaymentOrderId(), true, null);
            return true;
        }
        markGiftPaid(giftOrder);

        User receiver = userMapper.selectById(giftOrder.getReceiverId());
        if (!UserAccountStatusUtil.canInteract(receiver)) {
            String reason = UserAccountStatusUtil.targetUnavailableMessage(receiver) + "，商城赠礼交付暂缓";
            upsertMarketplaceGiftRecord(giftOrder, RECORD_STATUS_BLOCKED, null, reason);
            markGiftBlocked(giftOrder, reason);
            markPaymentCompletion(giftOrder.getPaymentOrderId(), false, reason);
            return false;
        }

        MarketplaceItem item = marketplaceItemMapper.selectById(giftOrder.getTargetId());
        if (ObjectUtils.isEmpty(item) || Boolean.TRUE.equals(item.getIsDeleted())) {
            String reason = "商品不存在或已删除，商城赠礼交付暂缓";
            upsertMarketplaceGiftRecord(giftOrder, RECORD_STATUS_BLOCKED, null, reason);
            markGiftBlocked(giftOrder, reason);
            markPaymentCompletion(giftOrder.getPaymentOrderId(), false, reason);
            return false;
        }
        if (!"available".equals(item.getStatus())) {
            String reason = "商品当前状态不可交付: " + item.getStatus();
            upsertMarketplaceGiftRecord(giftOrder, RECORD_STATUS_BLOCKED, null, reason);
            markGiftBlocked(giftOrder, reason);
            markPaymentCompletion(giftOrder.getPaymentOrderId(), false, reason);
            return false;
        }

        try {
            LocalDateTime now = LocalDateTime.now();
            LambdaUpdateWrapper<MarketplaceItem> itemWrapper = new LambdaUpdateWrapper<>();
            itemWrapper.eq(MarketplaceItem::getId, item.getId())
                    .eq(MarketplaceItem::getIsDeleted, false)
                    .eq(MarketplaceItem::getStatus, "available")
                    .set(MarketplaceItem::getStatus, "sold")
                    .set(MarketplaceItem::getSoldTime, now);
            if (marketplaceItemMapper.update(null, itemWrapper) != 1) {
                String reason = "商品状态已变化，商城赠礼交付暂缓";
                upsertMarketplaceGiftRecord(giftOrder, RECORD_STATUS_BLOCKED, null, reason);
                markGiftBlocked(giftOrder, reason);
                markPaymentCompletion(giftOrder.getPaymentOrderId(), false, reason);
                return false;
            }

            upsertMarketplaceGiftRecord(giftOrder, RECORD_STATUS_APPLIED, null, null);
            markGiftCompleted(giftOrder, now);
            markPaymentCompletion(giftOrder.getPaymentOrderId(), true, null);
            return true;
        } catch (Exception e) {
            log.error("商城赠礼交付失败，回滚后等待重试: giftOrderId={}, error={}",
                    giftOrder.getId(), e.getClass().getSimpleName());
            throw new BusinessException("商城赠礼交付失败，请稍后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean retryVipGift(Long giftOrderId, Long operatorId) {
        if (ObjectUtils.isEmpty(operatorId)) {
            throw new BusinessException("当前操作人不存在");
        }
        GiftOrder giftOrder = findGiftOrder(giftOrderId);
        if (ObjectUtils.isEmpty(giftOrder.getPaymentOrderId())) {
            throw new BusinessException("赠礼尚未关联支付订单");
        }
        PaymentOrder paymentOrder = paymentOrderMapper.selectById(giftOrder.getPaymentOrderId());
        if (ObjectUtils.isEmpty(paymentOrder) || !PaymentOrderStatusUtil.isPaidStatus(paymentOrder.getStatus())) {
            throw new BusinessException("只有已审核通过的赠礼订单可以补发");
        }
        boolean completed = completeVipGiftByPaymentOrder(giftOrder.getPaymentOrderId());
        if (completed) {
            markLatestRecordOperator(giftOrder.getId(), operatorId);
        }
        return completed;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean retryGift(Long giftOrderId, Long operatorId) {
        if (ObjectUtils.isEmpty(operatorId)) {
            throw new BusinessException("当前操作人不存在");
        }
        GiftOrder giftOrder = findGiftOrder(giftOrderId);
        if (GIFT_TYPE_VIP.equals(giftOrder.getGiftType())) {
            return retryVipGift(giftOrderId, operatorId);
        }
        if (!GIFT_TYPE_MARKETPLACE_ITEM.equals(giftOrder.getGiftType())) {
            throw new BusinessException("不支持的赠礼类型");
        }
        if (ObjectUtils.isEmpty(giftOrder.getPaymentOrderId())) {
            throw new BusinessException("赠礼尚未关联支付订单");
        }
        PaymentOrder paymentOrder = paymentOrderMapper.selectById(giftOrder.getPaymentOrderId());
        if (ObjectUtils.isEmpty(paymentOrder) || !PaymentOrderStatusUtil.isPaidStatus(paymentOrder.getStatus())) {
            throw new BusinessException("只有已审核通过的赠礼订单可以补发");
        }
        boolean completed = completeMarketplaceGiftByPaymentOrder(giftOrder.getPaymentOrderId());
        if (completed) {
            markLatestMarketplaceRecordOperator(giftOrder.getId(), operatorId);
        }
        return completed;
    }

    @Override
    public boolean hasAppliedVipGiftByPaymentOrder(Long paymentOrderId) {
        if (ObjectUtils.isEmpty(paymentOrderId)) {
            return false;
        }
        return vipGiftRecordMapper.selectCount(new LambdaQueryWrapper<VipGiftRecord>()
                .eq(VipGiftRecord::getPaymentOrderId, paymentOrderId)
                .eq(VipGiftRecord::getStatus, RECORD_STATUS_APPLIED)
                .eq(VipGiftRecord::getDeleted, 0)) > 0;
    }

    @Override
    public boolean hasAppliedMarketplaceGiftByPaymentOrder(Long paymentOrderId) {
        if (ObjectUtils.isEmpty(paymentOrderId)) {
            return false;
        }
        return marketplaceGiftRecordMapper.selectCount(new LambdaQueryWrapper<MarketplaceGiftRecord>()
                .eq(MarketplaceGiftRecord::getPaymentOrderId, paymentOrderId)
                .eq(MarketplaceGiftRecord::getStatus, RECORD_STATUS_APPLIED)
                .eq(MarketplaceGiftRecord::getDeleted, 0)) > 0;
    }

    private Map<String, Object> queryGiftPage(Long userId, boolean sent, String status, Integer page, Integer size) {
        if (ObjectUtils.isEmpty(userId)) {
            throw new BusinessException("请先登录");
        }
        Page<GiftOrder> pageParam = new Page<>(safePage(page), safeSize(size));
        LambdaQueryWrapper<GiftOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(sent, GiftOrder::getGiverId, userId)
                .eq(!sent, GiftOrder::getReceiverId, userId)
                .eq(GiftOrder::getDeleted, 0);
        if (ObjectUtils.isNotEmpty(status)) {
            wrapper.eq(GiftOrder::getStatus, status.trim());
        }
        wrapper.orderByDesc(GiftOrder::getCreateTime);
        Page<GiftOrder> resultPage = giftOrderMapper.selectPage(pageParam, wrapper);
        return toPageMap(resultPage);
    }

    private GiftOrder findGiftOrder(Long giftOrderId) {
        if (ObjectUtils.isEmpty(giftOrderId)) {
            throw new BusinessException("赠礼订单ID不能为空");
        }
        GiftOrder order = giftOrderMapper.selectById(giftOrderId);
        if (ObjectUtils.isEmpty(order) || ObjectUtils.isNotEmpty(order.getDeleted()) && order.getDeleted() != 0) {
            throw new BusinessException("赠礼订单不存在");
        }
        return order;
    }

    private GiftOrder findGiftOrderByPaymentOrder(Long paymentOrderId) {
        if (ObjectUtils.isEmpty(paymentOrderId)) {
            return null;
        }
        return giftOrderMapper.selectByPaymentOrderForUpdate(paymentOrderId);
    }

    private void ensureGiftVisible(GiftOrder order, Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            throw new BusinessException("请先登录");
        }
        if (!userId.equals(order.getGiverId()) && !userId.equals(order.getReceiverId())) {
            throw new BusinessException("无权查看此赠礼订单");
        }
    }

    private void upsertGiftRecord(GiftOrder order, String status, LocalDateTime startTime,
                                  LocalDateTime endTime, Long operatorId, String errorMessage) {
        VipGiftRecord existing = vipGiftRecordMapper.selectOne(new LambdaQueryWrapper<VipGiftRecord>()
                .eq(VipGiftRecord::getGiftOrderId, order.getId())
                .eq(VipGiftRecord::getDeleted, 0)
                .last("LIMIT 1"));
        VipGiftRecord record = ObjectUtils.isEmpty(existing) ? new VipGiftRecord() : existing;
        record.setGiftOrderId(order.getId());
        record.setPaymentOrderId(order.getPaymentOrderId());
        record.setGiverId(order.getGiverId());
        record.setReceiverId(order.getReceiverId());
        record.setVipType(order.getVipType());
        record.setVipLevel(order.getVipLevel());
        record.setVipDays(order.getVipDays());
        record.setStartTime(startTime);
        record.setEndTime(endTime);
        record.setStatus(status);
        record.setApplyTime(RECORD_STATUS_APPLIED.equals(status) ? LocalDateTime.now() : null);
        record.setOperatorId(operatorId);
        record.setErrorMessage(errorMessage);
        record.setDeleted(0);
        if (ObjectUtils.isEmpty(record.getId())) {
            vipGiftRecordMapper.insert(record);
        } else {
            vipGiftRecordMapper.updateById(record);
        }
    }

    private void upsertMarketplaceGiftRecord(GiftOrder order, String status, Long operatorId, String errorMessage) {
        MarketplaceGiftRecord existing = marketplaceGiftRecordMapper.selectOne(new LambdaQueryWrapper<MarketplaceGiftRecord>()
                .eq(MarketplaceGiftRecord::getGiftOrderId, order.getId())
                .eq(MarketplaceGiftRecord::getDeleted, 0)
                .last("LIMIT 1"));
        MarketplaceGiftRecord record = ObjectUtils.isEmpty(existing) ? new MarketplaceGiftRecord() : existing;
        record.setGiftOrderId(order.getId());
        record.setPaymentOrderId(order.getPaymentOrderId());
        record.setMarketplaceItemId(order.getTargetId());
        record.setGiverId(order.getGiverId());
        record.setReceiverId(order.getReceiverId());
        record.setSellerId(order.getSellerId());
        record.setStatus(status);
        record.setApplyTime(RECORD_STATUS_APPLIED.equals(status) ? LocalDateTime.now() : null);
        record.setOperatorId(operatorId);
        record.setErrorMessage(errorMessage);
        record.setDeleted(0);
        if (ObjectUtils.isEmpty(record.getId())) {
            marketplaceGiftRecordMapper.insert(record);
        } else {
            marketplaceGiftRecordMapper.updateById(record);
        }
    }

    private void markGiftPaid(GiftOrder order) {
        LambdaUpdateWrapper<GiftOrder> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(GiftOrder::getId, order.getId())
                .eq(GiftOrder::getDeleted, 0)
                .in(GiftOrder::getStatus, STATUS_PENDING_PAYMENT, STATUS_PAID, STATUS_BLOCKED,
                        STATUS_COMPENSATION_REQUIRED, STATUS_FAILED)
                .set(GiftOrder::getStatus, STATUS_PAID)
                .set(GiftOrder::getCompletionError, null)
                .set(GiftOrder::getUpdateTime, LocalDateTime.now());
        giftOrderMapper.update(null, wrapper);
    }

    private void markGiftCompleted(GiftOrder order, LocalDateTime completedAt) {
        LambdaUpdateWrapper<GiftOrder> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(GiftOrder::getId, order.getId())
                .eq(GiftOrder::getDeleted, 0)
                .set(GiftOrder::getStatus, STATUS_COMPLETED)
                .set(GiftOrder::getCompletionError, null)
                .set(GiftOrder::getCompletedAt, ObjectUtils.isEmpty(completedAt) ? LocalDateTime.now() : completedAt)
                .set(GiftOrder::getUpdateTime, LocalDateTime.now());
        giftOrderMapper.update(null, wrapper);
    }

    private void markGiftBlocked(GiftOrder order, String reason) {
        markGiftIncomplete(order, STATUS_COMPENSATION_REQUIRED, reason);
    }

    private void markGiftFailed(GiftOrder order, String reason) {
        markGiftIncomplete(order, STATUS_FAILED, reason);
    }

    private void markGiftIncomplete(GiftOrder order, String status, String reason) {
        LambdaUpdateWrapper<GiftOrder> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(GiftOrder::getId, order.getId())
                .eq(GiftOrder::getDeleted, 0)
                .set(GiftOrder::getStatus, status)
                .set(GiftOrder::getCompletionError, truncate(reason, 500))
                .set(GiftOrder::getCompletedAt, null)
                .set(GiftOrder::getUpdateTime, LocalDateTime.now());
        giftOrderMapper.update(null, wrapper);
    }

    private void markPaymentCompletion(Long paymentOrderId, boolean completed, String error) {
        if (ObjectUtils.isEmpty(paymentOrderId)) {
            return;
        }
        LambdaUpdateWrapper<PaymentOrder> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(PaymentOrder::getId, paymentOrderId)
                .eq(PaymentOrder::getDeleted, 0)
                .set(PaymentOrder::getCompletionStatus, completed ? STATUS_COMPLETED : STATUS_BLOCKED)
                .set(PaymentOrder::getCompletionTime, completed ? LocalDateTime.now() : null)
                .set(PaymentOrder::getCompletionError, completed ? null : truncate(error, 500))
                .set(PaymentOrder::getUpdateTime, LocalDateTime.now());
        paymentOrderMapper.update(null, wrapper);
    }

    private void markLatestRecordOperator(Long giftOrderId, Long operatorId) {
        LambdaUpdateWrapper<VipGiftRecord> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(VipGiftRecord::getGiftOrderId, giftOrderId)
                .eq(VipGiftRecord::getDeleted, 0)
                .set(VipGiftRecord::getOperatorId, operatorId)
                .set(VipGiftRecord::getUpdateTime, LocalDateTime.now());
        vipGiftRecordMapper.update(null, wrapper);
    }

    private void markLatestMarketplaceRecordOperator(Long giftOrderId, Long operatorId) {
        LambdaUpdateWrapper<MarketplaceGiftRecord> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(MarketplaceGiftRecord::getGiftOrderId, giftOrderId)
                .eq(MarketplaceGiftRecord::getDeleted, 0)
                .set(MarketplaceGiftRecord::getOperatorId, operatorId)
                .set(MarketplaceGiftRecord::getUpdateTime, LocalDateTime.now());
        marketplaceGiftRecordMapper.update(null, wrapper);
    }

    private Map<String, Object> toPageMap(Page<GiftOrder> page) {
        Map<String, Object> result = new HashMap<>();
        result.put("records", page.getRecords());
        result.put("total", page.getTotal());
        result.put("current", page.getCurrent());
        result.put("pages", page.getPages());
        result.put("size", page.getSize());
        return result;
    }

    private Map<String, Object> toGiftMap(GiftOrder order) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", order.getId());
        result.put("giftOrderId", order.getId());
        result.put("giftNo", order.getGiftNo());
        result.put("giftType", order.getGiftType());
        result.put("giverId", order.getGiverId());
        result.put("receiverId", order.getReceiverId());
        result.put("targetType", order.getTargetType());
        result.put("targetId", order.getTargetId());
        result.put("targetName", order.getTargetName());
        result.put("sellerId", order.getSellerId());
        result.put("vipType", order.getVipType());
        result.put("vipLevel", order.getVipLevel());
        result.put("vipDays", order.getVipDays());
        result.put("amount", order.getAmount());
        result.put("currency", order.getCurrency());
        result.put("giftMessage", order.getGiftMessage());
        result.put("paymentOrderId", order.getPaymentOrderId());
        result.put("status", order.getStatus());
        result.put("completionError", order.getCompletionError());
        result.put("completedAt", order.getCompletedAt());
        result.put("createTime", order.getCreateTime());
        result.put("updateTime", order.getUpdateTime());
        return result;
    }

    private Long resolvePaymentOrderId(Map<String, Object> payment) {
        Object value = ObjectUtils.isEmpty(payment) ? null : payment.get("orderId");
        if (ObjectUtils.isEmpty(value)) {
            value = ObjectUtils.isEmpty(payment) ? null : payment.get("id");
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String && !((String) value).trim().isEmpty()) {
            return Long.valueOf(((String) value).trim());
        }
        return null;
    }

    private String buildPaymentRemark(GiftOrder order) {
        return "giftOrderId=" + order.getId()
                + ";giftNo=" + order.getGiftNo()
                + ";receiverId=" + order.getReceiverId()
                + ";vipType=" + order.getVipType();
    }

    private String buildMarketplacePaymentRemark(GiftOrder order) {
        return "giftOrderId=" + order.getId()
                + ";giftNo=" + order.getGiftNo()
                + ";receiverId=" + order.getReceiverId()
                + ";giftType=" + order.getGiftType()
                + ";marketplaceItemId=" + order.getTargetId()
                + ";sellerId=" + order.getSellerId();
    }

    private String normalizeVipType(String vipType) {
        return ObjectUtils.isEmpty(vipType) ? "" : vipType.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeMessage(String message) {
        if (ObjectUtils.isEmpty(message)) {
            return null;
        }
        String normalized = message.trim();
        if (normalized.length() > 200) {
            throw new BusinessException("赠礼留言不能超过200个字符");
        }
        return normalized;
    }

    private Integer resolveVipLevel(String vipType) {
        if ("year".equals(vipType)) {
            return 3;
        }
        if ("quarter".equals(vipType)) {
            return 2;
        }
        return 1;
    }

    private int safePage(Integer page) {
        return ObjectUtils.isEmpty(page) || page <= 0 ? 1 : page;
    }

    private int safeSize(Integer size) {
        return ObjectUtils.isEmpty(size) || size <= 0 ? 20 : Math.min(size, 100);
    }

    private String generateGiftNo() {
        return "GIFT" + System.currentTimeMillis() + (int) (Math.random() * 10000);
    }

    private String truncate(String value, int maxLength) {
        if (ObjectUtils.isEmpty(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
