




package com.haoran.music.service.impl;

import com.haoran.music.common.config.PaymentConfig;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.haoran.music.common.config.PriceRangeConfig;
import com.haoran.music.common.constant.PublicStatsSql;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.*;
import com.haoran.music.mapper.*;
import com.haoran.music.service.PaidResourceService;
import com.haoran.music.service.PaymentOrderService;
import com.haoran.music.service.NotificationService;
import com.haoran.music.service.PaidEntitlementLedgerService;
import com.haoran.music.service.search.SearchIndexService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;




@Slf4j
@Service
public class PaidResourceServiceImpl implements PaidResourceService {

    private static final Set<Integer> ALLOWED_SUBSCRIBE_PERIODS =
            new java.util.HashSet<>(java.util.Arrays.asList(0, 30, 90, 180, 365));

    @javax.annotation.Resource
    private com.haoran.music.service.CreatorEligibilityService creatorEligibilityService;

    private final PaidResourceMapper paidResourceMapper;
    private final UserPurchasedMapper userPurchasedMapper;
    private final UserMapper userMapper;
    private final PlaylistMapper playlistMapper;
    private final SongMapper songMapper;
    private final MVMapper mvMapper;
    private final AlbumMapper albumMapper;
    private final PaymentOrderService paymentOrderService;
    private final PaymentConfig paymentConfig;
    private final PriceRangeConfig priceRangeConfig;
    private final SearchIndexService searchIndexService;
    private final NotificationService notificationService;
    private final PaidEntitlementLedgerService entitlementLedgerService;

    public PaidResourceServiceImpl(PaidResourceMapper paidResourceMapper,
                                  UserPurchasedMapper userPurchasedMapper,
                                  UserMapper userMapper,
                                  PlaylistMapper playlistMapper,
                                  SongMapper songMapper,
                                  MVMapper mvMapper,
                                  AlbumMapper albumMapper,
                                   @Lazy PaymentOrderService paymentOrderService,
                                   PaymentConfig paymentConfig,
                                   PriceRangeConfig priceRangeConfig,
                                   SearchIndexService searchIndexService,
                                   NotificationService notificationService,
                                   PaidEntitlementLedgerService entitlementLedgerService) {
        this.paidResourceMapper = paidResourceMapper;
        this.userPurchasedMapper = userPurchasedMapper;
        this.userMapper = userMapper;
        this.playlistMapper = playlistMapper;
        this.songMapper = songMapper;
        this.mvMapper = mvMapper;
        this.albumMapper = albumMapper;
        this.paymentOrderService = paymentOrderService;
        this.paymentConfig = paymentConfig;
        this.priceRangeConfig = priceRangeConfig;
        this.searchIndexService = searchIndexService;
        this.notificationService = notificationService;
        this.entitlementLedgerService = entitlementLedgerService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> setPaidResource(Long ownerId, String resourceType, Long resourceId,
                                              BigDecimal price, Integer subscribePeriod,
                                              String changeType, String changeReason) {
        creatorEligibilityService.requireEligible(ownerId, "设置付费资源");


        if (!isValidResourceType(resourceType)) {
            throw new BusinessException("不支持的资源类型");
        }
        if ("playlist".equals(resourceType)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "歌单付费申请请使用歌单专用接口");
        }
        requireResourceOwner(ownerId, resourceType, resourceId);

        if (price == null || price.scale() > 2 || !priceRangeConfig.isPriceInRange(resourceType, price)) {
            throw new BusinessException("价格必须在" +
                    priceRangeConfig.getPriceRangeDescription(resourceType) + "范围内，且最多保留两位小数");
        }
        if (subscribePeriod != null && !ALLOWED_SUBSCRIBE_PERIODS.contains(subscribePeriod)) {
            throw new BusinessException("订阅周期仅支持一次性、30、90、180或365天");
        }


        LambdaQueryWrapper<PaidResource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaidResource::getResourceType, resourceType)
                .eq(PaidResource::getResourceId, resourceId);

        PaidResource existing = paidResourceMapper.selectOne(wrapper);

