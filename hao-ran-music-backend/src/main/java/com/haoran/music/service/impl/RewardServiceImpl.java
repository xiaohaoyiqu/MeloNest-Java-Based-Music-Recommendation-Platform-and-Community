




package com.haoran.music.service.impl;

import com.haoran.music.common.config.PaymentConfig;
import com.haoran.music.common.config.RewardRulesConfig;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.*;
import com.haoran.music.mapper.*;
import com.haoran.music.service.RewardService;
import com.haoran.music.service.PaymentOrderService;
import com.haoran.music.service.PermissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;




@Slf4j
@Service
public class RewardServiceImpl implements RewardService {

    @javax.annotation.Resource
    private com.haoran.music.service.CreatorEligibilityService creatorEligibilityService;

    @javax.annotation.Resource
    private PermissionService permissionService;

    @javax.annotation.Resource
    private SongMapper songMapper;

    @javax.annotation.Resource
    private AlbumMapper albumMapper;

    @javax.annotation.Resource
    private PlaylistMapper playlistMapper;

    @javax.annotation.Resource
    private MVMapper mvMapper;

    @javax.annotation.Resource
    private com.haoran.music.service.NotificationService notificationService;

    private final RewardRecordMapper rewardRecordMapper;
    private final SongLikeMapper songLikeMapper;
    private final RewardDailyLimitMapper rewardDailyLimitMapper;
    private final RewardAlertMapper rewardAlertMapper;
    private final UserMapper userMapper;
    private final CreatorEarningsMapper creatorEarningsMapper;
    private final PaymentOrderService paymentOrderService;
    private final PaymentConfig paymentConfig;
    private final RewardRulesConfig rewardRulesConfig;
    private final RedisTemplate<String, Object> redisTemplate;


    public RewardServiceImpl(RewardRecordMapper rewardRecordMapper,
                           RewardDailyLimitMapper rewardDailyLimitMapper,
                           RewardAlertMapper rewardAlertMapper,
                           UserMapper userMapper,
                           CreatorEarningsMapper creatorEarningsMapper,
                           SongLikeMapper songLikeMapper,
                           @Lazy PaymentOrderService paymentOrderService,
                           PaymentConfig paymentConfig,
                           RewardRulesConfig rewardRulesConfig,
                           RedisTemplate<String, Object> redisTemplate) {
        this.rewardRecordMapper = rewardRecordMapper;
        this.rewardDailyLimitMapper = rewardDailyLimitMapper;
        this.rewardAlertMapper = rewardAlertMapper;
        this.userMapper = userMapper;
        this.creatorEarningsMapper = creatorEarningsMapper;
        this.songLikeMapper = songLikeMapper;
        this.paymentOrderService = paymentOrderService;
        this.paymentConfig = paymentConfig;
        this.rewardRulesConfig = rewardRulesConfig;
        this.redisTemplate = redisTemplate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> rewardCreator(Long userId, Long creatorId, BigDecimal amount,
                                            String message, Long resourceId,
                                            String resourceType, Boolean isAnonymous) {
        if (userId == null || creatorId == null || amount == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "打赏用户、创作者和金额不能为空");
        }
        if (userId.equals(creatorId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不能打赏自己");
        }
        if (message != null && message.length() > 500) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "打赏留言不能超过500字");
        }


        User user = userMapper.selectById(userId);
        if (ObjectUtils.isEmpty(user)) {
            throw new BusinessException("用户不存在");
        }


        creatorEligibilityService.requireEligible(creatorId, "接收打赏");
        User creator = userMapper.selectById(creatorId);

        String normalizedResourceType = normalizeResourceType(resourceId, resourceType);
        validateRewardResource(resourceId, normalizedResourceType, creatorId);


        if (amount.compareTo(rewardRulesConfig.getMinAmount()) < 0 || amount.compareTo(rewardRulesConfig.getMaxAmount()) > 0) {
            throw new BusinessException("单次打赏金额为" + rewardRulesConfig.getMinAmount() + "-" + rewardRulesConfig.getMaxAmount() + "元");
        }


