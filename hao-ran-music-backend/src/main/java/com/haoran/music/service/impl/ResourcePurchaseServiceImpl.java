




package com.haoran.music.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.config.PaymentConfig;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.Result;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.ResourcePurchase;
import com.haoran.music.entity.UserPurchased;
import com.haoran.music.mapper.ResourcePurchaseMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.mapper.UserPurchasedMapper;
import com.haoran.music.service.ResourcePurchaseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;











@Slf4j
@Service
public class ResourcePurchaseServiceImpl extends ServiceImpl<ResourcePurchaseMapper, ResourcePurchase>
        implements ResourcePurchaseService {

    @Autowired
    private ResourcePurchaseMapper resourcePurchaseMapper;

    @Autowired
    private PaymentConfig paymentConfig;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserPurchasedMapper userPurchasedMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Long> createPurchase(Long userId, Long resourceId, String resourceType,
                                       Long ownerId, String ownerType, BigDecimal amount) {

        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(resourceId) || ObjectUtils.isEmpty(amount)) {
            return Result.error("参数不能为空");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return Result.error("金额必须大于0");
        }

        try {
            UserAccountStatusUtil.requireCanInteract(userId, userMapper::selectById, "创建资源购买记录");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        }
        if ("creator".equalsIgnoreCase(ownerType)
                && !UserAccountStatusUtil.canExposePublicContent(ownerId, userMapper::selectById)) {
            return Result.error("资源创作者账号状态不可用");
        }


        if (hasPurchased(userId, resourceId, resourceType)) {
            return Result.error("您已经购买过该资源");
        }


        BigDecimal platformFee = amount.multiply(paymentConfig.getPlatformFeeRate()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal creatorEarnings = amount.subtract(platformFee);


        ResourcePurchase purchase = new ResourcePurchase();
        purchase.setOrderNo(generateOrderNo());
        purchase.setUserId(userId);
        purchase.setResourceId(resourceId);
        purchase.setResourceType(resourceType);
        purchase.setOwnerId(ownerId);
        purchase.setOwnerType(ObjectUtils.isEmpty(ownerType) ? "platform" : ownerType);
        purchase.setAmount(amount);
        purchase.setPlatformFee(platformFee);
        purchase.setCreatorEarnings(creatorEarnings);
        purchase.setStatus("pending");
        purchase.setPurchaseTime(LocalDateTime.now());

        resourcePurchaseMapper.insert(purchase);

        log.info("创建购买记录成功: orderNo={}, userId={}, resourceId={}, amount={}",
                purchase.getOrderNo(), userId, resourceId, amount);

        return Result.success(purchase.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> confirmPurchase(Long purchaseId, Long paymentOrderId) {
        ResourcePurchase purchase = resourcePurchaseMapper.selectById(purchaseId);

        if (ObjectUtils.isEmpty(purchase)) {
            return Result.error("购买记录不存在");
        }

        if ("active".equals(purchase.getStatus())) {
            return Result.error("该订单已确认");
        }

        try {
            UserAccountStatusUtil.requireCanInteract(
                    purchase.getUserId(), userMapper::selectById, "确认资源购买");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        }
        if ("creator".equalsIgnoreCase(purchase.getOwnerType())
                && !UserAccountStatusUtil.canExposePublicContent(
                purchase.getOwnerId(), userMapper::selectById)) {
            return Result.error("资源创作者账号状态不可用，暂不发放购买权益");
        }

        LambdaUpdateWrapper<ResourcePurchase> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(ResourcePurchase::getId, purchaseId)
                .set(ResourcePurchase::getStatus, "active")
                .set(ResourcePurchase::getPaymentOrderId, paymentOrderId);
        resourcePurchaseMapper.update(null, updateWrapper);




        log.info("确认购买成功: purchaseId={}, paymentOrderId={}, creatorEarnings={}",
            purchaseId, paymentOrderId, purchase.getCreatorEarnings());

        return Result.success();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> refundPurchase(Long purchaseId, String reason) {
        ResourcePurchase purchase = resourcePurchaseMapper.selectById(purchaseId);

        if (ObjectUtils.isEmpty(purchase)) {
            return Result.error("购买记录不存在");
        }

        if (!"active".equals(purchase.getStatus())) {
            return Result.error("只能退款已支付的订单");
        }

        LambdaUpdateWrapper<ResourcePurchase> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(ResourcePurchase::getId, purchaseId)
                .set(ResourcePurchase::getStatus, "refunded")
                .set(ResourcePurchase::getRefundTime, LocalDateTime.now());
        resourcePurchaseMapper.update(null, updateWrapper);




        log.info("退款成功: purchaseId={}, reason={}, refundAmount={}",
            purchaseId, reason, purchase.getCreatorEarnings());

        return Result.success();
    }

    @Override
    public Result<ResourcePurchase> getPurchase(Long purchaseId) {
        ResourcePurchase purchase = resourcePurchaseMapper.selectById(purchaseId);
        if (ObjectUtils.isEmpty(purchase)) {
            return Result.error("购买记录不存在");
        }
        return Result.success(purchase);
    }

    @Override
    public Boolean hasPurchased(Long userId, Long resourceId, String resourceType) {
        if (!UserAccountStatusUtil.canInteract(userId, userMapper::selectById)) {
            return false;
        }

        LambdaQueryWrapper<UserPurchased> entitlementWrapper = new LambdaQueryWrapper<>();
        entitlementWrapper.eq(UserPurchased::getUserId, userId)
                .eq(UserPurchased::getResourceId, resourceId)
                .eq(UserPurchased::getResourceType, resourceType);
        UserPurchased entitlement = userPurchasedMapper.selectOne(entitlementWrapper);
        if (entitlement != null
                && (entitlement.getExpireTime() == null
                || LocalDateTime.now().isBefore(entitlement.getExpireTime()))) {
            return true;
        }

        LambdaQueryWrapper<ResourcePurchase> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ResourcePurchase::getUserId, userId)
                .eq(ResourcePurchase::getResourceId, resourceId)
                .eq(ResourcePurchase::getResourceType, resourceType)
                .eq(ResourcePurchase::getStatus, "active");
        Long count = resourcePurchaseMapper.selectCount(queryWrapper);
        return count != null && count > 0;
    }

    @Override
    public Result<IPage<ResourcePurchase>> getUserPurchases(Long userId, PageQuery query) {
        Page<ResourcePurchase> page = new Page<>(query.getPageNum(), query.getPageSize());

        LambdaQueryWrapper<ResourcePurchase> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ResourcePurchase::getUserId, userId)
                .orderByDesc(ResourcePurchase::getPurchaseTime);

        IPage<ResourcePurchase> result = resourcePurchaseMapper.selectPage(page, queryWrapper);

        return Result.success(result);
    }

    @Override
    public Result<IPage<ResourcePurchase>> getCreatorSales(Long ownerId, PageQuery query) {
        Page<ResourcePurchase> page = new Page<>(query.getPageNum(), query.getPageSize());

        LambdaQueryWrapper<ResourcePurchase> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ResourcePurchase::getOwnerId, ownerId)
                .eq(ResourcePurchase::getOwnerType, "creator")
                .eq(ResourcePurchase::getStatus, "active")
                .orderByDesc(ResourcePurchase::getPurchaseTime);

        IPage<ResourcePurchase> result = resourcePurchaseMapper.selectPage(page, queryWrapper);

        return Result.success(result);
    }

    @Override
    public Result<Map<String, Object>> getCreatorEarnings(Long ownerId) {
        Map<String, Object> aggregate = resourcePurchaseMapper.selectCreatorEarnings(ownerId);
        Map<String, Object> result = purchaseStats(aggregate, true, true);

        return Result.success(result);
    }

    @Override
    public Result<Map<String, Object>> getPlatformEarnings(String startDate, String endDate) {
        LocalDateTime start = null;
        LocalDateTime end = null;
        if (ObjectUtils.isNotEmpty(startDate)) {
            start = LocalDate.parse(startDate).atStartOfDay();
        }
        if (ObjectUtils.isNotEmpty(endDate)) {
            end = LocalDate.parse(endDate).plusDays(1).atStartOfDay();
        }

        Map<String, Object> aggregate = resourcePurchaseMapper.selectPlatformEarnings(start, end);
        Map<String, Object> result = purchaseStats(aggregate, false, true);
        result.put("totalEarnings", result.get("totalPlatformFee"));

        return Result.success(result);
    }

    @Override
    public Result<Map<String, Object>> getResourceSales(Long resourceId, String resourceType) {
        Map<String, Object> aggregate = resourcePurchaseMapper.selectResourceSales(resourceId, resourceType);
        Map<String, Object> result = purchaseStats(aggregate, true, false);

        result.put("resourceId", resourceId);
        result.put("resourceType", resourceType);

        return Result.success(result);
    }




    private String generateOrderNo() {
        return "RP" + System.currentTimeMillis() + IdUtil.randomUUID().substring(0, 4).toUpperCase();
    }

    private Map<String, Object> purchaseStats(Map<String, Object> aggregate,
                                              boolean includeCreatorEarnings,
                                              boolean includePlatformFee) {
        Map<String, Object> result = new HashMap<>();
        result.put("totalSales", moneyValue(aggregate, "totalSales"));
        result.put("totalOrders", intValue(aggregate, "totalOrders"));
        if (includeCreatorEarnings) {
            result.put("totalEarnings", moneyValue(aggregate, "totalEarnings"));
        }
        if (includePlatformFee) {
            result.put("totalPlatformFee", moneyValue(aggregate, "totalPlatformFee"));
        }
        return result;
    }

    private BigDecimal moneyValue(Map<String, Object> source, String key) {
        if (source == null) {
            return BigDecimal.ZERO;
        }
        Object value = source.get(key);
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private int intValue(Map<String, Object> source, String key) {
        if (source == null) {
            return 0;
        }
        Object value = source.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
