   
                      
   
package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.entity.PaidEntitlementGrant;
import com.haoran.music.entity.PlaylistSubscribe;
import com.haoran.music.entity.PlaylistSubscribeOrder;
import com.haoran.music.entity.UserPurchased;
import com.haoran.music.mapper.PaidEntitlementGrantMapper;
import com.haoran.music.mapper.PlaylistSubscribeMapper;
import com.haoran.music.mapper.PlaylistSubscribeOrderMapper;
import com.haoran.music.mapper.UserPurchasedMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.PaidEntitlementLedgerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

   
                   
   
@Slf4j
@Service
public class PaidEntitlementLedgerServiceImpl implements PaidEntitlementLedgerService {

    private final PaidEntitlementGrantMapper grantMapper;
    private final UserPurchasedMapper userPurchasedMapper;
    private final UserMapper userMapper;
    private final PlaylistSubscribeMapper playlistSubscribeMapper;
    private final PlaylistSubscribeOrderMapper playlistSubscribeOrderMapper;

    public PaidEntitlementLedgerServiceImpl(PaidEntitlementGrantMapper grantMapper,
                                            UserPurchasedMapper userPurchasedMapper,
                                            UserMapper userMapper,
                                            PlaylistSubscribeMapper playlistSubscribeMapper,
                                            PlaylistSubscribeOrderMapper playlistSubscribeOrderMapper) {
        this.grantMapper = grantMapper;
        this.userPurchasedMapper = userPurchasedMapper;
        this.userMapper = userMapper;
        this.playlistSubscribeMapper = playlistSubscribeMapper;
        this.playlistSubscribeOrderMapper = playlistSubscribeOrderMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean recordGrant(Long paymentOrderId, Long userId, String resourceType, Long resourceId,
                               Long paidResourceId, String grantType,
                               LocalDateTime startsAt, LocalDateTime expiresAt) {
        validateGrant(paymentOrderId, userId, resourceType, resourceId, grantType, startsAt, expiresAt);
        if (userMapper.selectByIdForUpdate(userId) == null) {
            throw new BusinessException("付费权益用户不存在");
        }
        PaidEntitlementGrant existing = grantMapper.selectByPaymentOrderId(paymentOrderId);
        if (existing != null) {
            requireSameGrant(existing, userId, resourceType, resourceId, paidResourceId,
                    grantType, startsAt, expiresAt);
            if (!"active".equals(existing.getStatus())) {
                throw new BusinessException("该支付订单的权益已被撤销，不能重新激活");
            }
            return false;
        }

        PaidEntitlementGrant grant = new PaidEntitlementGrant();
        grant.setPaymentOrderId(paymentOrderId);
        grant.setUserId(userId);
        grant.setResourceType(resourceType);
        grant.setResourceId(resourceId);
        grant.setPaidResourceId(paidResourceId);
        grant.setGrantType(grantType);
        grant.setStartsAt(startsAt);
        grant.setExpiresAt(expiresAt);
        grant.setStatus("active");
        try {
            if (grantMapper.insert(grant) != 1) {
                throw new BusinessException("付费权益授予事实创建失败");
            }
        } catch (DuplicateKeyException e) {
            PaidEntitlementGrant concurrent = grantMapper.selectByPaymentOrderId(paymentOrderId);
            if (concurrent == null) {
                throw e;
            }
            requireSameGrant(concurrent, userId, resourceType, resourceId, paidResourceId,
                    grantType, startsAt, expiresAt);
            if (!"active".equals(concurrent.getStatus())) {
                throw new BusinessException("该支付订单的权益已被撤销，不能重新激活");
            }
            return false;
        }
        log.info("event=paid_entitlement_grant_recorded orderId={} userId={} resourceType={} resourceId={}",
                paymentOrderId, userId, resourceType, resourceId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean revokeByRefund(Long paymentOrderId, Long refundId) {
        if (paymentOrderId == null || refundId == null) {
            throw new BusinessException("退款权益参数不能为空");
        }
        PaidEntitlementGrant grant = grantMapper.selectByPaymentOrderId(paymentOrderId);
        if (grant == null) {
            return false;
        }
        if (userMapper.selectByIdForUpdate(grant.getUserId()) == null) {
            throw new BusinessException("退款权益用户不存在");
        }
        if ("revoked".equals(grant.getStatus())) {
            if (!Objects.equals(grant.getRefundId(), refundId)) {
                throw new BusinessException("该权益已由其他退款撤销");
            }
            return true;
        }
        if (!"active".equals(grant.getStatus())
                || grantMapper.revokeActiveByOrder(paymentOrderId, refundId) != 1) {
            throw new BusinessException("退款权益状态已变化，请刷新后重试");
        }
        if (isPlaylistSubscription(grant)) {
            requireSubscriptionOrder(grant, "paid");
            if (playlistSubscribeOrderMapper.markPaidOrderRefunded(paymentOrderId) != 1) {
                throw new BusinessException("订阅业务单退款状态已变化");
            }
        }
        LocalDateTime transactionTime = grantMapper.selectDatabaseTime();
        if (transactionTime == null) {
            throw new BusinessException("无法获取退款事务时间");
        }
        rebaseTimedGrants(grant, transactionTime);
        PaidEntitlementGrant effective = grantMapper.selectEffectiveGrant(
                grant.getUserId(), grant.getResourceType(), grant.getResourceId());
        rebuildPurchasedProjection(grant, effective);
        rebuildPlaylistSubscriptionProjection(grant, effective, transactionTime);
        log.info("event=paid_entitlement_grant_revoked orderId={} refundId={} userId={}",
                paymentOrderId, refundId, grant.getUserId());
        return true;
    }

    private void rebuildPurchasedProjection(PaidEntitlementGrant revoked,
                                            PaidEntitlementGrant effective) {
        UserPurchased projection = userPurchasedMapper.selectOne(new LambdaQueryWrapper<UserPurchased>()
                .eq(UserPurchased::getUserId, revoked.getUserId())
                .eq(UserPurchased::getResourceType, revoked.getResourceType())
                .eq(UserPurchased::getResourceId, revoked.getResourceId())
                .last("LIMIT 1"));
        if (effective == null) {
            if (projection != null && userPurchasedMapper.delete(new LambdaQueryWrapper<UserPurchased>()
                    .eq(UserPurchased::getId, projection.getId())) != 1) {
                throw new BusinessException("退款后权益投影删除失败");
            }
            return;
        }
        if (projection == null) {
            projection = new UserPurchased();
            projection.setUserId(effective.getUserId());
            projection.setResourceType(effective.getResourceType());
            projection.setResourceId(effective.getResourceId());
            applyEffectiveGrant(projection, effective);
            if (userPurchasedMapper.insert(projection) != 1) {
                throw new BusinessException("退款后权益投影重建失败");
            }
            return;
        }
        applyEffectiveGrant(projection, effective);
        if (userPurchasedMapper.updateById(projection) != 1) {
            throw new BusinessException("退款后权益投影更新失败");
        }
    }

       
                                               
       
    private void rebuildPlaylistSubscriptionProjection(PaidEntitlementGrant revoked,
                                                       PaidEntitlementGrant effective,
                                                       LocalDateTime transactionTime) {
        if (!isPlaylistSubscription(revoked)) {
            return;
        }
        PlaylistSubscribe projection = playlistSubscribeMapper.selectOne(
                new LambdaQueryWrapper<PlaylistSubscribe>()
                        .eq(PlaylistSubscribe::getUserId, revoked.getUserId())
                        .eq(PlaylistSubscribe::getPlaylistId, revoked.getResourceId())
                        .eq(PlaylistSubscribe::getDeleted, 0)
                        .last("LIMIT 1"));
        if (effective == null) {
            if (projection == null) {
                return;
            }
            int updated = playlistSubscribeMapper.update(null,
                    new LambdaUpdateWrapper<PlaylistSubscribe>()
                            .eq(PlaylistSubscribe::getId, projection.getId())
                            .eq(PlaylistSubscribe::getDeleted, 0)
                            .set(PlaylistSubscribe::getStatus, "refunded")
                            .set(PlaylistSubscribe::getAutoRenew, 0)
                            .set(PlaylistSubscribe::getEndTime, transactionTime));
            if (updated != 1) {
                throw new BusinessException("退款后订阅投影失效失败");
            }
            projection.setStatus("refunded");
            projection.setAutoRenew(0);
            projection.setEndTime(transactionTime);
            return;
        }
        if (!isPlaylistSubscription(effective) || effective.getExpiresAt() == null) {
            throw new BusinessException("剩余订阅权益事实无效，不能重建投影");
        }
        PlaylistSubscribeOrder sourceOrder = requireSubscriptionOrder(effective, "paid");
        if (projection == null) {
            projection = new PlaylistSubscribe();
            projection.setUserId(effective.getUserId());
            projection.setPlaylistId(effective.getResourceId());
            projection.setDeleted(0);
        }
        projection.setCreatorId(sourceOrder.getCreatorId());
        projection.setSubscribeType(sourceOrder.getSubscribeType());
        projection.setDays(sourceOrder.getDays());
        projection.setAmount(sourceOrder.getAmount());
        projection.setStartTime(effective.getStartsAt());
        projection.setEndTime(effective.getExpiresAt());
        projection.setAutoRenew(0);
        projection.setStatus("active");
        projection.setPaymentOrderId(effective.getPaymentOrderId());
        int affected = projection.getId() == null
                ? playlistSubscribeMapper.insert(projection)
                : playlistSubscribeMapper.updateById(projection);
        if (affected != 1) {
            throw new BusinessException("退款后订阅投影重建失败");
        }
    }

    private boolean isPlaylistSubscription(PaidEntitlementGrant grant) {
        return grant != null
                && "subscribe".equals(grant.getGrantType())
                && "playlist".equals(grant.getResourceType());
    }

    private PlaylistSubscribeOrder requireSubscriptionOrder(PaidEntitlementGrant grant,
                                                            String expectedStatus) {
        PlaylistSubscribeOrder order = playlistSubscribeOrderMapper
                .selectByPaymentOrderId(grant.getPaymentOrderId());
        if (order == null
                || !Objects.equals(order.getUserId(), grant.getUserId())
                || !Objects.equals(order.getPlaylistId(), grant.getResourceId())
                || !Objects.equals(expectedStatus, order.getStatus())) {
            throw new BusinessException("订阅权益与业务单事实不一致");
        }
        return order;
    }

       
                                               
       
    private void rebaseTimedGrants(PaidEntitlementGrant revoked, LocalDateTime transactionTime) {
        List<PaidEntitlementGrant> grants = grantMapper.selectActiveGrants(
                revoked.getUserId(), revoked.getResourceType(), revoked.getResourceId());
        if (grants == null || grants.isEmpty()) {
            return;
        }
        LocalDateTime base = transactionTime;
        for (PaidEntitlementGrant grant : grants) {
            LocalDateTime startsAt = grant.getStartsAt();
            LocalDateTime expiresAt = grant.getExpiresAt();
            if (expiresAt == null) {
                return;
            }
            if (startsAt == null || !expiresAt.isAfter(startsAt)) {
                throw new BusinessException("剩余权益授予区间无效，不能自动重建");
            }
            if (!expiresAt.isAfter(transactionTime)) {
                continue;
            }
            Duration duration = Duration.between(startsAt, expiresAt);
            if (!startsAt.isAfter(transactionTime)) {
                if (expiresAt.isAfter(base)) {
                    base = expiresAt;
                }
                continue;
            }
            LocalDateTime rebasedStart = base;
            LocalDateTime rebasedEnd = rebasedStart.plus(duration);
            if ((!startsAt.equals(rebasedStart) || !expiresAt.equals(rebasedEnd))
                    && grantMapper.rebaseActiveGrant(grant.getId(), startsAt, expiresAt,
                    rebasedStart, rebasedEnd) != 1) {
                throw new BusinessException("剩余权益区间已变化，不能完成退款");
            }
            grant.setStartsAt(rebasedStart);
            grant.setExpiresAt(rebasedEnd);
            base = rebasedEnd;
        }
    }

    private void applyEffectiveGrant(UserPurchased projection, PaidEntitlementGrant grant) {
        projection.setPurchaseType(grant.getGrantType());
        projection.setPurchaseOrderId(grant.getPaymentOrderId());
        projection.setPurchaseTime(grant.getStartsAt());
        projection.setExpireTime(grant.getExpiresAt());
    }

    private void validateGrant(Long paymentOrderId, Long userId, String resourceType, Long resourceId,
                               String grantType, LocalDateTime startsAt, LocalDateTime expiresAt) {
        if (paymentOrderId == null || userId == null || resourceId == null || startsAt == null
                || resourceType == null || resourceType.trim().isEmpty()
                || grantType == null || grantType.trim().isEmpty()) {
            throw new BusinessException("付费权益授予参数无效");
        }
        if (expiresAt != null && !expiresAt.isAfter(startsAt)) {
            throw new BusinessException("付费权益有效期无效");
        }
    }

    private void requireSameGrant(PaidEntitlementGrant grant, Long userId, String resourceType,
                                  Long resourceId, Long paidResourceId, String grantType,
                                  LocalDateTime startsAt, LocalDateTime expiresAt) {
        if (!Objects.equals(grant.getUserId(), userId)
                || !Objects.equals(grant.getResourceType(), resourceType)
                || !Objects.equals(grant.getResourceId(), resourceId)
                || !Objects.equals(grant.getPaidResourceId(), paidResourceId)
                || !Objects.equals(grant.getGrantType(), grantType)
                || !Objects.equals(grant.getStartsAt(), startsAt)
                || !Objects.equals(grant.getExpiresAt(), expiresAt)) {
            throw new BusinessException("支付订单权益授予事实不一致");
        }
    }
}