        if (!checkUserStatus(user)) {
            throw new BusinessException(UserAccountStatusUtil.currentUnavailableMessage(user) + "，无法打赏");
        }


        if (!UserAccountStatusUtil.canInteract(creator)) {
            throw new BusinessException(UserAccountStatusUtil.targetUnavailableMessage(creator) + "，无法收款");
        }
        if (!checkCreatorReceiveStatus(creatorId)) {
            throw new BusinessException("创作者当前无法收款");
        }


        reserveDailyLimit(userId, amount);


        String orderNo = "RWD" + UUID.randomUUID().toString().replace("-", "");


        RewardRecord reward = new RewardRecord();
        reward.setOrderNo(orderNo);
        reward.setUserId(userId);
        reward.setCreatorId(creatorId);
        reward.setResourceId(resourceId);
        reward.setResourceType(normalizedResourceType);
        reward.setAmount(amount);
        reward.setMessage(message);
        reward.setStatus("pending");
        reward.setIsAnonymous(isAnonymous != null && isAnonymous ? 1 : 0);

        if (rewardRecordMapper.insert(reward) != 1 || reward.getId() == null) {
            throw new BusinessException("打赏记录创建失败");
        }


        Map<String, Object> orderResult = paymentOrderService.createOrder(
                userId,
                "reward",
                reward.getId(),
                amount,
                creatorId,
                message != null ? "打赏: " + message : "打赏",
                "reward:" + reward.getId()
        );


        Object orderIdValue = orderResult == null ? null : orderResult.get("orderId");
        if (!(orderIdValue instanceof Number)) {
            throw new BusinessException("打赏支付订单创建失败");
        }
        Long orderId = ((Number) orderIdValue).longValue();
        reward.setPaymentOrderId(orderId);
        if (rewardRecordMapper.updateById(reward) != 1) {
            throw new BusinessException("打赏记录关联支付订单失败");
        }

        log.info("event=reward_created userId={} creatorId={} rewardId={}",
                userId, creatorId, reward.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("rewardId", reward.getId());
        result.put("orderNo", orderNo);
        result.put("amount", amount);
        result.put("paymentOrderId", orderResult.get("orderId"));
        result.put("message", "打赏订单创建成功");

        return result;
    }

    @Override
    public Map<String, Object> getMyRewardRecords(Long userId, Integer page, Integer size) {
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        Page<RewardRecord> pageParam = new Page<>(safePage, safeSize);

        LambdaQueryWrapper<RewardRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RewardRecord::getUserId, userId)
                .orderByDesc(RewardRecord::getCreateTime);

        Page<RewardRecord> resultPage = rewardRecordMapper.selectPage(pageParam, wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("list", resultPage.getRecords());
        result.put("total", resultPage.getTotal());
        result.put("page", safePage);
        result.put("size", safeSize);

        return result;
    }

    @Override
    public Map<String, Object> getReceivedRewards(Long creatorId, Integer page, Integer size) {
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        Page<RewardRecord> pageParam = new Page<>(safePage, safeSize);

        LambdaQueryWrapper<RewardRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RewardRecord::getCreatorId, creatorId)
                .orderByDesc(RewardRecord::getCreateTime);

        Page<RewardRecord> resultPage = rewardRecordMapper.selectPage(pageParam, wrapper);

        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> records = new ArrayList<>();
        for (RewardRecord reward : resultPage.getRecords()) {
            records.add(toReceivedSummary(reward));
        }
        result.put("list", records);
        result.put("total", resultPage.getTotal());
        result.put("page", safePage);
        result.put("size", safeSize);