        if (existing != null) {
            if (!ownerId.equals(existing.getOwnerId())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权修改他人付费资源配置");
            }
            if ("pending".equals(existing.getStatus())) {
                throw new BusinessException("付费资源正在审核中，暂不能修改");
            }
            if ("approved".equals(existing.getStatus()) && Integer.valueOf(1).equals(existing.getIsEnabled())) {
                if (samePriceAndPeriod(existing, price, subscribePeriod)) {
                    log.info("event=paid_resource_change_unchanged ownerId={} resourceType={} resourceId={}",
                            ownerId, resourceType, resourceId);
                    return buildResourceResult(existing, "unchanged");
                }
                stageCandidateChange(existing, price, subscribePeriod, changeType, changeReason);
                return buildResourceResult(existing, "change_pending");
            }


            int updated = paidResourceMapper.update(null, new LambdaUpdateWrapper<PaidResource>()
                    .eq(PaidResource::getId, existing.getId())
                    .eq(PaidResource::getOwnerId, ownerId)
                    .eq(PaidResource::getStatus, existing.getStatus())
                    .set(PaidResource::getPrice, price)
                    .set(PaidResource::getSubscribePeriod, subscribePeriod)
                    .set(PaidResource::getIsEnabled, 1)
                    .set(PaidResource::getStatus, "pending")
                    .set(PaidResource::getChangeType, changeType)
                    .set(PaidResource::getChangeReason, changeReason)
                    .set(PaidResource::getChangeReviewStatus, null));
            if (updated != 1) {
                throw new BusinessException("付费资源状态已变化，请刷新后重试");
            }
            existing.setPrice(price);
            existing.setSubscribePeriod(subscribePeriod);
            existing.setIsEnabled(1);
            existing.setStatus("pending");
            existing.setChangeType(changeType);
            existing.setChangeReason(changeReason);
            existing.setChangeReviewStatus(null);
            syncFormalPaidProjection(existing, false);
            searchIndexService.sync(existing.getResourceType(), existing.getResourceId());

            log.info("event=paid_resource_resubmitted ownerId={} resourceType={} resourceId={} price={}",
                    ownerId, resourceType, resourceId, price);

            return buildResourceResult(existing, "updated");
        }


        PaidResource resource = new PaidResource();
        resource.setResourceType(resourceType);
        resource.setResourceId(resourceId);
        resource.setOwnerId(ownerId);
        resource.setOwnerType("creator");
        resource.setPrice(price);
        resource.setPriceType("creator");
        resource.setIsEnabled(1);
        resource.setStatus("pending");
        resource.setSalesCount(0);
        resource.setTotalEarnings(BigDecimal.ZERO);
        resource.setSubscribePeriod(subscribePeriod);
        resource.setPlatformFeeRate(paymentConfig.getPlatformFeeRate());
        resource.setChangeType(changeType);
        resource.setChangeReason(changeReason);

        if (paidResourceMapper.insert(resource) != 1) {
            throw new BusinessException("付费资源申请创建失败");
        }

        log.info("event=paid_resource_submitted ownerId={} resourceType={} resourceId={} price={}",
                ownerId, resourceType, resourceId, price);

        return buildResourceResult(resource, "created");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean cancelPaidResource(Long ownerId, String resourceType, Long resourceId) {
        creatorEligibilityService.requireEligible(ownerId, "取消付费资源");
        LambdaQueryWrapper<PaidResource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaidResource::getResourceType, resourceType)
                .eq(PaidResource::getResourceId, resourceId)
                .eq(PaidResource::getOwnerId, ownerId);

        PaidResource resource = paidResourceMapper.selectOne(wrapper);

        if (resource == null) {
            throw new BusinessException("付费资源配置不存在");
        }

        int updated = paidResourceMapper.update(null, new LambdaUpdateWrapper<PaidResource>()
                .eq(PaidResource::getId, resource.getId())
                .eq(PaidResource::getOwnerId, ownerId)
                .eq(PaidResource::getIsEnabled, 1)
                .set(PaidResource::getIsEnabled, 0)
                .set(PaidResource::getStatus, "cancelled")
                .set(PaidResource::getChangeReviewStatus, "cancelled"));
        if (updated != 1) {
            throw new BusinessException("付费资源状态已变化，请刷新后重试");
        }
        resource.setIsEnabled(0);
        resource.setStatus("cancelled");
        resource.setChangeReviewStatus("cancelled");
        syncStoppedSaleProjection(resource);
        searchIndexService.sync(resource.getResourceType(), resource.getResourceId());

