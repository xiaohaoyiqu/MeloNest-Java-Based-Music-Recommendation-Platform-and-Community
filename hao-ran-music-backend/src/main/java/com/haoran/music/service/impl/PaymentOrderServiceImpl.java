




package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.haoran.music.common.config.PaymentConfig;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.*;
import com.haoran.music.enums.UserRole;
import com.haoran.music.mapper.*;
import com.haoran.music.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;




@Slf4j
@Service
public class PaymentOrderServiceImpl implements PaymentOrderService {

    private static final String PAYMENT_PROOF_REF_PREFIX = "payment-proof:";
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final PaymentOrderMapper paymentOrderMapper;
    private final UserMapper userMapper;
    private final PaymentConfig paymentConfig;
    private final PaymentCodeService paymentCodeService;
    private final VipService vipService;
    private final PaidResourceService paidResourceService;
    private final RewardService rewardService;
    private final SubscribeService subscribeService;
    private final RefundService refundService;
    private final VipGiftService vipGiftService;
    private final RewardRecordMapper rewardRecordMapper;
    private final VipPurchaseRecordMapper vipPurchaseRecordMapper;
    private final PaidEntitlementGrantMapper paidEntitlementGrantMapper;
    private final PaymentProductCatalogService paymentProductCatalogService;
    private final PaymentSecurityService paymentSecurityService;
    private final PaymentProofLifecycleService paymentProofLifecycleService;
    private final PaymentOrderCompletionTransactionExecutor completionTransactionExecutor;
    private final EmojiPackageMapper emojiPackageMapper;
    private final UserEmojiMapper userEmojiMapper;
    private final EmojiPackagePurchaseRecordMapper emojiPackagePurchaseRecordMapper;
    private final DecorationPurchaseRecordMapper decorationPurchaseRecordMapper;
    private final DecorationService decorationService;
    private final CreatorEarningsMapper creatorEarningsMapper;

    public PaymentOrderServiceImpl(PaymentOrderMapper paymentOrderMapper,
                                    UserMapper userMapper,
                                    PaymentConfig paymentConfig,
                                    @Lazy PaymentCodeService paymentCodeService,
                                    @Lazy VipService vipService,
                                     @Lazy PaidResourceService paidResourceService,
                                     @Lazy RewardService rewardService,
                                     @Lazy SubscribeService subscribeService,
                                     @Lazy RefundService refundService,
                                      @Lazy VipGiftService vipGiftService,
                                      RewardRecordMapper rewardRecordMapper,
                                      VipPurchaseRecordMapper vipPurchaseRecordMapper,
                                      PaidEntitlementGrantMapper paidEntitlementGrantMapper,
                                   PaymentProductCatalogService paymentProductCatalogService,
                                    PaymentSecurityService paymentSecurityService,
                                    PaymentProofLifecycleService paymentProofLifecycleService,
                                    PaymentOrderCompletionTransactionExecutor completionTransactionExecutor,
                                    EmojiPackageMapper emojiPackageMapper,
                                    UserEmojiMapper userEmojiMapper,
                                     EmojiPackagePurchaseRecordMapper emojiPackagePurchaseRecordMapper,
                                     DecorationPurchaseRecordMapper decorationPurchaseRecordMapper,
                                     @Lazy DecorationService decorationService,
                                     CreatorEarningsMapper creatorEarningsMapper) {
        this.paymentOrderMapper = paymentOrderMapper;
        this.userMapper = userMapper;
        this.paymentConfig = paymentConfig;
        this.paymentCodeService = paymentCodeService;
        this.vipService = vipService;
        this.paidResourceService = paidResourceService;
        this.rewardService = rewardService;
        this.subscribeService = subscribeService;
        this.refundService = refundService;
        this.vipGiftService = vipGiftService;
        this.rewardRecordMapper = rewardRecordMapper;
        this.vipPurchaseRecordMapper = vipPurchaseRecordMapper;
        this.paidEntitlementGrantMapper = paidEntitlementGrantMapper;
        this.paymentProductCatalogService = paymentProductCatalogService;
        this.paymentSecurityService = paymentSecurityService;
        this.paymentProofLifecycleService = paymentProofLifecycleService;
        this.completionTransactionExecutor = completionTransactionExecutor;
        this.emojiPackageMapper = emojiPackageMapper;
        this.userEmojiMapper = userEmojiMapper;
        this.emojiPackagePurchaseRecordMapper = emojiPackagePurchaseRecordMapper;
        this.decorationPurchaseRecordMapper = decorationPurchaseRecordMapper;
        this.decorationService = decorationService;
        this.creatorEarningsMapper = creatorEarningsMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createOrder(Long userId, String businessType, Long businessId,
                                           BigDecimal amount, Long payeeId, String userRemark) {
        return createOrder(userId, businessType, businessId, amount, payeeId, userRemark, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createOrder(Long userId, String businessType, Long businessId,
                                           BigDecimal amount, Long payeeId, String userRemark,
                                           String idempotencyKey) {

        String normalizedBusinessType = normalizeBusinessType(businessType);
        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        PaymentOrder replayOrder = findIdempotentOrder(userId, normalizedIdempotencyKey);
        if (replayOrder != null) {
            return replayCreateOrder(replayOrder, null, normalizedBusinessType, businessId);
        }


        boolean catalogPurchaseNeedsUserLock = "emoji_package".equals(normalizedBusinessType)
                || "decoration".equals(normalizedBusinessType);
        User user = catalogPurchaseNeedsUserLock
                ? userMapper.selectByIdForUpdate(userId)
                : userMapper.selectById(userId);
        if (ObjectUtils.isEmpty(user)) {
            throw new BusinessException("用户不存在");
        }
        UserAccountStatusUtil.requireCanInteract(user, "创建支付订单");


        if (!isValidBusinessType(normalizedBusinessType)) {
            throw new BusinessException("不支持的业务类型");
        }
        PaymentProductSnapshot productSnapshot = paymentProductCatalogService.resolve(
                userId, normalizedBusinessType, businessId, amount, payeeId, userRemark);
        BigDecimal authoritativeAmount = productSnapshot.getAmount();
        Long authoritativePayeeId = productSnapshot.getPayeeId();
        String requestHash = calculateRequestHash(userId, productSnapshot);


        if (authoritativePayeeId != null) {
            User payee = userMapper.selectById(authoritativePayeeId);
            if (ObjectUtils.isEmpty(payee)) {
                throw new BusinessException("收款人不存在");
            }
            if (!UserAccountStatusUtil.canInteract(payee)) {
                throw new BusinessException(UserAccountStatusUtil.targetUnavailableMessage(payee) + "，无法收款");
            }
        }


        String orderNo = generateOrderNo();


        LocalDateTime expireTime = LocalDateTime.now()
                .plusHours(paymentConfig.getOrderExpireHours());


        PaymentOrder order = new PaymentOrder();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setPayeeId(authoritativePayeeId);
        order.setAmount(authoritativeAmount);
        order.setCurrency(productSnapshot.getCurrency());
        order.setBusinessType(normalizedBusinessType);
        order.setBusinessId(productSnapshot.getBusinessId());
        order.setOrderTitle(productSnapshot.getProductName());
        order.setProductInfo(productSnapshot.toJson());
        order.setUserRemark(userRemark);
        order.setStatus("pending");
        order.setExpireTime(expireTime);
        order.setPaymentType("wechat");                                      
        order.setIdempotencyKey(normalizedIdempotencyKey);
        order.setRequestHash(requestHash);

        try {
            if (paymentOrderMapper.insert(order) != 1) {
                throw new BusinessException("支付订单创建失败，请稍后重试");
            }
        } catch (DuplicateKeyException e) {
            PaymentOrder concurrentOrder = findIdempotentOrder(userId, normalizedIdempotencyKey);
            if (concurrentOrder == null) {
                throw e;
            }
            return replayCreateOrder(
                    concurrentOrder, requestHash, normalizedBusinessType, productSnapshot.getBusinessId());
        }

        log.info("event=payment_order_created orderId={} userId={} businessType={}",
                order.getId(), userId, normalizedBusinessType);

        Map<String, Object> result = new HashMap<>();
        result.put("id", order.getId());
        result.put("orderId", order.getId());
        result.put("orderNo", orderNo);
        result.put("amount", authoritativeAmount);
        result.put("currency", productSnapshot.getCurrency());
        result.put("businessType", normalizedBusinessType);
        result.put("expireTime", expireTime);
        result.put("status", "pending");
        result.put("message", "订单创建成功");
        result.put("idempotentReplay", false);

        return result;
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (ObjectUtils.isEmpty(idempotencyKey)) {
            return null;
        }
        String normalized = idempotencyKey.trim();
        if (normalized.length() < 8 || normalized.length() > 128
                || !normalized.matches("[A-Za-z0-9._:-]+")) {
            throw new BusinessException("幂等键格式不正确");
        }
        return normalized;
    }

    private PaymentOrder findIdempotentOrder(Long userId, String idempotencyKey) {
        if (ObjectUtils.isEmpty(idempotencyKey)) {
            return null;
        }
        return paymentOrderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getUserId, userId)
                .eq(PaymentOrder::getIdempotencyKey, idempotencyKey)
                .last("LIMIT 1"));
    }

    private Map<String, Object> replayCreateOrder(PaymentOrder order,
                                                  String requestHash,
                                                  String businessType,
                                                  Long businessId) {
        if (!Objects.equals(businessType, normalizeBusinessType(order.getBusinessType()))
                || !Objects.equals(businessId, order.getBusinessId())
                || (requestHash != null && order.getRequestHash() != null
                && !Objects.equals(requestHash, order.getRequestHash()))) {
            throw new BusinessException("幂等键已用于不同的支付请求");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("id", order.getId());
        result.put("orderId", order.getId());
        result.put("orderNo", order.getOrderNo());
        result.put("amount", order.getAmount());
        result.put("currency", order.getCurrency());
        result.put("businessType", order.getBusinessType());
        result.put("expireTime", order.getExpireTime());
        result.put("status", order.getStatus());
        result.put("message", "返回已创建订单");
        result.put("idempotentReplay", true);
        return result;
    }

    private String calculateRequestHash(Long userId, PaymentProductSnapshot snapshot) {
        try {
            String canonical = userId + "\n" + snapshot.toJson();
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value & 0xff));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("无法计算支付请求摘要", e);
        }
    }

