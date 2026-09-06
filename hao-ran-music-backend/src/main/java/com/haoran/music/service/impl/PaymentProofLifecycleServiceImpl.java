package com.haoran.music.service.impl;

import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.entity.PaymentProofAsset;
import com.haoran.music.mapper.PaymentProofAssetMapper;
import com.haoran.music.service.PaymentProofLifecycleService;
import com.haoran.music.service.PaymentProofStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;






@Slf4j
@Service
public class PaymentProofLifecycleServiceImpl implements PaymentProofLifecycleService {

    private static final int MAX_ATTEMPTS = 8;
    private static final int MAX_BATCH_SIZE = 100;
    private static final int LEASE_SECONDS = 60;
    private static final String REFERENCE_PREFIX = "payment-proof:";

    private final PaymentProofAssetMapper assetMapper;
    private final PaymentProofStorageService storageService;

    public PaymentProofLifecycleServiceImpl(PaymentProofAssetMapper assetMapper,
                                            PaymentProofStorageService storageService) {
        this.assetMapper = assetMapper;
        this.storageService = storageService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordReplacement(Long orderId, Long ownerId,
                                  String previousReference, String newReference) {
        requireIdentity(orderId, ownerId, newReference);
        PaymentProofAsset active = assetMapper.selectActiveForUpdate(orderId);
        if (active != null) {
            if (!Objects.equals(active.getOwnerId(), ownerId)
                    || !Objects.equals(active.getProofReference(), previousReference)) {
                throw new BusinessException("付款凭证版本与订单不一致");
            }
            if (Objects.equals(previousReference, newReference)) {
                return;
            }
            requireSingleWrite(assetMapper.retireActive(orderId, previousReference),
                    "付款凭证旧版本撤销失败");
        } else if (isPrivateReference(previousReference)
                && !Objects.equals(previousReference, newReference)) {
            insertAsset(orderId, ownerId, previousReference,
                    PaymentProofAsset.STATUS_CLEANUP_PENDING);
        }

        if (active == null || !Objects.equals(previousReference, newReference)) {
            insertAsset(orderId, ownerId, newReference, PaymentProofAsset.STATUS_ACTIVE);
        }
    }

    @Override
    public boolean dispatchCleanup(Long assetId) {
        if (assetId == null || assetId <= 0) {
            return false;
        }
        String workerId = UUID.randomUUID().toString();
        if (assetMapper.claim(assetId, workerId, LEASE_SECONDS) != 1) {
            return false;
        }
        PaymentProofAsset asset = assetMapper.selectClaimed(assetId, workerId);
        if (asset == null) {
            log.warn("event=payment_proof_cleanup_lease_lost assetId={} stage=load", assetId);
            return false;
        }
        try {
            if (!isPrivateReference(asset.getProofReference())) {
                requireSingleWrite(assetMapper.markTerminalFailed(
                        assetId, workerId, "INVALID_REFERENCE"), "付款凭证清理终态写入失败");
                log.warn("event=payment_proof_cleanup_terminal_failed assetId={} reason=invalid_reference",
                        assetId);
                return false;
            }
            storageService.delete(asset.getProofReference());
            if (assetMapper.markCleaned(assetId, workerId) != 1) {
                log.warn("event=payment_proof_cleanup_lease_lost assetId={} stage=mark_cleaned", assetId);
                return false;
            }
            log.info("event=payment_proof_cleanup_succeeded assetId={} orderId={} attempt={}",
                    assetId, asset.getOrderId(), safeAttempt(asset));
            return true;
        } catch (Exception exception) {
            int attempt = safeAttempt(asset);
            Integer retryDelay = attempt < safeMaxAttempts(asset)
                    ? Math.toIntExact(retryDelaySeconds(attempt)) : null;
            int marked = assetMapper.markFailed(assetId, workerId,
                    "STORAGE_DEPENDENCY_ERROR", retryDelay);
            if (marked != 1) {
                log.warn("event=payment_proof_cleanup_lease_lost assetId={} stage=mark_failed", assetId);
                return false;
            }
            log.warn("event=payment_proof_cleanup_failed assetId={} orderId={} attempt={} errorType={}",
                    assetId, asset.getOrderId(), attempt, exception.getClass().getSimpleName());
            return false;
        }
    }

    @Override
    public int retryDueCleanup(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_BATCH_SIZE));
        List<Long> assetIds = assetMapper.selectDueIds(safeLimit);
        if (ObjectUtils.isEmpty(assetIds)) {
            return 0;
        }
        int completed = 0;
        for (Long assetId : assetIds) {
            if (dispatchCleanup(assetId)) {
                completed++;
            }
        }
        return completed;
    }

    @Override
    public boolean retryFailedCleanup(Long assetId) {
        if (assetId == null || assetId <= 0 || assetMapper.requeueFailed(assetId) != 1) {
            return false;
        }
        log.info("event=payment_proof_cleanup_requeued assetId={}", assetId);
        return dispatchCleanup(assetId);
    }

    @Override
    public Map<String, Object> getStatusSummary() {
        Map<String, Object> result = new HashMap<>();
        result.put("statuses", assetMapper.selectStatusSummary());
        result.put("maxAttempts", MAX_ATTEMPTS);
        result.put("maxBatchSize", MAX_BATCH_SIZE);
        result.put("leaseSeconds", LEASE_SECONDS);
        return result;
    }

    @Override
    public List<Map<String, Object>> getRecentFailures(int limit) {
        return assetMapper.selectRecentFailures(Math.max(1, Math.min(limit, MAX_BATCH_SIZE)));
    }

    private void insertAsset(Long orderId, Long ownerId, String reference, String status) {
        PaymentProofAsset asset = new PaymentProofAsset();
        asset.setEventId(UUID.randomUUID().toString());
        asset.setOrderId(orderId);
        asset.setOwnerId(ownerId);
        asset.setProofReference(reference);
        asset.setReferenceDigest(digest(reference));
        asset.setStatus(status);
        asset.setAttemptCount(0);
        asset.setMaxAttempts(MAX_ATTEMPTS);
        if (PaymentProofAsset.STATUS_CLEANUP_PENDING.equals(status)) {
            asset.setNextRetryTime(LocalDateTime.now());
        }
        asset.setCreateTime(LocalDateTime.now());
        asset.setUpdateTime(LocalDateTime.now());
        requireSingleWrite(assetMapper.insert(asset), "付款凭证版本登记失败");
    }

    private void requireIdentity(Long orderId, Long ownerId, String reference) {
        if (orderId == null || orderId <= 0 || ownerId == null || ownerId <= 0
                || !isPrivateReference(reference)) {
            throw new BusinessException("付款凭证版本身份无效");
        }
    }

    private boolean isPrivateReference(String reference) {
        if (reference == null || !reference.startsWith(REFERENCE_PREFIX)) {
            return false;
        }
        return reference.substring(REFERENCE_PREFIX.length())
                .matches("\\d{8}/\\d+/[A-Za-z0-9._-]+");
    }

    private String digest(String reference) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(reference.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                result.append(String.format("%02x", value & 0xff));
            }
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("付款凭证摘要生成失败", exception);
        }
    }

    private int safeAttempt(PaymentProofAsset asset) {
        return asset.getAttemptCount() == null ? 1 : Math.max(1, asset.getAttemptCount());
    }

    private int safeMaxAttempts(PaymentProofAsset asset) {
        return asset.getMaxAttempts() == null ? MAX_ATTEMPTS : asset.getMaxAttempts();
    }

    private long retryDelaySeconds(int attempt) {
        return Math.min(900L, 15L * (1L << Math.min(6, Math.max(0, attempt - 1))));
    }

    private void requireSingleWrite(int affectedRows, String message) {
        if (affectedRows != 1) {
            throw new BusinessException(message);
        }
    }
}