        log.info("event=paid_resource_cancelled ownerId={} resourceType={} resourceId={}",
                ownerId, resourceType, resourceId);
        return true;
    }

    @Override
    public Map<String, Object> getMyPaidResources(Long ownerId, Integer page, Integer size) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        Page<PaidResource> pageParam = new Page<>(safePage, safeSize);

        LambdaQueryWrapper<PaidResource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaidResource::getOwnerId, ownerId)
                .orderByDesc(PaidResource::getCreateTime);

        Page<PaidResource> resultPage = paidResourceMapper.selectPage(pageParam, wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("list", resultPage.getRecords());
        result.put("total", resultPage.getTotal());
        result.put("page", safePage);
        result.put("size", safeSize);

        return result;
    }

    @Override
    public Map<String, Object> getPaidResourceDetail(String resourceType, Long resourceId) {
        LambdaQueryWrapper<PaidResource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaidResource::getResourceType, resourceType)
                .eq(PaidResource::getResourceId, resourceId)
                .eq(PaidResource::getIsEnabled, 1)
                .eq(PaidResource::getStatus, "approved");

        PaidResource resource = paidResourceMapper.selectOne(wrapper);

        if (resource == null) {
            throw new BusinessException("资源不存在或未设置为付费");
        }
        if (!canSellOwner(resource.getOwnerId())) {
            throw new BusinessException("资源不存在或未设置为付费");
        }

        return buildPublicResourceResult(resource, "detail");
    }

    @Override
    public Boolean checkPurchased(Long userId, String resourceType, Long resourceId) {
        if (!UserAccountStatusUtil.canInteract(userId, userMapper::selectById)) {
            return false;
        }

        LambdaQueryWrapper<UserPurchased> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserPurchased::getUserId, userId)
                .eq(UserPurchased::getResourceType, resourceType)
                .eq(UserPurchased::getResourceId, resourceId);

        UserPurchased purchased = userPurchasedMapper.selectOne(wrapper);

        if (purchased == null) {
            return false;
        }


        if (purchased.getExpireTime() != null &&
                LocalDateTime.now().isAfter(purchased.getExpireTime())) {
            return false;
        }

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> purchaseResource(Long userId, String resourceType, Long resourceId,
                                                String idempotencyKey) {
        UserAccountStatusUtil.requireCanInteract(userId, userMapper::selectById, "购买付费资源");


        if (checkPurchased(userId, resourceType, resourceId)) {
            throw new BusinessException("您已购买过此资源");
        }


        PaidResource resource = getActivePublicPaidResource(resourceType, resourceId);
        BigDecimal price = resource.getPrice();
        Long ownerId = resource.getOwnerId();
        Long paidResourceId = resource.getId();


        Map<String, Object> orderResult = paymentOrderService.createOrder(
                userId,
                "purchase",
                resourceId,
                price,
                ownerId,
                purchaseOrderRemark(resourceType, paidResourceId),
                idempotencyKey
        );

        Long orderId = (Long) orderResult.get("orderId");

        log.info("event=paid_resource_order_created userId={} resourceType={} resourceId={} paidResourceId={} orderId={}",
                userId, resourceType, resourceId, paidResourceId, orderId);

        Map<String, Object> result = new HashMap<>();
        result.put("orderId", orderId);
        result.put("orderNo", orderResult.get("orderNo"));
        result.put("amount", price);
        result.put("resourceInfo", buildPublicResourceResult(resource, "detail"));
        result.put("message", "订单创建成功，请完成支付");

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean grantPurchasedResource(Long userId, String resourceType, Long resourceId, Long orderId) {
        PaidResource resource = getActivePublicPaidResource(resourceType, resourceId);
        return grantPurchasedResource(userId, resourceType, resourceId, orderId,
                resource.getId(), resource.getSubscribePeriod(), resource.getPrice());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean grantPurchasedResource(Long userId, String resourceType, Long resourceId, Long orderId,
                                          Long paidResourceId, Integer subscribePeriod, BigDecimal saleAmount) {
        UserAccountStatusUtil.requireCanInteract(
                userMapper.selectByIdForUpdate(userId), "激活付费资源权益");

        if (paidResourceId == null || saleAmount == null || saleAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("付费资源订单权益快照无效");
        }
        PaidResource resource = paidResourceMapper.selectById(paidResourceId);
        if (resource == null
                || !resourceType.equals(resource.getResourceType())
                || !resourceId.equals(resource.getResourceId())) {
            throw new BusinessException("付费资源订单对应的历史配置不存在");
        }

        LambdaQueryWrapper<UserPurchased> existingWrapper = new LambdaQueryWrapper<>();
        existingWrapper.eq(UserPurchased::getUserId, userId)
                .eq(UserPurchased::getResourceType, resourceType)
                .eq(UserPurchased::getResourceId, resourceId);
        UserPurchased existing = userPurchasedMapper.selectOne(existingWrapper);
        if (existing != null && !isExpired(existing)) {
            if (!java.util.Objects.equals(existing.getPurchaseOrderId(), orderId)) {
                throw new BusinessException("已有其他订单授予的有效权益，不能重复覆盖");
            }
            entitlementLedgerService.recordGrant(orderId, userId, resourceType, resourceId,
                    paidResourceId, existing.getPurchaseType(), existing.getPurchaseTime(), existing.getExpireTime());
            return true;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireTime = subscribePeriod != null && subscribePeriod > 0
                ? now.plusDays(subscribePeriod)
                : null;
        boolean newGrant = entitlementLedgerService.recordGrant(orderId, userId, resourceType, resourceId,
                paidResourceId, "purchase", now, expireTime);

        if (existing == null) {
            UserPurchased purchased = new UserPurchased();
            purchased.setUserId(userId);
            purchased.setResourceType(resourceType);
            purchased.setResourceId(resourceId);
            purchased.setPurchaseType("purchase");
            purchased.setPurchaseOrderId(orderId);
            purchased.setPurchaseTime(now);
            purchased.setExpireTime(expireTime);
            if (userPurchasedMapper.insert(purchased) != 1) {
                throw new BusinessException("付费资源权益投影创建失败");
            }
        } else {
            existing.setPurchaseType("purchase");
            existing.setPurchaseOrderId(orderId);
            existing.setPurchaseTime(now);
            existing.setExpireTime(expireTime);
            if (userPurchasedMapper.updateById(existing) != 1) {
                throw new BusinessException("付费资源权益投影更新失败");
            }
        }

        if (newGrant && paidResourceMapper.incrementHistoricalSalesAndEarnings(resource.getId(), saleAmount) != 1) {
            throw new BusinessException("付费资源订单对应的历史配置不存在");
        }

        log.info("event=paid_resource_entitlement_granted userId={} resourceType={} resourceId={} orderId={}",
                userId, resourceType, resourceId, orderId);
        return true;
    }

    @Override
    public Map<String, Object> getPaidResourceList(String resourceType, Integer page, Integer size) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        Page<PaidResource> pageParam = new Page<>(safePage, safeSize);

        LambdaQueryWrapper<PaidResource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaidResource::getIsEnabled, 1)
                .eq(PaidResource::getStatus, "approved")
                .exists(publicOwnerExistsSql("owner_id"));

        if (ObjectUtils.isNotEmpty(resourceType)) {
            wrapper.eq(PaidResource::getResourceType, resourceType);
        }

        wrapper.orderByDesc(PaidResource::getSalesCount);

        Page<PaidResource> resultPage = paidResourceMapper.selectPage(pageParam, wrapper);

        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> records = new ArrayList<>();
        for (PaidResource resource : resultPage.getRecords()) {
            records.add(buildPublicResourceResult(resource, "list"));
        }
        result.put("list", records);
        result.put("total", resultPage.getTotal());
        result.put("page", safePage);
        result.put("size", safeSize);

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reviewPaidResource(Long resourceId, Long reviewerId,
                                                 Boolean approved, String reviewReason) {
        if (resourceId == null || reviewerId == null || approved == null) {
            throw new BusinessException("审核参数不能为空");
        }
        if (reviewReason != null && reviewReason.length() > 1000) {
            throw new BusinessException("审核原因不能超过1000字");
        }
        PaidResource resource = paidResourceMapper.selectById(resourceId);
        if (resource == null) {
            throw new BusinessException("付费资源配置不存在");
        }

        boolean initialReview = "pending".equals(resource.getStatus());
        boolean changeReview = "approved".equals(resource.getStatus())
                && Integer.valueOf(1).equals(resource.getIsEnabled())
                && "pending".equals(resource.getChangeReviewStatus());
        if (!initialReview && !changeReview) {
            throw new BusinessException("资源已审核");
        }
        if (!Integer.valueOf(1).equals(resource.getIsEnabled())) {
            throw new BusinessException("资源已取消");
        }

        if (approved) {
            creatorEligibilityService.requireEligible(resource.getOwnerId(), "通过付费资源审核");
        }

        if (changeReview) {
            return reviewCandidateChange(resource, reviewerId, approved, reviewReason);
        }

        String nextStatus = approved ? "approved" : "rejected";
        int updated = paidResourceMapper.update(null, new LambdaUpdateWrapper<PaidResource>()
                .eq(PaidResource::getId, resourceId)
                .eq(PaidResource::getStatus, "pending")
                .eq(PaidResource::getIsEnabled, 1)
                .set(PaidResource::getStatus, nextStatus)
                .set(!approved, PaidResource::getIsEnabled, 0));
        if (updated != 1) {
            throw new BusinessException("资源已被其他审核人处理");
        }
        resource.setStatus(nextStatus);
        if (!approved) {
            resource.setIsEnabled(0);
        }
        syncFormalPaidProjection(resource, approved);
        searchIndexService.sync(resource.getResourceType(), resource.getResourceId());
        notifyReviewResult(resource, approved, false);

        log.info("event=paid_resource_initial_reviewed resourceId={} approved={} reviewerId={}",
                resourceId, approved, reviewerId);

        Map<String, Object> result = new HashMap<>();
        result.put("resourceId", resourceId);
        result.put("approved", approved);
        result.put("status", resource.getStatus());
        result.put("reviewType", "initial");
        result.put("message", approved ? "审核通过" : "审核拒绝");

        return result;
    }

    private void syncFormalPaidProjection(PaidResource resource, boolean enabled) {
        int updated;
        switch (resource.getResourceType()) {
            case "song":
                Integer priceInCents = enabled && resource.getPrice() != null
                        ? resource.getPrice().movePointRight(2).intValueExact()
                        : null;
                updated = songMapper.update(null, new LambdaUpdateWrapper<Song>()
                        .eq(Song::getId, resource.getResourceId())
                        .eq(Song::getDeleted, 0)
                        .set(Song::getIsPaid, enabled ? 1 : 0)
                        .set(Song::getPrice, priceInCents));
                break;
            case "album":
                updated = albumMapper.update(null, new LambdaUpdateWrapper<Album>()
                        .eq(Album::getId, resource.getResourceId())
                        .eq(Album::getDeleted, 0)
                        .set(Album::getIsPaid, enabled ? 1 : 0)
                        .set(Album::getPaidResourceId, enabled ? resource.getId() : null)
                        .set(Album::getPrice, enabled ? resource.getPrice() : null));
                break;
            case "playlist":
                updated = playlistMapper.update(null, new LambdaUpdateWrapper<Playlist>()
                        .eq(Playlist::getId, resource.getResourceId())
                        .eq(Playlist::getDeleted, 0)
                        .set(Playlist::getIsPaid, enabled ? 1 : 0)
                        .set(Playlist::getPrice, enabled ? resource.getPrice() : null)
                        .set(Playlist::getSubscribePeriod, enabled ? resource.getSubscribePeriod() : null));
                break;
            case "mv":
                return;
            default:
                throw new BusinessException("不支持的资源类型");
        }
        if (updated != 1) {
            throw new BusinessException("正式资源状态已变化，付费配置未生效");
        }
    }




    private void syncStoppedSaleProjection(PaidResource resource) {
        int updated;
        switch (resource.getResourceType()) {
            case "song":
                updated = songMapper.update(null, new LambdaUpdateWrapper<Song>()
                        .eq(Song::getId, resource.getResourceId())
                        .eq(Song::getDeleted, 0)
                        .set(Song::getIsPaid, 1)
                        .set(Song::getPrice, null));
                break;
            case "album":
                updated = albumMapper.update(null, new LambdaUpdateWrapper<Album>()
                        .eq(Album::getId, resource.getResourceId())
                        .eq(Album::getDeleted, 0)
                        .set(Album::getIsPaid, 1)
                        .set(Album::getPaidResourceId, resource.getId())
                        .set(Album::getPrice, null));
                break;
            case "playlist":
                updated = playlistMapper.update(null, new LambdaUpdateWrapper<Playlist>()
                        .eq(Playlist::getId, resource.getResourceId())
                        .eq(Playlist::getDeleted, 0)
                        .set(Playlist::getIsPaid, 1)
                        .set(Playlist::getPrice, null)
                        .set(Playlist::getSubscribePeriod, null));
                break;
            case "mv":
                return;
            default:
                throw new BusinessException("不支持的资源类型");
        }
        if (updated != 1) {
            throw new BusinessException("正式资源状态已变化，停售状态未生效");
        }
    }

    @Override
    public Map<String, Object> getPendingPaidResources(Integer page, Integer size) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null ? 20 : Math.max(1, Math.min(size, 100));
        Page<PaidResource> pageParam = new Page<>(safePage, safeSize);

        LambdaQueryWrapper<PaidResource> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(condition -> condition.eq(PaidResource::getStatus, "pending")
                        .or()
                        .eq(PaidResource::getChangeReviewStatus, "pending"))
                .orderByAsc(PaidResource::getCreateTime);

        Page<PaidResource> resultPage = paidResourceMapper.selectPage(pageParam, wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("list", resultPage.getRecords());
        result.put("total", resultPage.getTotal());
        result.put("page", safePage);
        result.put("size", safeSize);

        return result;
    }

    @Override
    public Map<String, Object> getUserPurchasedResources(Long userId, String resourceType,
                                                        Integer page, Integer size) {
        int safePage = ObjectUtils.isEmpty(page) || page < 1 ? 1 : page;
        int safeSize = ObjectUtils.isEmpty(size) || size < 1 ? 20 : Math.min(size, 100);
        Page<UserPurchased> pageParam = new Page<>(safePage, safeSize);

        LambdaQueryWrapper<UserPurchased> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserPurchased::getUserId, userId);

        if (ObjectUtils.isNotEmpty(resourceType)) {
            wrapper.eq(UserPurchased::getResourceType, resourceType);
        }

        wrapper.orderByDesc(UserPurchased::getPurchaseTime);

        Page<UserPurchased> resultPage = userPurchasedMapper.selectPage(pageParam, wrapper);


        if ("playlist".equals(resourceType)) {
            Map<Long, Playlist> playlistById = new HashMap<>();
            java.util.LinkedHashSet<Long> playlistIds = new java.util.LinkedHashSet<>();
            for (UserPurchased purchased : resultPage.getRecords()) {
                if (!ObjectUtils.isEmpty(purchased.getResourceId())) {
                    playlistIds.add(purchased.getResourceId());
                }
            }
            if (!playlistIds.isEmpty()) {
                for (Playlist playlist : playlistMapper.selectBatchIds(playlistIds)) {
                    playlistById.put(playlist.getId(), playlist);
                }
            }

            Map<Long, User> creatorById = new HashMap<>();
            java.util.LinkedHashSet<Long> creatorIds = new java.util.LinkedHashSet<>();
            for (Playlist playlist : playlistById.values()) {
                if (!ObjectUtils.isEmpty(playlist.getUserId())) {
                    creatorIds.add(playlist.getUserId());
                }
            }
            if (!creatorIds.isEmpty()) {
                for (User creator : userMapper.selectBatchIds(creatorIds)) {
                    creatorById.put(creator.getId(), creator);
                }
            }

            List<Map<String, Object>> records = new ArrayList<>();
            for (UserPurchased purchased : resultPage.getRecords()) {
                Playlist playlist = playlistById.get(purchased.getResourceId());
                if (playlist != null) {
                    Map<String, Object> record = new HashMap<>();
                    record.put("id", playlist.getId());
                    record.put("purchaseId", purchased.getId());
                    record.put("resourceType", "playlist");
                    record.put("resourceId", playlist.getId());
                    record.put("name", playlist.getName());
                    record.put("cover", playlist.getCover());
                    record.put("description", playlist.getDescription());
                    record.put("songCount", playlist.getSongCount());
                    record.put("playCount", playlist.getPlayCount());
                    record.put("purchaseTime", purchased.getPurchaseTime());
                    record.put("expireTime", purchased.getExpireTime());


                    User creator = creatorById.get(playlist.getUserId());
                    if (creator != null) {
                        record.put("creatorName", creator.getNickname());
                        record.put("creatorAvatar", creator.getAvatar());
                    }

                    records.add(record);
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("records", records);
            result.put("total", resultPage.getTotal());
            result.put("page", safePage);
            result.put("size", safeSize);

            return result;
        }


        Map<String, Object> result = new HashMap<>();
        result.put("list", resultPage.getRecords());
        result.put("total", resultPage.getTotal());
        result.put("page", safePage);
        result.put("size", safeSize);

        return result;
    }

    @Override
    public Boolean isPriceInRange(String resourceType, BigDecimal price) {
        return priceRangeConfig.isPriceInRange(resourceType, price);
    }

    @Override
    public Map<String, BigDecimal> getPriceRange(String resourceType) {
        PriceRangeConfig.PriceRange range = priceRangeConfig.getPriceRange(resourceType);
        Map<String, BigDecimal> result = new HashMap<>();
        result.put("min", range.getMin());
        result.put("max", range.getMax());
        return result;
    }






    private Boolean isValidResourceType(String resourceType) {
        return "song".equals(resourceType)
                || "mv".equals(resourceType)
                || "album".equals(resourceType)
                || "playlist".equals(resourceType);
    }




    private Map<String, Object> buildResourceResult(PaidResource resource, String type) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", resource.getId());
        result.put("resourceType", resource.getResourceType());
        result.put("resourceId", resource.getResourceId());
        result.put("ownerId", resource.getOwnerId());
        result.put("price", resource.getPrice());
        result.put("isEnabled", resource.getIsEnabled());
        result.put("status", resource.getStatus());
        result.put("salesCount", resource.getSalesCount());
        result.put("totalEarnings", resource.getTotalEarnings());
        result.put("subscribePeriod", resource.getSubscribePeriod());
        result.put("candidatePrice", resource.getCandidatePrice());
        result.put("candidateSubscribePeriod", resource.getCandidateSubscribePeriod());
        result.put("changeReviewStatus", resource.getChangeReviewStatus());
        result.put("type", type);
        return result;
    }




    private void stageCandidateChange(PaidResource resource, BigDecimal price, Integer subscribePeriod,
                                      String changeType, String changeReason) {
        if ("pending".equals(resource.getChangeReviewStatus())) {
            throw new BusinessException("付费配置变更正在审核中，暂不能覆盖");
        }
        int updated = paidResourceMapper.update(null, new LambdaUpdateWrapper<PaidResource>()
                .eq(PaidResource::getId, resource.getId())
                .eq(PaidResource::getOwnerId, resource.getOwnerId())
                .eq(PaidResource::getStatus, "approved")
                .eq(PaidResource::getIsEnabled, 1)
                .and(condition -> condition.isNull(PaidResource::getChangeReviewStatus)
                        .or().ne(PaidResource::getChangeReviewStatus, "pending"))
                .set(PaidResource::getCandidatePrice, price)
                .set(PaidResource::getCandidateSubscribePeriod, subscribePeriod)
                .set(PaidResource::getCandidateChangeType, changeType)
                .set(PaidResource::getCandidateChangeReason, changeReason)
                .set(PaidResource::getChangeReviewStatus, "pending")
                .setSql("candidate_submitted_at = NOW()")
                .set(PaidResource::getCandidateReviewerId, null)
                .set(PaidResource::getCandidateReviewTime, null)
                .set(PaidResource::getCandidateReviewReason, null));
        if (updated != 1) {
            throw new BusinessException("付费配置状态已变化，请刷新后重试");
        }
        resource.setCandidatePrice(price);
        resource.setCandidateSubscribePeriod(subscribePeriod);
        resource.setCandidateChangeType(changeType);
        resource.setCandidateChangeReason(changeReason);
        resource.setChangeReviewStatus("pending");
        resource.setCandidateSubmittedAt(LocalDateTime.now());
        resource.setCandidateReviewerId(null);
        resource.setCandidateReviewTime(null);
        resource.setCandidateReviewReason(null);

        log.info("event=paid_resource_change_submitted ownerId={} resourceType={} resourceId={} candidatePrice={}",
                resource.getOwnerId(), resource.getResourceType(), resource.getResourceId(), price);
    }




    private Map<String, Object> reviewCandidateChange(PaidResource resource, Long reviewerId,
                                                      boolean approved, String reviewReason) {

        if (approved) {
            validateCandidateChange(resource);
        }
        LambdaUpdateWrapper<PaidResource> update = new LambdaUpdateWrapper<PaidResource>()
                .eq(PaidResource::getId, resource.getId())
                .eq(PaidResource::getStatus, "approved")
                .eq(PaidResource::getIsEnabled, 1)
                .eq(PaidResource::getChangeReviewStatus, "pending")
                .set(PaidResource::getChangeReviewStatus, approved ? "approved" : "rejected")
                .set(PaidResource::getCandidateReviewerId, reviewerId)
                .setSql("candidate_review_time = NOW()")
                .set(PaidResource::getCandidateReviewReason, reviewReason);
        if (approved) {
            update.set(PaidResource::getPrice, resource.getCandidatePrice())
                    .set(PaidResource::getSubscribePeriod, resource.getCandidateSubscribePeriod())
                    .set(PaidResource::getChangeType, resource.getCandidateChangeType())
                    .set(PaidResource::getChangeReason, resource.getCandidateChangeReason());
        }
        if (paidResourceMapper.update(null, update) != 1) {
            throw new BusinessException("资源变更已被其他审核人处理");
        }

        resource.setChangeReviewStatus(approved ? "approved" : "rejected");
        resource.setCandidateReviewerId(reviewerId);
        resource.setCandidateReviewTime(LocalDateTime.now());
        resource.setCandidateReviewReason(reviewReason);
        if (approved) {
            resource.setPrice(resource.getCandidatePrice());
            resource.setSubscribePeriod(resource.getCandidateSubscribePeriod());
            resource.setChangeType(resource.getCandidateChangeType());
            resource.setChangeReason(resource.getCandidateChangeReason());
            syncFormalPaidProjection(resource, true);
            searchIndexService.sync(resource.getResourceType(), resource.getResourceId());
        }
        notifyReviewResult(resource, approved, true);

        log.info("event=paid_resource_change_reviewed resourceId={} approved={} reviewerId={}",
                resource.getId(), approved, reviewerId);
        Map<String, Object> result = new HashMap<>();
        result.put("resourceId", resource.getId());
        result.put("approved", approved);
        result.put("status", resource.getStatus());
        result.put("changeReviewStatus", resource.getChangeReviewStatus());
        result.put("reviewType", "change");
        result.put("message", approved ? "变更审核通过" : "变更审核拒绝");
        return result;
    }




    private void validateCandidateChange(PaidResource resource) {
        BigDecimal price = resource.getCandidatePrice();
        Integer subscribePeriod = resource.getCandidateSubscribePeriod();
        if (!isValidResourceType(resource.getResourceType())
                || resource.getResourceId() == null
                || resource.getOwnerId() == null
                || resource.getCandidateSubmittedAt() == null
                || price == null
                || price.scale() > 2
                || !priceRangeConfig.isPriceInRange(resource.getResourceType(), price)) {
            throw new BusinessException("待审核付费配置无效，请重新提交");
        }
        if (subscribePeriod != null && !ALLOWED_SUBSCRIBE_PERIODS.contains(subscribePeriod)) {
            throw new BusinessException("待审核订阅周期无效，请重新提交");
        }
        if ("playlist".equals(resource.getResourceType()) && !Integer.valueOf(30).equals(subscribePeriod)) {
            throw new BusinessException("付费歌单仅支持30天订阅周期");
        }
    }

    private boolean samePriceAndPeriod(PaidResource resource, BigDecimal price, Integer subscribePeriod) {
        return resource.getPrice() != null
                && price != null
                && resource.getPrice().compareTo(price) == 0
                && java.util.Objects.equals(resource.getSubscribePeriod(), subscribePeriod);
    }




    private void notifyReviewResult(PaidResource resource, boolean approved, boolean changeReview) {
        String subject = changeReview ? "付费配置变更审核结果" : "付费资源审核结果";
        String content = approved
                ? (changeReview ? "您的付费配置变更已审核通过" : "您的付费资源申请已审核通过")
                : (changeReview ? "您的付费配置变更未通过审核" : "您的付费资源申请未通过审核");
        notificationService.sendSystemNotification(
                resource.getOwnerId(), subject, content, "/creator/paid-resources");
    }

    private Map<String, Object> buildPublicResourceResult(PaidResource resource, String type) {
        Map<String, Object> result = new HashMap<>();
        result.put("resourceType", resource.getResourceType());
        result.put("resourceId", resource.getResourceId());
        result.put("price", resource.getPrice());
        result.put("subscribePeriod", resource.getSubscribePeriod());
        result.put("type", type);
        return result;
    }

    private PaidResource getActivePublicPaidResource(String resourceType, Long resourceId) {
        LambdaQueryWrapper<PaidResource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaidResource::getResourceType, resourceType)
                .eq(PaidResource::getResourceId, resourceId)
                .eq(PaidResource::getIsEnabled, 1)
                .eq(PaidResource::getStatus, "approved");

        PaidResource resource = paidResourceMapper.selectOne(wrapper);
        if (resource == null || !canSellOwner(resource.getOwnerId())) {
            throw new BusinessException("资源不存在或未设置为付费");
        }
        return resource;
    }

    private void requireResourceOwner(Long ownerId, String resourceType, Long resourceId) {
        if (ObjectUtils.isEmpty(resourceId)) {
            throw new BusinessException("资源ID不能为空");
        }

        boolean ownerMatched;
        switch (resourceType) {
            case "playlist":
                ownerMatched = ownsPlaylist(ownerId, resourceId);
                break;
            case "song":
                ownerMatched = ownsSong(ownerId, resourceId);
                break;
            case "mv":
                ownerMatched = ownsMv(ownerId, resourceId);
                break;
            case "album":
                ownerMatched = ownsAlbum(ownerId, resourceId);
                break;
            default:
                throw new BusinessException("不支持的资源类型");
        }

        if (!ownerMatched) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权设置他人资源为付费");
        }
    }

    private boolean ownsPlaylist(Long ownerId, Long resourceId) {
        Playlist playlist = playlistMapper.selectById(resourceId);
        if (playlist == null || Integer.valueOf(1).equals(playlist.getDeleted())) {
            return false;
        }
        Long actualOwnerId = playlist.getCreatorId() != null ? playlist.getCreatorId() : playlist.getUserId();
        return ownerId.equals(actualOwnerId);
    }

    private boolean ownsSong(Long ownerId, Long resourceId) {
        Song song = songMapper.selectById(resourceId);
        return song != null
                && !Integer.valueOf(1).equals(song.getDeleted())
                && ownerId.equals(song.getUploaderId());
    }

    private boolean ownsMv(Long ownerId, Long resourceId) {
        MV mv = mvMapper.selectById(resourceId);
        if (mv == null || Integer.valueOf(1).equals(mv.getDeleted()) || ObjectUtils.isEmpty(mv.getSongId())) {
            return false;
        }
        return ownsSong(ownerId, mv.getSongId());
    }

    private boolean ownsAlbum(Long ownerId, Long resourceId) {
        Album album = albumMapper.selectById(resourceId);
        if (album == null || Integer.valueOf(1).equals(album.getDeleted())) {
            return false;
        }
        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Song::getAlbumId, resourceId)
                .eq(Song::getUploaderId, ownerId)
                .eq(Song::getDeleted, 0)
                .last("LIMIT 1");
        return songMapper.selectOne(wrapper) != null;
    }

    private boolean canSellOwner(Long ownerId) {
        return creatorEligibilityService.isEligible(ownerId);
    }

    private String publicOwnerExistsSql(String ownerColumn) {
        return PublicStatsSql.USER_EXISTS_PREFIX + ownerColumn + PublicStatsSql.ACTIVE_CREATOR_FILTER;
    }

    private boolean isExpired(UserPurchased purchased) {
        return purchased.getExpireTime() != null && LocalDateTime.now().isAfter(purchased.getExpireTime());
    }

    private String purchaseOrderRemark(String resourceType, Long paidResourceId) {
        return "resourceType=" + resourceType + ";paidResourceId=" + paidResourceId;
    }
}