    @Override
    public Map<String, Object> getOrderQrCode(Long orderId, Long userId) {
        PaymentOrder order = findActiveOrder(orderId);
        ensureOrderOwner(order, userId);
        ensureOrderPartiesCanProceed(order, "获取付款码");


        if (!"pending".equals(order.getStatus())) {
            throw new BusinessException("订单状态不正确");
        }


        if (order.getExpireTime() != null && LocalDateTime.now().isAfter(order.getExpireTime())) {
            throw new BusinessException("订单已过期");
        }


        String paymentType = ObjectUtils.isNotEmpty(order.getPaymentType())
                && !"qrcode".equals(order.getPaymentType()) ? order.getPaymentType() : "wechat";
        Long payeeId = order.getPayeeId();

        Map<String, Object> qrCodeInfo;
        if (payeeId == null) {

            qrCodeInfo = paymentCodeService.getPlatformPaymentCode(paymentType);
        } else {

            qrCodeInfo = paymentCodeService.getCreatorPaymentCode(payeeId, paymentType);
        }

        Object baseQrCodeUrl = qrCodeInfo.get("qrCodeUrl");
        if (ObjectUtils.isEmpty(baseQrCodeUrl)) {
            throw new BusinessException("付款码图片未配置");
        }
        String expectedVerifyCode = paymentSecurityService.generateVerificationCode();
        String displayQrCodeUrl = String.valueOf(baseQrCodeUrl);


        order.setPaymentType(paymentType);
        order.setQrCodeUrl(String.valueOf(baseQrCodeUrl));
        order.setVerifyCode(paymentSecurityService.hashOrderCode(order.getId(), expectedVerifyCode));
        if (paymentOrderMapper.updateById(order) != 1) {
            throw new BusinessException("付款码生成失败，请稍后重试");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("orderId", order.getId());
        result.put("orderNo", order.getOrderNo());
        result.put("amount", order.getAmount());
        result.put("currency", ObjectUtils.isNotEmpty(order.getCurrency()) ? order.getCurrency() : resolveCurrency());
        result.put("expireTime", order.getExpireTime());
        result.put("qrCodeUrl", displayQrCodeUrl);
        result.put("qrcodeUrl", displayQrCodeUrl);
        result.put("qrCodeWithVerify", displayQrCodeUrl);
        result.put("verifyCode", expectedVerifyCode);
        result.put("paymentType", paymentType);
        result.put("platformAccount", payeeId == null ? "平台微信收款码" : "创作者微信收款码");
        result.put("configId", qrCodeInfo.get("configId"));

        return result;
    }









    @Override
    @Transactional(rollbackFor = Exception.class, noRollbackFor = BusinessException.class)
    public Map<String, Object> submitPayment(Long orderId, Long userId, String proofUrl, String verifyCode) {
        PaymentOrder order = findActiveOrder(orderId);
        ensureOrderOwner(order, userId);
        ensureOrderPartiesCanProceed(order, "提交付款凭证");

        String currentStatus = order.getStatus();
        if (!"pending".equals(currentStatus) && !"rejected".equals(currentStatus)) {
            throw new BusinessException("订单已处理");
        }


        if (order.getExpireTime() != null && LocalDateTime.now().isAfter(order.getExpireTime())) {
            LambdaUpdateWrapper<PaymentOrder> expiration = new LambdaUpdateWrapper<>();
            expiration.eq(PaymentOrder::getId, orderId)
                    .eq(PaymentOrder::getUserId, userId)
                    .eq(PaymentOrder::getDeleted, 0)
                    .in(PaymentOrder::getStatus, Arrays.asList("pending", "rejected"))
                    .set(PaymentOrder::getStatus, "expired")
                    .set(PaymentOrder::getUpdateTime, LocalDateTime.now());
            if (paymentOrderMapper.update(null, expiration) != 1) {
                throw new BusinessException("订单状态已变化，请刷新后重试");
            }
            throw new BusinessException("订单已过期");
        }


        if (ObjectUtils.isEmpty(verifyCode)) {
            throw new BusinessException("请输入验证码");
        }
        verifyCode = verifyCode.trim();
        if (!verifyCode.matches("\\d{6}")) {
            throw new BusinessException("验证码格式不正确，应为6位数字");
        }
        if (ObjectUtils.isEmpty(order.getVerifyCode())) {
            throw new BusinessException("请先获取付款码后再提交凭证");
        }
        if (!paymentSecurityService.matchesOrderCode(orderId, verifyCode, order.getVerifyCode())) {
            throw new BusinessException("验证码不匹配，请核对付款码下方的6位数字");
        }


        if (!isPrivateProofReference(order.getPaymentProof())) {
            throw new BusinessException("请上传付款凭证");
        }


        LambdaUpdateWrapper<PaymentOrder> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(PaymentOrder::getId, orderId)
                .in(PaymentOrder::getStatus, Arrays.asList("pending", "rejected"))
                .set(PaymentOrder::getStatus, "submitted")
                .set(PaymentOrder::getReviewerId, null)
                .set(PaymentOrder::getReviewTime, null)
                .set(PaymentOrder::getReviewReason, null)
                .set(PaymentOrder::getUpdateTime, LocalDateTime.now());
        if (paymentOrderMapper.update(null, updateWrapper) != 1) {
            throw new BusinessException("订单状态已变化，请刷新后重试");
        }

        log.info("event=payment_proof_submitted orderId={} userId={}", orderId, userId);

        Map<String, Object> result = new HashMap<>();
        result.put("orderId", orderId);
        result.put("status", "submitted");
        result.put("message", "付款凭证已提交，等待审核");

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> bindPaymentProof(Long orderId, Long userId, String proofReference) {
        PaymentOrder order = paymentOrderMapper.selectActiveByIdForUpdate(orderId);
        if (ObjectUtils.isEmpty(order)) {
            throw new BusinessException("订单不存在");
        }
        ensureOrderOwner(order, userId);
        ensureOrderPartiesCanProceed(order, "上传付款凭证");
        if (!"pending".equals(order.getStatus()) && !"rejected".equals(order.getStatus())) {
            throw new BusinessException("订单状态不允许上传付款凭证");
        }
        if (order.getExpireTime() != null && LocalDateTime.now().isAfter(order.getExpireTime())) {
            throw new BusinessException("订单已过期");
        }
        if (!isPrivateProofReference(proofReference)) {
            throw new BusinessException("付款凭证引用无效");
        }

        String storedPreviousReference = order.getPaymentProof();
        String previousReference = normalizeStoredProofReference(storedPreviousReference);
        LambdaUpdateWrapper<PaymentOrder> update = new LambdaUpdateWrapper<>();
        update.eq(PaymentOrder::getId, orderId)
                .eq(PaymentOrder::getUserId, userId)
                .in(PaymentOrder::getStatus, Arrays.asList("pending", "rejected"))
                .eq(PaymentOrder::getDeleted, 0);
        if (storedPreviousReference == null) {
            update.isNull(PaymentOrder::getPaymentProof);
        } else {
            update.eq(PaymentOrder::getPaymentProof, storedPreviousReference);
        }
        update.set(PaymentOrder::getPaymentProof, proofReference)
                .set(PaymentOrder::getUpdateTime, LocalDateTime.now());
        if (paymentOrderMapper.update(null, update) != 1) {
            throw new BusinessException("订单状态已变化，请刷新后重试");
        }
        paymentProofLifecycleService.recordReplacement(
                orderId, userId, previousReference, proofReference);

        Map<String, Object> result = new HashMap<>();
        result.put("orderId", orderId);
        result.put("proofImageUrl", "/api/payment/proof/" + orderId);
        result.put("message", "付款凭证上传成功");
        return result;
    }

    @Override
    public String getPaymentProofReference(Long orderId, Long viewerId) {
        PaymentOrder order = findActiveOrder(orderId);
        if (viewerId == null) {
            throw new BusinessException("请先登录");
        }
        if (!viewerId.equals(order.getUserId())) {
            ensureAdminOperator(viewerId);
        }
        String reference = normalizeStoredProofReference(order.getPaymentProof());
        if (!isPrivateProofReference(reference)) {
            throw new BusinessException("付款凭证不存在");
        }
        return reference;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Map<String, Object> reviewOrder(Long orderId, Long reviewerId,
                                           Boolean approved, String reviewReason) {
        PaymentOrder order = findActiveOrder(orderId);
        ensureAdminOperator(reviewerId);
        if (approved == null) {
            throw new BusinessException("审核结果不能为空");
        }

        if (!"submitted".equals(order.getStatus())) {
            throw new BusinessException("订单状态不正确");
        }
        String normalizedReviewReason = reviewReason == null ? null : reviewReason.trim();
        if (!approved && ObjectUtils.isEmpty(normalizedReviewReason)) {
            throw new BusinessException("拒绝订单时必须填写审核理由");
        }
        if (normalizedReviewReason != null && normalizedReviewReason.length() > 500) {
            throw new BusinessException("审核理由不能超过500个字符");
        }

        Map<String, Object> result = new HashMap<>();

        LocalDateTime reviewTime = LocalDateTime.now();
        LambdaUpdateWrapper<PaymentOrder> transition = new LambdaUpdateWrapper<>();
        transition.eq(PaymentOrder::getId, orderId)
                .eq(PaymentOrder::getStatus, "submitted")
                .set(PaymentOrder::getStatus, approved ? "paid" : "rejected")
                .set(PaymentOrder::getCompletionStatus, approved ? "pending" : null)
                .set(PaymentOrder::getCompletionTime, null)
                .set(PaymentOrder::getCompletionError, null)
                .set(PaymentOrder::getReviewerId, reviewerId)
                .set(PaymentOrder::getReviewTime, reviewTime)
                .set(PaymentOrder::getReviewReason, normalizedReviewReason)
                .set(PaymentOrder::getUpdateTime, reviewTime);
        if (paymentOrderMapper.update(null, transition) != 1) {
            throw new BusinessException("订单已被其他审核请求处理");
        }

        if (approved) {
            order.setStatus("paid");
            order.setReviewerId(reviewerId);
            order.setReviewTime(reviewTime);
            order.setReviewReason(normalizedReviewReason);

            boolean completionSucceeded = completeOrderAndRecordState(order);

            result.put("status", "paid");
            result.put("completionPending", !completionSucceeded);
            result.put("message", completionSucceeded
                    ? "订单审核通过"
                    : "付款已审核通过，权益暂未发放，待账号恢复或故障处理后补发");

            log.info("event=payment_order_reviewed orderId={} approved=true completionSucceeded={}",
                    orderId, completionSucceeded);

        } else {
            result.put("status", "rejected");
            result.put("message", "订单审核拒绝");
            result.put("reason", normalizedReviewReason);

            log.info("event=payment_order_reviewed orderId={} approved=false", orderId);
        }

        return result;
    }

    @Override
    public Map<String, Object> getMyOrders(Long userId, String status, String businessType,
                                          Integer page, Integer size) {
        int safePage = normalizePage(page);
        int safeSize = normalizePageSize(size);
        Page<PaymentOrder> pageParam = new Page<>(safePage, safeSize);

        LambdaQueryWrapper<PaymentOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaymentOrder::getUserId, userId)
                .eq(PaymentOrder::getDeleted, 0);

        if (ObjectUtils.isNotEmpty(status)) {
            wrapper.eq(PaymentOrder::getStatus, status);
        }
        if (ObjectUtils.isNotEmpty(businessType)) {
            wrapper.eq(PaymentOrder::getBusinessType, normalizeBusinessType(businessType));
        }

        wrapper.orderByDesc(PaymentOrder::getCreateTime);

        Page<PaymentOrder> resultPage = paymentOrderMapper.selectPage(pageParam, wrapper);

        List<Map<String, Object>> records = resultPage.getRecords().stream()
                .map(this::buildUserOrderResult)
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("list", records);
        result.put("total", resultPage.getTotal());
        result.put("page", safePage);
        result.put("size", safeSize);

        return result;
    }

    @Override
    public Map<String, Object> getPendingOrders(Long payeeId, Integer page, Integer size) {
        int safePage = normalizePage(page);
        Page<PaymentOrder> pageParam = new Page<>(safePage, normalizePageSize(size));

        LambdaQueryWrapper<PaymentOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaymentOrder::getStatus, "submitted")
                .eq(PaymentOrder::getDeleted, 0);

        if (payeeId != null) {
            wrapper.eq(PaymentOrder::getPayeeId, payeeId);
        }

        wrapper.orderByAsc(PaymentOrder::getCreateTime);

        Page<PaymentOrder> resultPage = paymentOrderMapper.selectPage(pageParam, wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("records", resultPage.getRecords().stream()
                .map(this::buildAdminOrderResult)
                .collect(Collectors.toList()));
        result.put("total", resultPage.getTotal());
        result.put("current", safePage);
        result.put("pages", resultPage.getPages());

        return result;
    }

    @Override
    public Map<String, Object> getPendingCompletionOrders(Integer page, Integer size) {
        int safePage = normalizePage(page);
        Page<PaymentOrder> pageParam = new Page<>(safePage, normalizePageSize(size));
        LambdaQueryWrapper<PaymentOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(PaymentOrder::getStatus, Arrays.asList("paid", "success", "completed"))
                .in(PaymentOrder::getCompletionStatus, Arrays.asList("pending", "processing", "blocked", "failed"))
                .eq(PaymentOrder::getDeleted, 0)
                .orderByAsc(PaymentOrder::getUpdateTime);

        Page<PaymentOrder> resultPage = paymentOrderMapper.selectPage(pageParam, wrapper);
        Map<String, Object> result = new HashMap<>();
        result.put("records", resultPage.getRecords().stream()
                .map(this::buildAdminOrderResult)
                .collect(Collectors.toList()));
        result.put("total", resultPage.getTotal());
        result.put("current", safePage);
        result.put("pages", resultPage.getPages());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean cancelOrder(Long orderId, Long userId) {
        PaymentOrder order = findActiveOrder(orderId);
        ensureOrderOwner(order, userId);


        if (!"pending".equals(order.getStatus()) && !"submitted".equals(order.getStatus())) {
            throw new BusinessException("订单状态不允许取消");
        }

        LambdaUpdateWrapper<PaymentOrder> transition = new LambdaUpdateWrapper<>();
        transition.eq(PaymentOrder::getId, orderId)
                .eq(PaymentOrder::getUserId, userId)
                .eq(PaymentOrder::getDeleted, 0)
                .in(PaymentOrder::getStatus, Arrays.asList("pending", "submitted"))
                .set(PaymentOrder::getStatus, "cancelled")
                .set(PaymentOrder::getUpdateTime, LocalDateTime.now());
        if (paymentOrderMapper.update(null, transition) != 1) {
            throw new BusinessException("订单状态已变化，请刷新后重试");
        }

        log.info("event=payment_order_cancelled orderId={} userId={}", orderId, userId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer handleExpiredOrders() {
        LambdaQueryWrapper<PaymentOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaymentOrder::getStatus, "pending")
                .eq(PaymentOrder::getDeleted, 0)
                .lt(PaymentOrder::getExpireTime, LocalDateTime.now());

        Integer count = Math.toIntExact(paymentOrderMapper.selectCount(wrapper));

        if (count > 0) {
            PaymentOrder updateEntity = new PaymentOrder();
            updateEntity.setStatus("expired");

            paymentOrderMapper.update(updateEntity, wrapper);
            log.info("event=payment_order_timeout_processed processedCount={}", count);
        }

        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer cleanOldCancelledOrders(LocalDateTime cutoffDate) {
        if (cutoffDate == null) {
            cutoffDate = LocalDateTime.now().minusDays(30);
        }

        LambdaQueryWrapper<PaymentOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaymentOrder::getStatus, "cancelled")
                .eq(PaymentOrder::getDeleted, 0)
                .lt(PaymentOrder::getCreateTime, cutoffDate);

        Integer count = Math.toIntExact(paymentOrderMapper.selectCount(wrapper));

        if (count > 0) {
            PaymentOrder updateEntity = new PaymentOrder();
            updateEntity.setDeleted(1);
            updateEntity.setUpdateTime(LocalDateTime.now());
            paymentOrderMapper.update(updateEntity, wrapper);
            log.info("event=payment_order_cancelled_marked processedCount={} cutoffDate={}",
                    count, cutoffDate);
        }

        return count;
    }

    @Override
    public Map<String, Object> getOrderDetail(Long orderId, Long userId) {
        PaymentOrder order = findActiveOrder(orderId);
        ensureOrderOwner(order, userId);

        long remainingTime = 0L;
        if (order.getExpireTime() != null && "pending".equals(order.getStatus())) {
            remainingTime = Math.max(0L, java.time.Duration.between(LocalDateTime.now(), order.getExpireTime()).getSeconds());
        }

        boolean canCancel = "pending".equals(order.getStatus()) || "submitted".equals(order.getStatus());
        boolean canSubmitProof = "pending".equals(order.getStatus()) || "rejected".equals(order.getStatus());

        Map<String, Object> result = buildUserOrderResult(order);
        result.put("canCancel", canCancel);
        result.put("canSubmitProof", canSubmitProof);
        result.put("remainingTime", remainingTime);

        return result;
    }

    @Override
    public Map<String, Object> getOrderByNo(String orderNo, Long userId) {
        LambdaQueryWrapper<PaymentOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaymentOrder::getOrderNo, orderNo)
                .eq(PaymentOrder::getDeleted, 0)
                .eq(PaymentOrder::getUserId, userId);
        PaymentOrder order = paymentOrderMapper.selectOne(wrapper);

        if (ObjectUtils.isEmpty(order)) {
            throw new BusinessException("订单不存在");
        }

        return getOrderDetail(order.getId(), userId);
    }


    @Override
    public Map<String, Object> getOrderStatus(Long orderId, Long userId) {
        Map<String, Object> detail = getOrderDetail(orderId, userId);

        Map<String, Object> result = new HashMap<>();
        result.put("orderId", detail.get("orderId"));
        result.put("status", detail.get("status"));
        result.put("canCancel", detail.get("canCancel"));
        result.put("canSubmitProof", detail.get("canSubmitProof"));
        result.put("remainingTime", detail.get("remainingTime"));
        result.put("currency", detail.get("currency"));
        return result;
    }

    @Override
    public Map<String, Object> getOrderStatistics() {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> statistics = paymentOrderMapper.selectOrderStatistics();
        result.put("pendingCount", numberValue(statistics, "pending_count"));
        result.put("submittedCount", numberValue(statistics, "submitted_count"));
        result.put("paidCount", numberValue(statistics, "paid_count"));
        result.put("rejectedCount", numberValue(statistics, "rejected_count"));
        result.put("cancelledCount", numberValue(statistics, "cancelled_count"));
        result.put("expiredCount", numberValue(statistics, "expired_count"));
        result.put("refundingCount", numberValue(statistics, "refunding_count"));
        result.put("refundedCount", numberValue(statistics, "refunded_count"));
        result.put("totalAmount", amountValue(statistics, "total_amount"));
        result.put("todayAmount", amountValue(statistics, "today_amount"));
        result.put("currency", resolveCurrency());
        return result;
    }
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Boolean completeOrder(Long orderId) {
        PaymentOrder order;
        try {
            order = findActiveOrder(orderId);
        } catch (BusinessException ex) {
            return false;
        }
        if (!isPaidStatus(order.getStatus())) {
            return false;
        }
        if (isOrderCompletionRecorded(order)) {
            markCompletionState(order.getId(), true, null);
            return true;
        }

        return completeOrderAndRecordState(order);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Boolean retryOrderCompletion(Long orderId, Long operatorId) {
        ensureAdminOperator(operatorId);
        PaymentOrder order = findActiveOrder(orderId);
        if (!isPaidStatus(order.getStatus())) {
            throw new BusinessException("只有已审核通过的订单可以补发权益");
        }
        if (isOrderCompletionRecorded(order)) {
            markCompletionState(order.getId(), true, null);
            return true;
        }

        PaymentConfig.CompletionRecovery configured = paymentConfig.getCompletionRecovery();
        PaymentConfig.CompletionRecovery recovery = configured == null
                ? new PaymentConfig.CompletionRecovery() : configured;
        int leaseSeconds = boundedInt(recovery.getLeaseSeconds(), 300, 30, 3600);
        int baseBackoffSeconds = boundedInt(recovery.getBaseBackoffSeconds(), 30, 1, 3600);
        int maxBackoffSeconds = boundedInt(
                recovery.getMaxBackoffSeconds(), 3600, baseBackoffSeconds, 86400);
        String workerId = "manual-" + Long.toUnsignedString(operatorId, 36) + "-"
                + UUID.randomUUID().toString().replace("-", "");
        if (paymentOrderMapper.claimCompletionForManualRetry(orderId, workerId, leaseSeconds) != 1) {
            throw new BusinessException("订单权益正在处理，请稍后重试");
        }

        PaymentOrder claimedOrder = paymentOrderMapper.selectClaimedCompletion(orderId, workerId);
        if (claimedOrder == null) {
            throw new BusinessException("订单权益处理租约已失效，请稍后重试");
        }
        CompletionAttemptResult attempt = attemptOrderCompletion(claimedOrder);
        if (attempt.completed) {
            if (paymentOrderMapper.completeClaimedCompletion(orderId, workerId) == 1) {
                return true;
            }
            if (isOrderCompletionRecorded(claimedOrder)) {
                return true;
            }
            throw new BusinessException("订单权益处理状态已变化，请刷新后重试");
        }

        boolean alreadyDeadLettered = "failed".equals(order.getCompletionStatus());
        String targetStatus = alreadyDeadLettered ? "failed" : "blocked";
        int attemptCount = claimedOrder.getCompletionAttemptCount() == null
                ? 1 : Math.max(1, claimedOrder.getCompletionAttemptCount());
        long nextRetrySeconds = alreadyDeadLettered ? 0L : calculateBackoffSeconds(
                orderId, attemptCount, baseBackoffSeconds, maxBackoffSeconds);
        if (paymentOrderMapper.releaseClaimedCompletion(
                orderId, workerId, targetStatus, attempt.errorType, nextRetrySeconds) != 1) {
            throw new BusinessException("订单权益处理状态已变化，请刷新后重试");
        }
        log.warn("event=payment_entitlement_reissue_deferred orderId={} operatorId={} userId={} errorType={}",
                orderId, operatorId, order.getUserId(), attempt.errorType);
        return false;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentCompletionRecoverySummary recoverPendingCompletions(String workerId) {
        if (ObjectUtils.isEmpty(workerId)
                || workerId.length() > 64
                || !workerId.matches("[A-Za-z0-9._:-]+")) {
            throw new BusinessException("支付履约任务实例标识无效");
        }

        PaymentConfig.CompletionRecovery configured = paymentConfig.getCompletionRecovery();
        PaymentConfig.CompletionRecovery recovery = configured == null
                ? new PaymentConfig.CompletionRecovery() : configured;
        int batchSize = boundedInt(recovery.getBatchSize(), 20, 1, 100);
        int maxAttempts = boundedInt(recovery.getMaxAttempts(), 8, 1, 20);
        int leaseSeconds = boundedInt(recovery.getLeaseSeconds(), 300, 30, 3600);
        int baseBackoffSeconds = boundedInt(recovery.getBaseBackoffSeconds(), 30, 1, 3600);
        int maxBackoffSeconds = boundedInt(
                recovery.getMaxBackoffSeconds(), 3600, baseBackoffSeconds, 86400);

        List<Long> candidates = paymentOrderMapper.selectRecoverableCompletionIds(batchSize, maxAttempts);
        PaymentCompletionRecoverySummary summary = new PaymentCompletionRecoverySummary();
        summary.setCandidates(candidates == null ? 0 : candidates.size());
        if (candidates == null || candidates.isEmpty()) {
            return summary;
        }

        for (Long orderId : candidates) {
            if (orderId == null) {
                continue;
            }
            boolean exhaustedLeaseRecovery = false;
            int claimed = paymentOrderMapper.claimCompletion(
                    orderId, workerId, leaseSeconds, maxAttempts);
            if (claimed != 1) {
                claimed = paymentOrderMapper.claimExhaustedCompletionForReconciliation(
                        orderId, workerId, leaseSeconds, maxAttempts);
                exhaustedLeaseRecovery = claimed == 1;
            }
            if (claimed != 1) {
                continue;
            }
            summary.recordClaimed();

            PaymentOrder order = paymentOrderMapper.selectClaimedCompletion(orderId, workerId);
            if (order == null) {
                summary.recordLostClaim();
                continue;
            }

            CompletionAttemptResult attempt = exhaustedLeaseRecovery
                    ? reconcileExhaustedCompletion(order)
                    : attemptOrderCompletion(order);
            if (attempt.completed) {
                if (paymentOrderMapper.completeClaimedCompletion(orderId, workerId) == 1) {
                    summary.recordCompleted();
                } else {
                    summary.recordLostClaim();
                }
                continue;
            }

            int attemptCount = order.getCompletionAttemptCount() == null
                    ? 1 : Math.max(1, order.getCompletionAttemptCount());
            boolean terminal = attemptCount >= maxAttempts;
            String targetStatus = terminal ? "failed" : "blocked";
            long nextRetrySeconds = terminal ? 0L : calculateBackoffSeconds(
                    orderId, attemptCount, baseBackoffSeconds, maxBackoffSeconds);
            int released = paymentOrderMapper.releaseClaimedCompletion(
                    orderId, workerId, targetStatus, attempt.errorType, nextRetrySeconds);
            if (released != 1) {
                summary.recordLostClaim();
            } else if (terminal) {
                summary.recordDeadLettered();
            } else {
                summary.recordDeferred();
            }
        }

        return summary;
    }

    private CompletionAttemptResult reconcileExhaustedCompletion(PaymentOrder order) {
        try {
            return isOrderCompletionRecorded(order)
                    ? CompletionAttemptResult.completed()
                    : CompletionAttemptResult.failed("LeaseExpiredAtAttemptLimit");
        } catch (Exception exception) {
            String errorType = exception.getClass().getSimpleName();
            return CompletionAttemptResult.failed(
                    ObjectUtils.isEmpty(errorType) ? "CompletionFactCheckException" : errorType);
        }
    }

    private boolean completeOrderAndRecordState(PaymentOrder order) {
        return completeOrderAndRecordState(order, "blocked");
    }

    private boolean completeOrderAndRecordState(PaymentOrder order, String failureStatus) {
        CompletionAttemptResult attempt = attemptOrderCompletion(order);
        markCompletionState(order.getId(), attempt.completed,
                attempt.completed ? null : "账号状态或业务记录暂不可用", failureStatus);
        return attempt.completed;
    }

    private CompletionAttemptResult attemptOrderCompletion(PaymentOrder order) {
        try {
            if (isOrderCompletionRecorded(order)) {
                return CompletionAttemptResult.completed();
            }
            boolean completed = completionTransactionExecutor.executeBoolean(
                    () -> completeOrderAtomically(order));
            return completed
                    ? CompletionAttemptResult.completed()
                    : CompletionAttemptResult.failed("CompletionRejected");
        } catch (Exception exception) {
            String errorType = exception.getClass().getSimpleName();
            if (ObjectUtils.isEmpty(errorType) || errorType.length() > 100) {
                errorType = "CompletionException";
            }
            log.warn("event=payment_order_completion_deferred orderId={} businessType={} errorType={}",
                    order == null ? null : order.getId(),
                    order == null ? null : normalizeBusinessType(order.getBusinessType()), errorType);
            return CompletionAttemptResult.failed(errorType);
        }
    }

    private int boundedInt(Integer value, int defaultValue, int minimum, int maximum) {
        int resolved = value == null ? defaultValue : value;
        return Math.max(minimum, Math.min(maximum, resolved));
    }

    private long calculateBackoffSeconds(Long orderId,
                                         int attemptCount,
                                         int baseBackoffSeconds,
                                         int maxBackoffSeconds) {
        int exponent = Math.min(30, Math.max(0, attemptCount - 1));
        long exponential;
        if (baseBackoffSeconds > (Long.MAX_VALUE >> exponent)) {
            exponential = maxBackoffSeconds;
        } else {
            exponential = Math.min(maxBackoffSeconds, ((long) baseBackoffSeconds) << exponent);
        }
        if (exponential >= maxBackoffSeconds) {
            return maxBackoffSeconds;
        }
        long jitterWindow = Math.max(1L, exponential / 5L);
        long stableSeed = (orderId == null ? 0L : orderId) * 31L + attemptCount * 17L;
        long jitter = Math.floorMod(stableSeed, jitterWindow + 1L);
        return Math.min(maxBackoffSeconds, exponential + jitter);
    }

    private static final class CompletionAttemptResult {
        private final boolean completed;
        private final String errorType;

        private CompletionAttemptResult(boolean completed, String errorType) {
            this.completed = completed;
            this.errorType = errorType;
        }

        private static CompletionAttemptResult completed() {
            return new CompletionAttemptResult(true, null);
        }

        private static CompletionAttemptResult failed(String errorType) {
            return new CompletionAttemptResult(false, errorType);
        }
    }




    private Boolean completeOrderAtomically(PaymentOrder order) {
        if (order == null || order.getId() == null) {
            return false;
        }
        PaymentOrder lockedOrder = paymentOrderMapper.selectActiveByIdForUpdate(order.getId());
        if (lockedOrder == null || !isPaidStatus(lockedOrder.getStatus())) {
            return false;
        }
        order = lockedOrder;
        String businessType = normalizeBusinessType(order.getBusinessType());
        Long userId = order.getUserId();
        Long businessId = order.getBusinessId();
        Long payeeId = order.getPayeeId();
        BigDecimal amount = order.getAmount();

        if (isOrderCompletionRecorded(order)) {
            return true;
        }



        ensurePaidOrderBuyerCanReceive(order);
        PaymentProductSnapshot productSnapshot = readProductSnapshot(order);

        switch (businessType) {
                case "vip":

                    if (!completeVipOrder(userId, businessId, productSnapshot, order.getId())) {
                        return false;
                    }
                    break;
                case "purchase":

                    if (!completePurchaseOrder(order, productSnapshot)) {
                        return false;
                    }
                    break;
                case "reward":

                    if (productSnapshot != null) {
                        payeeId = productSnapshot.getPayeeId();
                        businessId = productSnapshot.getBusinessId();
                        amount = productSnapshot.getAmount();
                    }
                    completeRewardOrder(userId, payeeId, businessId, amount, order.getId());
                    break;
                case "subscribe":

                    if (!Boolean.TRUE.equals(subscribeService.completeSubscribeOrder(businessId, order.getId()))) {
                        return false;
                    }
                    break;
                case "gift_vip":
                    if (!vipGiftService.completeVipGiftByPaymentOrder(order.getId())) {
                        return false;
                    }
                    break;
                case "gift_marketplace":
                    if (!vipGiftService.completeMarketplaceGiftByPaymentOrder(order.getId())) {
                        return false;
                    }
                    break;
                case "emoji_package":
                    if (!completeEmojiPackageOrder(order, productSnapshot)) {
                        return false;
                    }
                    break;
                case "decoration":
                    if (!completeDecorationOrder(order, productSnapshot)) {
                        return false;
                    }
                    break;
            default:
                log.warn("event=payment_business_type_unknown businessType={}", businessType);
                return false;
        }

        log.info("event=payment_order_completion_succeeded orderId={} businessType={}",
                order.getId(), businessType);
        return true;
    }

    private boolean completeVipOrder(Long userId,
                                     Long businessId,
                                     PaymentProductSnapshot productSnapshot,
                                     Long orderId) {
        String vipType;
        Integer days;
        if (productSnapshot != null) {
            vipType = productSnapshot.entitlementString("vipType");
            days = productSnapshot.entitlementInteger("days");
            if (ObjectUtils.isEmpty(vipType) || days == null || days <= 0) {
                throw new BusinessException("VIP订单权益快照无效");
            }
        } else {

            vipType = legacyVipType(businessId);
            days = getVipDays(vipType);
        }


        if (!Boolean.TRUE.equals(vipService.grantVip(userId, days, "支付订单充值完成", null))) {
            return false;
        }


        createVipPurchaseRecord(userId, vipType, days, orderId);

        log.info("event=vip_payment_completed userId={} orderId={}", userId, orderId);
        return true;
    }

    private boolean completePurchaseOrder(PaymentOrder order, PaymentProductSnapshot productSnapshot) {
        Long userId = order.getUserId();
        Long resourceId = productSnapshot == null
                ? order.getBusinessId() : productSnapshot.entitlementLong("resourceId");
        String resourceType = productSnapshot == null
                ? resolvePurchaseResourceType(order) : productSnapshot.entitlementString("resourceType");
        Long paidResourceId = productSnapshot == null
                ? null : productSnapshot.entitlementLong("paidResourceId");
        Integer subscribePeriod = productSnapshot == null
                ? null : productSnapshot.entitlementInteger("subscribePeriod");
        if (ObjectUtils.isEmpty(resourceId) || ObjectUtils.isEmpty(resourceType)
                || productSnapshot != null && ObjectUtils.isEmpty(paidResourceId)) {
            throw new BusinessException("付费资源订单权益快照无效");
        }

        Boolean granted = productSnapshot == null
                ? paidResourceService.grantPurchasedResource(userId, resourceType, resourceId, order.getId())
                : paidResourceService.grantPurchasedResource(userId, resourceType, resourceId, order.getId(),
                        paidResourceId, subscribePeriod, productSnapshot.getAmount());
        if (!Boolean.TRUE.equals(granted)) {
            return false;
        }

        log.info("event=paid_resource_purchase_completed userId={} resourceType={} resourceId={} orderId={}",
                userId, resourceType, resourceId, order.getId());
        return true;
    }

    private boolean completeEmojiPackageOrder(PaymentOrder order, PaymentProductSnapshot productSnapshot) {
        if (productSnapshot == null
                || !"cash".equals(productSnapshot.entitlementString("purchaseMode"))) {
            throw new BusinessException("表情包订单权益快照无效");
        }
        Long packageId = productSnapshot.entitlementLong("emojiPackageId");
        if (packageId == null || !packageId.equals(order.getBusinessId())
                || productSnapshot.getAmount() == null
                || order.getAmount() == null
                || productSnapshot.getAmount().compareTo(order.getAmount()) != 0) {
            throw new BusinessException("表情包订单权益快照无效");
        }
        EmojiPackage emojiPackage = emojiPackageMapper.selectById(packageId);
        if (emojiPackage == null) {
            return false;
        }

        EmojiPackagePurchaseRecord record = new EmojiPackagePurchaseRecord();
        record.setUserId(order.getUserId());
        record.setEmojiPackageId(packageId);
        record.setPaymentOrderId(order.getId());
        record.setAmount(order.getAmount());
        record.setPlatformFeeRate(productSnapshot.entitlementDecimal("platformFeeRate"));
        record.setPlatformFee(productSnapshot.entitlementDecimal("platformFee"));
        record.setCreatorEarnings(productSnapshot.entitlementDecimal("creatorEarnings"));
        record.setCurrency(order.getCurrency());
        record.setPurchaseChannel("cash");
        record.setStatus("active");
        if (emojiPackagePurchaseRecordMapper.insert(record) != 1) {
            return false;
        }
        BigDecimal creatorEarningsAmount = record.getCreatorEarnings();
        if (order.getPayeeId() != null && creatorEarningsAmount != null
                && creatorEarningsAmount.compareTo(BigDecimal.ZERO) > 0) {
            CreatorEarnings earnings = new CreatorEarnings();
            earnings.setUserId(order.getPayeeId());
            earnings.setWorkId(order.getId());
            earnings.setWorkType("emoji_package_order");
            earnings.setEarningsType("purchase");
            earnings.setEarningsAmount(creatorEarningsAmount.movePointRight(2).longValueExact());
            earnings.setCreateTime(LocalDateTime.now());
            earnings.setDeleted(0);
            if (creatorEarningsMapper.insert(earnings) != 1
                    || userMapper.incrementRewardEarnings(order.getPayeeId(), creatorEarningsAmount) != 1) {
                throw new BusinessException("创作者表情包收益写入失败");
            }
        }
        if (userEmojiMapper.grantPurchasedPackage(
                order.getUserId(), packageId, LocalDateTime.now()) != 1) {
            throw new BusinessException("表情包权益写入失败");
        }
        if (emojiPackageMapper.incrementDownloadCount(packageId) != 1) {
            throw new BusinessException("表情包下载数写入失败");
        }
        return true;
    }

    private boolean completeDecorationOrder(PaymentOrder order, PaymentProductSnapshot productSnapshot) {
        if (productSnapshot == null
                || !order.getBusinessId().equals(productSnapshot.getBusinessId())
                || productSnapshot.getAmount() == null
                || order.getAmount() == null
                || productSnapshot.getAmount().compareTo(order.getAmount()) != 0) {
            throw new BusinessException("装饰订单权益快照无效");
        }
        String decorationId = productSnapshot.entitlementString("decorationId");
        if (ObjectUtils.isEmpty(decorationId)) {
            throw new BusinessException("装饰订单权益快照无效");
        }

        DecorationPurchaseRecord record = new DecorationPurchaseRecord();
        record.setUserId(order.getUserId());
        record.setDecorationConfigId(order.getBusinessId());
        record.setDecorationId(decorationId);
        record.setPaymentOrderId(order.getId());
        record.setAmount(order.getAmount());
        record.setPlatformFeeRate(productSnapshot.entitlementDecimal("platformFeeRate"));
        record.setPlatformFee(productSnapshot.entitlementDecimal("platformFee"));
        record.setCreatorEarnings(productSnapshot.entitlementDecimal("creatorEarnings"));
        record.setContentVersion(productSnapshot.entitlementInteger("contentVersion"));
        record.setCurrency(order.getCurrency());
        record.setStatus("active");
        if (decorationPurchaseRecordMapper.insert(record) != 1) {
            return false;
        }
        BigDecimal creatorEarningsAmount = record.getCreatorEarnings();
        if (order.getPayeeId() != null && creatorEarningsAmount != null
                && creatorEarningsAmount.compareTo(BigDecimal.ZERO) > 0) {
            CreatorEarnings earnings = new CreatorEarnings();
            earnings.setUserId(order.getPayeeId());
            earnings.setWorkId(order.getId());
            earnings.setWorkType("decoration_order");
            earnings.setEarningsType("purchase");
            earnings.setEarningsAmount(creatorEarningsAmount.movePointRight(2).longValueExact());
            earnings.setCreateTime(LocalDateTime.now());
            earnings.setDeleted(0);
            if (creatorEarningsMapper.insert(earnings) != 1
                    || userMapper.incrementRewardEarnings(order.getPayeeId(), creatorEarningsAmount) != 1) {
                throw new BusinessException("创作者装饰收益写入失败");
            }
        }
        return decorationService.grantDecoration(order.getUserId(), decorationId, "payment");
    }

    private void completeRewardOrder(Long userId, Long creatorId, Long resourceId, BigDecimal amount, Long orderId) {
        RewardRecord rewardRecord = rewardRecordMapper.selectOne(
            new LambdaQueryWrapper<RewardRecord>().eq(RewardRecord::getPaymentOrderId, orderId)
        );
        if (rewardRecord == null) {
            throw new BusinessException("打赏记录不存在");
        }


        Boolean completed = rewardService.completeReward(rewardRecord.getId());
        if (!completed) {
            throw new BusinessException("打赏记录状态异常，无法完成");
        }

        log.info("event=reward_payment_completed userId={} creatorId={} orderId={}",
                userId, creatorId, orderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean refundOrder(Long orderId, String reason, Long operatorId) {
        PaymentOrder order = paymentOrderMapper.selectById(orderId);
        if (ObjectUtils.isEmpty(order)) {
            throw new BusinessException("订单不存在");
        }

        if (!"paid".equals(order.getStatus())) {
            throw new BusinessException("只能退款已支付的订单");
        }


        refundService.applyRefund(order.getUserId(), orderId, order.getBusinessType(), reason, "订单退款");

        log.info("event=payment_order_refunded orderId={} operatorId={}", orderId, operatorId);
        return true;
    }

    private boolean isOrderCompletionRecorded(PaymentOrder order) {
        if (order == null || order.getId() == null) {
            return false;
        }

        switch (normalizeBusinessType(order.getBusinessType())) {
            case "vip":
                return vipPurchaseRecordMapper.selectCount(
                        new LambdaQueryWrapper<VipPurchaseRecord>()
                                .eq(VipPurchaseRecord::getOrderId, order.getId())
                ) > 0;
            case "purchase":
                return paidEntitlementGrantMapper.selectByPaymentOrderId(order.getId()) != null;
            case "reward":
                RewardRecord reward = rewardRecordMapper.selectOne(
                        new LambdaQueryWrapper<RewardRecord>()
                                .eq(RewardRecord::getPaymentOrderId, order.getId())
                                .last("LIMIT 1")
                );
                return reward != null && "paid".equals(reward.getStatus());
            case "subscribe":
                return paidEntitlementGrantMapper.selectByPaymentOrderId(order.getId()) != null;
            case "gift_vip":
                return vipGiftService.hasAppliedVipGiftByPaymentOrder(order.getId());
            case "gift_marketplace":
                return vipGiftService.hasAppliedMarketplaceGiftByPaymentOrder(order.getId());
            case "emoji_package":
                return emojiPackagePurchaseRecordMapper.selectCount(
                        new LambdaQueryWrapper<EmojiPackagePurchaseRecord>()
                                .eq(EmojiPackagePurchaseRecord::getPaymentOrderId, order.getId())
                ) > 0;
            case "decoration":
                return decorationPurchaseRecordMapper.selectCount(
                        new LambdaQueryWrapper<DecorationPurchaseRecord>()
                                .eq(DecorationPurchaseRecord::getPaymentOrderId, order.getId())
                ) > 0;
            default:
                return false;
        }
    }

    private void markCompletionState(Long orderId, boolean completed, String error) {
        markCompletionState(orderId, completed, error, "blocked");
    }

    private void markCompletionState(Long orderId, boolean completed, String error, String failureStatus) {
        if (orderId == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<PaymentOrder> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(PaymentOrder::getId, orderId)
                .eq(PaymentOrder::getDeleted, 0)
                .set(PaymentOrder::getCompletionStatus, completed ? "completed" : failureStatus)
                .set(PaymentOrder::getCompletionTime, completed ? now : null)
                .set(PaymentOrder::getCompletionError, completed ? null : error)
                .set(PaymentOrder::getUpdateTime, now);
        if (completed) {
            wrapper.set(PaymentOrder::getCompletionNextRetryTime, null)
                    .set(PaymentOrder::getCompletionLeaseOwner, null)
                    .set(PaymentOrder::getCompletionLeaseUntil, null)
                    .set(PaymentOrder::getCompletionDeadLetterTime, null);
        }
        if (paymentOrderMapper.update(null, wrapper) != 1) {
            throw new BusinessException("支付订单履约状态写入失败");
        }
    }

    private PaymentOrder findActiveOrder(Long orderId) {
        if (orderId == null) {
            throw new BusinessException("订单ID不能为空");
        }
        LambdaQueryWrapper<PaymentOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaymentOrder::getId, orderId)
                .eq(PaymentOrder::getDeleted, 0);
        PaymentOrder order = paymentOrderMapper.selectOne(wrapper);
        if (ObjectUtils.isEmpty(order)) {
            throw new BusinessException("订单不存在");
        }
        return order;
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int normalizePageSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private void ensureOrderOwner(PaymentOrder order, Long userId) {
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        if (order == null || order.getUserId() == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作此订单");
        }
    }

    private void ensureAdminOperator(Long operatorId) {
        if (operatorId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "当前操作人不存在");
        }
        User operator = userMapper.selectById(operatorId);
        if (operator == null || !UserRole.isAdmin(operator.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有管理员可以执行支付审核操作");
        }
    }

    private boolean isPaidStatus(String status) {
        return "paid".equalsIgnoreCase(status)
                || "success".equalsIgnoreCase(status)
                || "completed".equalsIgnoreCase(status);
    }

    private boolean isPrivateProofReference(String proofReference) {
        if (ObjectUtils.isEmpty(proofReference) || !proofReference.startsWith(PAYMENT_PROOF_REF_PREFIX)) {
            return false;
        }
        String relativePath = proofReference.substring(PAYMENT_PROOF_REF_PREFIX.length());
        return relativePath.matches("\\d{8}/\\d+/[A-Za-z0-9._-]+");
    }

    private String normalizeStoredProofReference(String storedValue) {
        if (isPrivateProofReference(storedValue)) {
            return storedValue;
        }
        if (ObjectUtils.isEmpty(storedValue)) {
            return null;
        }
        String normalized = storedValue.replace("\\", "/");
        int proofIndex = normalized.indexOf("/payment-assets/proof/");
        if (proofIndex >= 0) {
            return PAYMENT_PROOF_REF_PREFIX
                    + normalized.substring(proofIndex + "/payment-assets/proof/".length());
        }
        return null;
    }


    private Long numberValue(Map<String, Object> values, String key) {
        Object value = values == null ? null : values.get(key);
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private Map<String, Object> buildUserOrderResult(PaymentOrder order) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", order.getId());
        result.put("orderId", order.getId());
        result.put("orderNo", order.getOrderNo());
        result.put("userId", order.getUserId());
        result.put("amount", order.getAmount());
        result.put("currency", ObjectUtils.isNotEmpty(order.getCurrency()) ? order.getCurrency() : resolveCurrency());
        result.put("businessType", order.getBusinessType());
        result.put("businessId", order.getBusinessId());
        result.put("status", order.getStatus());
        result.put("completionStatus", order.getCompletionStatus());
        result.put("completionTime", order.getCompletionTime());
        result.put("completionPending", "pending".equals(order.getCompletionStatus())
                || "processing".equals(order.getCompletionStatus())
                || "blocked".equals(order.getCompletionStatus())
                || "failed".equals(order.getCompletionStatus()));
        result.put("paymentType", order.getPaymentType());
        result.put("paymentMethod", order.getPaymentType());
        result.put("userRemark", order.getUserRemark());
        result.put("createTime", order.getCreateTime());
        result.put("expireTime", order.getExpireTime());
        result.put("reviewTime", order.getReviewTime());
        return result;
    }

    private Map<String, Object> buildAdminOrderResult(PaymentOrder order) {
        Map<String, Object> result = buildUserOrderResult(order);
        result.put("payeeId", order.getPayeeId());
        result.put("orderTitle", order.getOrderTitle());
        result.put("reviewerId", order.getReviewerId());
        result.put("reviewReason", order.getReviewReason());
        result.put("completionError", order.getCompletionError());
        result.put("completionAttemptCount", order.getCompletionAttemptCount());
        result.put("completionLastAttemptTime", order.getCompletionLastAttemptTime());
        result.put("completionNextRetryTime", order.getCompletionNextRetryTime());
        result.put("completionLeaseUntil", order.getCompletionLeaseUntil());
        result.put("completionDeadLetterTime", order.getCompletionDeadLetterTime());
        if (isPrivateProofReference(normalizeStoredProofReference(order.getPaymentProof()))) {
            result.put("proofImageUrl", "/api/payment/proof/" + order.getId());
        }
        return result;
    }

    private BigDecimal amountValue(Map<String, Object> values, String key) {
        Object value = values == null ? null : values.get(key);
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }
        return BigDecimal.ZERO;
    }

    private String resolveCurrency() {
        String configuredCurrency = paymentConfig.getCurrency();
        return ObjectUtils.isNotEmpty(configuredCurrency) ? configuredCurrency.trim().toUpperCase(Locale.ROOT) : "CNY";
    }
    private Boolean isValidBusinessType(String businessType) {
        String normalized = normalizeBusinessType(businessType);
        return "vip".equals(normalized)
                || "purchase".equals(normalized)
                || "reward".equals(normalized)
                || "subscribe".equals(normalized)
                || "gift_vip".equals(normalized)
                || "gift_marketplace".equals(normalized)
                || "emoji_package".equals(normalized)
                || "decoration".equals(normalized);
    }

    private String normalizeBusinessType(String businessType) {
        return businessType == null ? "" : businessType.trim().toLowerCase(Locale.ROOT);
    }

    private void ensureOrderPartiesCanProceed(PaymentOrder order, String action) {
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        UserAccountStatusUtil.requireCanInteract(order.getUserId(), userMapper::selectById, action);

        Long payeeId = order.getPayeeId();
        if (payeeId != null) {
            User payee = userMapper.selectById(payeeId);
            if (!UserAccountStatusUtil.canInteract(payee)) {
                throw new BusinessException(UserAccountStatusUtil.targetUnavailableMessage(payee) + "，无法" + action);
            }
        }
    }

    private void ensurePaidOrderBuyerCanReceive(PaymentOrder order) {
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        UserAccountStatusUtil.requireCanInteract(order.getUserId(), userMapper::selectById, "接收已购权益");
    }

    private PaymentProductSnapshot readProductSnapshot(PaymentOrder order) {
        if (ObjectUtils.isEmpty(order.getProductInfo())) {
            return null;
        }

        final PaymentProductSnapshot snapshot;
        try {
            snapshot = PaymentProductSnapshot.fromJson(order.getProductInfo());
        } catch (Exception e) {
            throw new BusinessException("支付订单商品快照损坏");
        }

        if (snapshot == null || snapshot.getVersion() == null) {
            return null;
        }
        if (!Integer.valueOf(PaymentProductSnapshot.CURRENT_VERSION).equals(snapshot.getVersion())
                || !normalizeBusinessType(order.getBusinessType()).equals(snapshot.getBusinessType())
                || !Objects.equals(order.getBusinessId(), snapshot.getBusinessId())
                || order.getAmount() == null
                || snapshot.getAmount() == null
                || order.getAmount().compareTo(snapshot.getAmount()) != 0
                || !Objects.equals(order.getCurrency(), snapshot.getCurrency())
                || !Objects.equals(order.getPayeeId(), snapshot.getPayeeId())) {
            throw new BusinessException("支付订单与商品快照不一致");
        }
        return snapshot;
    }

    private String resolvePurchaseResourceType(PaymentOrder order) {
        String fromRemark = getRemarkValue(order.getUserRemark(), "resourceType");
        if (ObjectUtils.isNotEmpty(fromRemark)) {
            return fromRemark;
        }

        BigDecimal amount = order.getAmount();
        if (amount != null && amount.compareTo(new BigDecimal("10")) > 0) {
            return "album";
        }
        if (amount != null && amount.compareTo(new BigDecimal("5")) > 0) {
            return "mv";
        }
        return "song";
    }

    private String getRemarkValue(String remark, String key) {
        if (ObjectUtils.isEmpty(remark) || ObjectUtils.isEmpty(key)) {
            return null;
        }
        String prefix = key + "=";
        String[] parts = remark.split(";");
        for (String part : parts) {
            String trimmed = part == null ? "" : part.trim();
            if (trimmed.startsWith(prefix)) {
                return trimmed.substring(prefix.length());
            }
        }
        return null;
    }

    private String generateOrderNo() {
        return paymentConfig.getOrderNoPrefix()
                + System.currentTimeMillis()
                + (int)(Math.random() * 10000);
    }




    private void createVipPurchaseRecord(Long userId, String vipType, Integer days, Long orderId) {
        VipPurchaseRecord record = new VipPurchaseRecord();
        record.setUserId(userId);
        record.setOrderId(orderId);
        record.setVipDays(days);
        record.setStartTime(LocalDateTime.now());
        record.setEndTime(LocalDateTime.now().plusDays(days));

        if (vipPurchaseRecordMapper.insert(record) != 1) {
            throw new BusinessException("VIP购买记录保存失败");
        }
    }




    private Integer getVipDays(String vipType) {
        switch (vipType) {
            case "month": return 30;
            case "quarter": return 90;
            case "half_year": return 180;
            case "year": return 365;
            default: return 30;
        }
    }

    private String legacyVipType(Long businessId) {
        if (Long.valueOf(3L).equals(businessId)) {
            return "quarter";
        }
        if (Long.valueOf(6L).equals(businessId)) {
            return "half_year";
        }
        if (Long.valueOf(12L).equals(businessId)) {
            return "year";
        }
        return "month";
    }
}