        return result;
    }

    @Override
    public Map<String, Object> getResourceRewards(Long resourceId, String resourceType,
                                                  Integer page, Integer size) {
        if (resourceId == null || resourceId <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "资源ID无效");
        }
        String normalizedResourceType = normalizeResourceType(resourceId, resourceType);
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        Page<RewardRecord> pageParam = new Page<>(safePage, safeSize);

        LambdaQueryWrapper<RewardRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RewardRecord::getResourceId, resourceId)
                .eq(RewardRecord::getResourceType, normalizedResourceType)
                .eq(RewardRecord::getStatus, "paid")
                .orderByDesc(RewardRecord::getCreateTime);

        Page<RewardRecord> resultPage = rewardRecordMapper.selectPage(pageParam, wrapper);

        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> records = new ArrayList<>();
        for (RewardRecord reward : resultPage.getRecords()) {
            records.add(toPublicSummary(reward));
        }
        result.put("list", records);
        result.put("total", resultPage.getTotal());
        result.put("page", safePage);
        result.put("size", safeSize);

        return result;
    }

    @Override
    public Map<String, Object> getRewardDetail(Long rewardId, Long viewerId) {
        RewardRecord reward = rewardRecordMapper.selectById(rewardId);
        if (reward == null) {
            throw new BusinessException("打赏记录不存在");
        }

        boolean payer = viewerId != null && viewerId.equals(reward.getUserId());
        boolean creator = viewerId != null && viewerId.equals(reward.getCreatorId());
        boolean admin = viewerId != null && permissionService.isAdmin(viewerId);
        if (!payer && !creator && !admin) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看此打赏记录");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("rewardId", reward.getId());
        if (payer || admin) {
            result.put("orderNo", reward.getOrderNo());
        }
        boolean hidePayer = Integer.valueOf(1).equals(reward.getIsAnonymous()) && !payer && !admin;
        result.put("userId", hidePayer ? null : reward.getUserId());
        result.put("creatorId", reward.getCreatorId());
        result.put("amount", reward.getAmount());
        result.put("message", reward.getMessage());
        result.put("status", reward.getStatus());
        result.put("isAnonymous", reward.getIsAnonymous());
        result.put("createTime", reward.getCreateTime());

        return result;
    }

    @Override
    public Map<String, Object> getRewardStatistics(Long creatorId) {
        LambdaQueryWrapper<RewardRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RewardRecord::getCreatorId, creatorId)
                .eq(RewardRecord::getStatus, "paid");

        List<RewardRecord> rewards = rewardRecordMapper.selectList(wrapper);

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (RewardRecord reward : rewards) {
            totalAmount = totalAmount.add(reward.getAmount());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalAmount", totalAmount);
        result.put("totalCount", rewards.size());
        result.put("creatorId", creatorId);

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean completeReward(Long rewardId) {
        RewardRecord reward = rewardRecordMapper.selectById(rewardId);
        if (reward == null) {
            return false;
        }

        if ("paid".equals(reward.getStatus())) {
            return true;
        }
        if (!"pending".equals(reward.getStatus())) {
            return false;
        }


        if (rewardRecordMapper.markPaidIfPending(rewardId) != 1) {
            RewardRecord current = rewardRecordMapper.selectById(rewardId);
            return current != null && "paid".equals(current.getStatus());
        }


        BigDecimal platformFee = paymentConfig.calculatePlatformFee(reward.getAmount());
        BigDecimal creatorEarnings = paymentConfig.calculateCreatorEarnings(reward.getAmount());
        log.debug("event=reward_platform_fee_recorded rewardId={}", rewardId);


        CreatorEarnings earnings = new CreatorEarnings();
        earnings.setUserId(reward.getCreatorId());

        earnings.setWorkId(rewardId);
        earnings.setWorkType("reward");
        earnings.setEarningsType("reward");

        earnings.setEarningsAmount(creatorEarnings.multiply(new BigDecimal("100")).longValue());
        earnings.setCreateTime(LocalDateTime.now());
        if (creatorEarningsMapper.insert(earnings) != 1) {
            throw new BusinessException("打赏收益明细写入失败");
        }

        log.debug("event=reward_creator_earnings_recorded rewardId={}", rewardId);


        if (userMapper.incrementRewardEarnings(reward.getCreatorId(), creatorEarnings) != 1) {
            throw new BusinessException("创作者收益汇总更新失败");
        }

        User payer = Integer.valueOf(1).equals(reward.getIsAnonymous())
                ? null : userMapper.selectById(reward.getUserId());
        String payerName = payer == null ? null
                : (payer.getNickname() != null ? payer.getNickname() : payer.getUsername());
        notificationService.sendRewardNotificationOnce(
                rewardId, reward.getCreatorId(),
                Integer.valueOf(1).equals(reward.getIsAnonymous()) ? null : reward.getUserId(),
                payerName, payer == null ? null : payer.getAvatar(), reward.getAmount(),
                reward.getResourceId(), reward.getResourceType());


        checkAndCreateHighAmountAlert(reward.getUserId(), reward.getCreatorId());

        log.info("event=reward_completed rewardId={}", rewardId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean cancelReward(Long rewardId, Long userId) {
        RewardRecord reward = rewardRecordMapper.selectById(rewardId);
        if (reward == null) {
            throw new BusinessException("打赏记录不存在");
        }

        if (!reward.getUserId().equals(userId)) {
            throw new BusinessException("无权操作此打赏");
        }

        if (!"pending".equals(reward.getStatus())) {
            throw new BusinessException("只能取消待支付的打赏");
        }

        if (rewardRecordMapper.cancelPending(rewardId, userId) != 1) {
            throw new BusinessException("打赏状态已变化，请刷新后重试");
        }


        LocalDate reservationDate = reward.getCreateTime() == null
                ? LocalDate.now() : reward.getCreateTime().toLocalDate();
        releaseDailyLimit(userId, reservationDate, reward.getAmount());

        log.info("event=reward_cancelled rewardId={} userId={}", rewardId, userId);
        return true;
    }

    @Override
    public Boolean checkRewardPermission(Long userId, BigDecimal amount) {

        User user = userMapper.selectById(userId);
        if (!UserAccountStatusUtil.canInteract(user)) {
            return false;
        }


        if (amount.compareTo(rewardRulesConfig.getMinAmount()) < 0 || amount.compareTo(rewardRulesConfig.getMaxAmount()) > 0) {
            return false;
        }

        return true;
    }

    @Override
    public Boolean checkCreatorReceiveStatus(Long creatorId) {
        return creatorEligibilityService.isEligible(creatorId);
    }








    private boolean checkUserStatus(User user) {
        return UserAccountStatusUtil.canInteract(user);
    }

    private void reserveDailyLimit(Long userId, BigDecimal amount) {
        LocalDate today = LocalDate.now();
        rewardDailyLimitMapper.ensureDailyLimit(userId, today);
        int reserved = rewardDailyLimitMapper.reserveWithinLimits(
                userId, today, amount,
                rewardRulesConfig.getDailyMaxCount(), rewardRulesConfig.getDailyMaxAmount());
        if (reserved != 1) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS,
                    "今日打赏次数或金额已达上限");
        }
        evictDailyLimitCache(userId, today);
    }

    private void releaseDailyLimit(Long userId, LocalDate limitDate, BigDecimal amount) {
        if (rewardDailyLimitMapper.releaseReservation(userId, limitDate, amount) != 1) {
            throw new BusinessException("打赏额度回退失败");
        }
        evictDailyLimitCache(userId, limitDate);
    }

    private void evictDailyLimitCache(Long userId, LocalDate limitDate) {
        String suffix = userId + ":" + limitDate;
        try {
            redisTemplate.delete(Arrays.asList("reward:count:" + suffix, "reward:amount:" + suffix));
        } catch (RuntimeException exception) {

            log.warn("event=reward_limit_cache_cleanup_failed userId={}", userId);
        }
    }

    private String normalizeResourceType(Long resourceId, String resourceType) {
        boolean hasId = resourceId != null && resourceId > 0;
        boolean hasType = resourceType != null && !resourceType.trim().isEmpty();
        if (!hasId && !hasType) {
            return null;
        }
        if (!hasId || !hasType) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "关联资源ID和类型必须同时提供");
        }
        String normalized = resourceType.trim().toLowerCase(Locale.ROOT);
        if (!Arrays.asList("song", "album", "playlist", "mv").contains(normalized)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的打赏资源类型");
        }
        return normalized;
    }

    private void validateRewardResource(Long resourceId, String resourceType, Long creatorId) {
        if (resourceType == null) {
            return;
        }

        Long ownerId;
        switch (resourceType) {
            case "song":
                Song song = songMapper.selectById(resourceId);
                ownerId = song == null ? null : song.getArtistId();
                break;
            case "album":
                Album album = albumMapper.selectById(resourceId);
                ownerId = album == null ? null : album.getArtistId();
                break;
            case "playlist":
                Playlist playlist = playlistMapper.selectById(resourceId);
                if (playlist == null || Integer.valueOf(0).equals(playlist.getIsPublic())) {
                    ownerId = null;
                } else {
                    ownerId = playlist.getCreatorId() != null ? playlist.getCreatorId() : playlist.getUserId();
                }
                break;
            case "mv":
                MV mv = mvMapper.selectById(resourceId);
                ownerId = mv == null ? null : mv.getArtistId();
                break;
            default:
                ownerId = null;
        }

        if (ownerId == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "关联资源不存在或不可公开打赏");
        }
        if (!creatorId.equals(ownerId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "关联资源不属于收款创作者");
        }
    }

    private Map<String, Object> toPublicSummary(RewardRecord reward) {
        Map<String, Object> result = new HashMap<>();
        result.put("amount", reward.getAmount());
        result.put("message", reward.getMessage());
        result.put("isAnonymous", Integer.valueOf(1).equals(reward.getIsAnonymous()));
        result.put("createTime", reward.getCreateTime());
        return result;
    }

    private Map<String, Object> toReceivedSummary(RewardRecord reward) {
        Map<String, Object> result = toPublicSummary(reward);
        result.put("rewardId", reward.getId());
        result.put("status", reward.getStatus());
        result.put("resourceId", reward.getResourceId());
        result.put("resourceType", reward.getResourceType());
        if (!Integer.valueOf(1).equals(reward.getIsAnonymous())) {
            result.put("userId", reward.getUserId());
        }
        return result;
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return 20;
        }
        return Math.min(size, 100);
    }











    private void checkAndCreateHighAmountAlert(Long userId, Long creatorId) {

        LambdaQueryWrapper<RewardRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RewardRecord::getUserId, userId)
                .eq(RewardRecord::getCreatorId, creatorId)
                .eq(RewardRecord::getStatus, "paid");

        List<RewardRecord> records = rewardRecordMapper.selectList(wrapper);

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (RewardRecord record : records) {
            totalAmount = totalAmount.add(record.getAmount());
        }


        if (totalAmount.compareTo(rewardRulesConfig.getAlertAmountThreshold()) >= 0) {

            LambdaQueryWrapper<RewardAlert> alertWrapper = new LambdaQueryWrapper<>();
            alertWrapper.eq(RewardAlert::getUserId, userId)
                    .eq(RewardAlert::getCreatorId, creatorId)
                    .eq(RewardAlert::getAlertType, "high_amount")
                    .eq(RewardAlert::getIsHandled, 0)
                    .orderByDesc(RewardAlert::getCreateTime)
                    .last("LIMIT 1");

            RewardAlert existingAlert = rewardAlertMapper.selectOne(alertWrapper);

            if (existingAlert == null) {

                RewardAlert alert = new RewardAlert();
                alert.setUserId(userId);
                alert.setCreatorId(creatorId);
                alert.setAlertType("high_amount");
                alert.setAlertLevel("info");
                alert.setAlertAmount(totalAmount);
                alert.setAlertDetail("用户对创作者(ID:" + creatorId + ")累计打赏金额达到" + totalAmount + "元，请平台管理员关注");
                alert.setIsHandled(0);
                alert.setCreateTime(LocalDateTime.now());

                rewardAlertMapper.insert(alert);

                log.info("event=reward_high_value_alert_created userId={} creatorId={}",
                        userId, creatorId);
            }
        }
    }




}
