package com.haoran.music.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.haoran.music.common.config.PaymentConfig;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.*;
import com.haoran.music.mapper.*;
import com.haoran.music.service.PaymentOrderService;
import com.haoran.music.service.PaidEntitlementLedgerService;
import com.haoran.music.service.PaidResourceService;
import com.haoran.music.service.PlaylistService;
import com.haoran.music.service.SubscribeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

   
           
                      
   
@Service
public class SubscribeServiceImpl implements SubscribeService {

    @javax.annotation.Resource
    private com.haoran.music.service.CreatorEligibilityService creatorEligibilityService;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SubscribeServiceImpl.class);

    private final PlaylistSubscribeMapper playlistSubscribeMapper;
    private final PlaylistMapper playlistMapper;
    private final UserMapper userMapper;
    private final UserPurchasedMapper userPurchasedMapper;
    private final PaymentConfig paymentConfig;
    private final PlaylistSubscribeOrderMapper playlistSubscribeOrderMapper;
    private final PaymentOrderService paymentOrderService;
    private final PaidEntitlementLedgerService entitlementLedgerService;
    private final PlaylistService playlistService;
    private final PaidResourceService paidResourceService;

    public SubscribeServiceImpl(PlaylistSubscribeMapper playlistSubscribeMapper,
                               PlaylistMapper playlistMapper,
                               UserMapper userMapper,
                               UserPurchasedMapper userPurchasedMapper,
                                PaymentConfig paymentConfig,
                                PlaylistSubscribeOrderMapper playlistSubscribeOrderMapper,
                                @Lazy PaymentOrderService paymentOrderService,
                                PaidEntitlementLedgerService entitlementLedgerService,
                                @Lazy PlaylistService playlistService,
                                @Lazy PaidResourceService paidResourceService) {
        this.playlistSubscribeMapper = playlistSubscribeMapper;
        this.playlistMapper = playlistMapper;
        this.userMapper = userMapper;
        this.userPurchasedMapper = userPurchasedMapper;
        this.paymentConfig = paymentConfig;
        this.playlistSubscribeOrderMapper = playlistSubscribeOrderMapper;
        this.paymentOrderService = paymentOrderService;
        this.entitlementLedgerService = entitlementLedgerService;
        this.playlistService = playlistService;
        this.paidResourceService = paidResourceService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> setPlaylistSubscribe(Long creatorId, Long playlistId,
                                                    BigDecimal price, Integer period) {
        if (price == null || period == null || period != 30) {
            throw new BusinessException("付费歌单仅支持30天单月产品");
        }
        final int monthlyPrice;
        try {
            monthlyPrice = price.stripTrailingZeros().intValueExact();
        } catch (ArithmeticException exception) {
            throw new BusinessException("月度价格必须为整数元");
        }
        com.haoran.music.dto.playlist.PlaylistPaidSettingsDTO settings =
                new com.haoran.music.dto.playlist.PlaylistPaidSettingsDTO();
        settings.setPlaylistId(playlistId);
        settings.setMonthlyPrice(monthlyPrice);
        Boolean submitted = playlistService.setPlaylistPaid(creatorId, settings);
        if (!Boolean.TRUE.equals(submitted)) {
            throw new BusinessException("付费歌单申请提交失败");
        }

        BigDecimal normalizedPrice = BigDecimal.valueOf(monthlyPrice).setScale(2);
        BigDecimal platformFee = normalizedPrice.multiply(paymentConfig.getPlatformFeeRate());
        BigDecimal creatorEarning = normalizedPrice.subtract(platformFee);

        log.info("event=playlist_paid_application_submitted creatorId={} playlistId={} monthlyPrice={}",
                creatorId, playlistId, normalizedPrice);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("playlistId", playlistId);
        result.put("price", normalizedPrice);
        result.put("period", 30);
        result.put("status", "pending");
        result.put("platformFee", platformFee);
        result.put("creatorEarning", creatorEarning);
        result.put("message", "付费歌单申请已提交，等待审核");

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> subscribePlaylist(Long userId, Long playlistId,
                                                 String subscribeType, Boolean autoRenew) {
        return subscribePlaylist(userId, playlistId, subscribeType, autoRenew,
                "subscribe:" + UUID.randomUUID().toString().replace("-", ""));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> subscribePlaylist(Long userId, Long playlistId,
                                                 String subscribeType, Boolean autoRenew,
                                                 String idempotencyKey) {
        User user = userMapper.selectById(userId);
        if (ObjectUtil.isEmpty(user)) {
            throw new BusinessException("用户不存在");
        }
        UserAccountStatusUtil.requireCanInteract(user, "订阅歌单");
        if (Boolean.TRUE.equals(autoRenew)) {
            throw new BusinessException("当前支付渠道不支持自动续费，请到期后手动续订");
        }

        Playlist playlist = playlistMapper.selectById(playlistId);
        if (ObjectUtil.isEmpty(playlist)) {
            throw new BusinessException("歌单不存在");
        }
        if (userId.equals(playlist.getUserId())) {
            throw new BusinessException("不能订阅自己的歌单");
        }
        UserAccountStatusUtil.requireCanInteract(playlist.getUserId(), userMapper::selectById, "接收歌单订阅");
        if (!Integer.valueOf(1).equals(playlist.getIsPaid())
                || playlist.getPrice() == null
                || playlist.getPrice().compareTo(BigDecimal.ZERO) <= 0
                || playlist.getSubscribePeriod() == null
                || playlist.getSubscribePeriod() <= 0) {
            throw new BusinessException("歌单未配置有效的付费订阅方案");
        }
        String normalizedKey = normalizeSubscribeIdempotencyKey(idempotencyKey);
        PlaylistSubscribeOrder replay = findSubscribeOrder(userId, normalizedKey);
        if (replay != null) {
            return replaySubscribeOrder(replay, playlistId);
        }

        int days = playlist.getSubscribePeriod();
        String authoritativeType = getTypeByDays(days);
        PlaylistSubscribeOrder subscribeOrder = new PlaylistSubscribeOrder();
        subscribeOrder.setUserId(userId);
        subscribeOrder.setPlaylistId(playlistId);
        subscribeOrder.setCreatorId(playlist.getUserId());
        subscribeOrder.setSubscribeType(authoritativeType);
        subscribeOrder.setDays(days);
        subscribeOrder.setAmount(playlist.getPrice().setScale(2, BigDecimal.ROUND_HALF_UP));
        subscribeOrder.setAutoRenew(0);
        subscribeOrder.setStatus("pending_payment");
        subscribeOrder.setIdempotencyKey(normalizedKey);
        subscribeOrder.setDeleted(0);
        try {
            int inserted = playlistSubscribeOrderMapper.insert(subscribeOrder);
            if (inserted != 1 || subscribeOrder.getId() == null) {
                throw new BusinessException("订阅业务单创建失败");
            }
        } catch (DuplicateKeyException e) {
            PlaylistSubscribeOrder concurrent = findSubscribeOrder(userId, normalizedKey);
            if (concurrent == null) {
                throw e;
            }
            return replaySubscribeOrder(concurrent, playlistId);
        }

        Map<String, Object> payment = paymentOrderService.createOrder(
                userId, "subscribe", subscribeOrder.getId(), subscribeOrder.getAmount(),
                subscribeOrder.getCreatorId(), null, "subscribe-payment:" + subscribeOrder.getId());
        Object paymentOrderValue = payment == null ? null : payment.get("orderId");
        if (!(paymentOrderValue instanceof Number)) {
            throw new BusinessException("支付订单创建失败");
        }
        Long paymentOrderId = ((Number) paymentOrderValue).longValue();
        subscribeOrder.setPaymentOrderId(paymentOrderId);
        requireSingleWrite(playlistSubscribeOrderMapper.updateById(subscribeOrder), "订阅业务单关联支付订单失败");
        payment.put("subscribeOrderId", subscribeOrder.getId());
        payment.put("message", "订阅支付订单已创建");
        return payment;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean completeSubscribeOrder(Long subscribeOrderId, Long paymentOrderId) {
        PlaylistSubscribeOrder order = playlistSubscribeOrderMapper.selectById(subscribeOrderId);
        if (order == null || Integer.valueOf(1).equals(order.getDeleted())) {
            throw new BusinessException("订阅业务单不存在");
        }
        if ("paid".equals(order.getStatus())) {
            return Objects.equals(order.getPaymentOrderId(), paymentOrderId);
        }
        if (!"pending_payment".equals(order.getStatus())
                || !Objects.equals(order.getPaymentOrderId(), paymentOrderId)) {
            throw new BusinessException("订阅业务单状态异常");
        }
        UserAccountStatusUtil.requireCanInteract(
                userMapper.selectByIdForUpdate(order.getUserId()), "完成歌单订阅");
        UserAccountStatusUtil.requireCanInteract(order.getCreatorId(), userMapper::selectById, "接收歌单订阅");

        int claimed = playlistSubscribeOrderMapper.update(null,
                new LambdaUpdateWrapper<PlaylistSubscribeOrder>()
                        .eq(PlaylistSubscribeOrder::getId, subscribeOrderId)
                        .eq(PlaylistSubscribeOrder::getStatus, "pending_payment")
                        .eq(PlaylistSubscribeOrder::getPaymentOrderId, paymentOrderId)
                        .eq(PlaylistSubscribeOrder::getDeleted, 0)
                        .set(PlaylistSubscribeOrder::getStatus, "paid"));
        if (claimed != 1) {
            throw new BusinessException("订阅业务单已被其他请求处理");
        }
        order.setStatus("paid");

        PlaylistSubscribe entitlement = playlistSubscribeMapper.selectOne(
                new LambdaQueryWrapper<PlaylistSubscribe>()
                        .eq(PlaylistSubscribe::getUserId, order.getUserId())
                        .eq(PlaylistSubscribe::getPlaylistId, order.getPlaylistId())
                        .eq(PlaylistSubscribe::getDeleted, 0)
                        .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime baseTime = entitlement != null && entitlement.getEndTime() != null
                && entitlement.getEndTime().isAfter(now) ? entitlement.getEndTime() : now;
        LocalDateTime endTime = baseTime.plusDays(order.getDays());
        entitlementLedgerService.recordGrant(paymentOrderId, order.getUserId(), "playlist",
                order.getPlaylistId(), null, "subscribe", baseTime, endTime);
        if (entitlement == null) {
            entitlement = new PlaylistSubscribe();
            entitlement.setPlaylistId(order.getPlaylistId());
            entitlement.setUserId(order.getUserId());
            entitlement.setCreatorId(order.getCreatorId());
            entitlement.setDeleted(0);
            entitlement.setStartTime(now);
        }
        entitlement.setSubscribeType(order.getSubscribeType());
        entitlement.setDays(order.getDays());
        entitlement.setAmount(order.getAmount());
        entitlement.setEndTime(endTime);
        entitlement.setAutoRenew(order.getAutoRenew());
        entitlement.setStatus("active");
        entitlement.setPaymentOrderId(paymentOrderId);
        if (entitlement.getId() == null) {
            requireSingleWrite(playlistSubscribeMapper.insert(entitlement), "订阅权益创建失败");
        } else {
            requireSingleWrite(playlistSubscribeMapper.updateById(entitlement), "订阅权益更新失败");
        }

        UserPurchased purchased = userPurchasedMapper.selectOne(
                new LambdaQueryWrapper<UserPurchased>()
                        .eq(UserPurchased::getUserId, order.getUserId())
                        .eq(UserPurchased::getResourceType, "playlist")
                        .eq(UserPurchased::getResourceId, order.getPlaylistId())
                        .last("LIMIT 1"));
        if (purchased == null) {
            purchased = new UserPurchased();
            purchased.setUserId(order.getUserId());
            purchased.setResourceType("playlist");
            purchased.setResourceId(order.getPlaylistId());
            purchased.setPurchaseType("subscribe");
            purchased.setPurchaseTime(now);
            purchased.setPurchaseOrderId(paymentOrderId);
            purchased.setExpireTime(endTime);
            requireSingleWrite(userPurchasedMapper.insert(purchased), "订阅购买投影创建失败");
        } else {
            purchased.setPurchaseOrderId(paymentOrderId);
            purchased.setExpireTime(endTime);
            requireSingleWrite(userPurchasedMapper.updateById(purchased), "订阅购买投影更新失败");
        }

        log.info("event=playlist_subscription_completed subscribeOrderId={} paymentOrderId={}",
                subscribeOrderId, paymentOrderId);
        return true;
    }

    private void requireSingleWrite(int affectedRows, String message) {
        if (affectedRows != 1) {
            throw new BusinessException(message);
        }
    }

    private String normalizeSubscribeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            throw new BusinessException("订阅幂等键不能为空");
        }
        String normalized = idempotencyKey.trim();
        if (normalized.length() < 8 || normalized.length() > 128
                || !normalized.matches("[A-Za-z0-9._:-]+")) {
            throw new BusinessException("订阅幂等键格式不正确");
        }
        return normalized;
    }

    private PlaylistSubscribeOrder findSubscribeOrder(Long userId, String idempotencyKey) {
        return playlistSubscribeOrderMapper.selectOne(
                new LambdaQueryWrapper<PlaylistSubscribeOrder>()
                        .eq(PlaylistSubscribeOrder::getUserId, userId)
                        .eq(PlaylistSubscribeOrder::getIdempotencyKey, idempotencyKey)
                        .eq(PlaylistSubscribeOrder::getDeleted, 0)
                        .last("LIMIT 1"));
    }

    private Map<String, Object> replaySubscribeOrder(PlaylistSubscribeOrder order, Long playlistId) {
        if (!Objects.equals(order.getPlaylistId(), playlistId)) {
            throw new BusinessException("订阅幂等键已用于其他歌单");
        }
        if (order.getPaymentOrderId() == null) {
            throw new BusinessException("订阅订单正在创建，请稍后重试");
        }
        Map<String, Object> result = paymentOrderService.getOrderStatus(
                order.getPaymentOrderId(), order.getUserId());
        result.put("orderId", order.getPaymentOrderId());
        result.put("subscribeOrderId", order.getId());
        result.put("idempotentReplay", true);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean cancelSubscribe(Long userId, Long playlistId) {
        LambdaQueryWrapper<PlaylistSubscribe> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PlaylistSubscribe::getUserId, userId)
                .eq(PlaylistSubscribe::getPlaylistId, playlistId)
                .eq(PlaylistSubscribe::getStatus, "active")
                .gt(PlaylistSubscribe::getEndTime, LocalDateTime.now());

        PlaylistSubscribe subscribe = playlistSubscribeMapper.selectOne(wrapper);
        if (subscribe == null) {
            throw new BusinessException("订阅记录不存在或已过期");
        }

        subscribe.setAutoRenew(0);
        requireSingleWrite(playlistSubscribeMapper.updateById(subscribe), "订阅取消失败");

        log.info("event=playlist_subscription_renewal_cancelled userId={} playlistId={}",
                userId, playlistId);
        return true;
    }

    @Override
    public Map<String, Object> checkSubscribed(Long userId, Long playlistId) {
        if (!UserAccountStatusUtil.canInteract(userId, userMapper::selectById)) {
            Map<String, Object> result = new HashMap<>();
            result.put("subscribed", false);
            result.put("expiresAt", null);
            result.put("accountAvailable", false);
            return result;
        }

        LambdaQueryWrapper<PlaylistSubscribe> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PlaylistSubscribe::getUserId, userId)
                .eq(PlaylistSubscribe::getPlaylistId, playlistId)
                .eq(PlaylistSubscribe::getStatus, "active")
                .gt(PlaylistSubscribe::getEndTime, LocalDateTime.now());

        PlaylistSubscribe subscribe = playlistSubscribeMapper.selectOne(wrapper);

        Map<String, Object> result = new HashMap<>();
        if (subscribe != null) {
            result.put("subscribed", true);
            result.put("expiresAt", subscribe.getEndTime());
            result.put("subscribeType", subscribe.getSubscribeType());
            result.put("autoRenew", subscribe.getAutoRenew() == 1);
            result.put("accountAvailable", true);
        } else {
            result.put("subscribed", false);
            result.put("expiresAt", null);
            result.put("accountAvailable", true);
        }

        return result;
    }

    @Override
    public Map<String, Object> getMySubscribes(Long userId, String status,
                                               Integer page, Integer size) {
        Page<PlaylistSubscribe> pageParam = new Page<>(page, size);

        LambdaQueryWrapper<PlaylistSubscribe> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PlaylistSubscribe::getUserId, userId);

        if (ObjectUtil.isNotEmpty(status)) {
            wrapper.eq(PlaylistSubscribe::getStatus, status);
        } else {
            wrapper.eq(PlaylistSubscribe::getStatus, "active")
                    .gt(PlaylistSubscribe::getEndTime, LocalDateTime.now());
        }

        wrapper.orderByDesc(PlaylistSubscribe::getStartTime);

        Page<PlaylistSubscribe> resultPage = playlistSubscribeMapper.selectPage(pageParam, wrapper);

        if (resultPage.getRecords().isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("records", new ArrayList<>());
            result.put("total", 0L);
            result.put("current", page);
            result.put("size", size);
            return result;
        }

                    
        Set<Long> playlistIds = resultPage.getRecords().stream()
                .map(PlaylistSubscribe::getPlaylistId)
                .collect(Collectors.toSet());
        Set<Long> creatorIds = resultPage.getRecords().stream()
                .map(PlaylistSubscribe::getCreatorId)
                .collect(Collectors.toSet());

                 
        LambdaQueryWrapper<Playlist> playlistWrapper = new LambdaQueryWrapper<>();
        playlistWrapper.in(Playlist::getId, playlistIds);
        List<Playlist> playlists = playlistMapper.selectList(playlistWrapper);
        Map<Long, Playlist> playlistMap = playlists.stream()
                .collect(Collectors.toMap(Playlist::getId, p -> p));

                  
        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.in(User::getId, creatorIds)
                .select(User::getId, User::getNickname);
        List<User> creators = userMapper.selectList(userWrapper);
        Map<Long, User> creatorMap = creators.stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<Map<String, Object>> records = new ArrayList<>();
        for (PlaylistSubscribe subscribe : resultPage.getRecords()) {
            Map<String, Object> record = new HashMap<>();
            record.put("id", subscribe.getId());
            record.put("playlistId", subscribe.getPlaylistId());
            record.put("subscribeType", subscribe.getSubscribeType());
            record.put("price", subscribe.getAmount());
            record.put("startTime", subscribe.getStartTime());
            record.put("endTime", subscribe.getEndTime());
            record.put("autoRenew", subscribe.getAutoRenew() == 1);
            record.put("status", subscribe.getStatus());

            Playlist playlist = playlistMap.get(subscribe.getPlaylistId());
            if (playlist != null) {
                record.put("playlistTitle", playlist.getName());
                record.put("playlistCover", playlist.getCover());
                record.put("creatorId", subscribe.getCreatorId());

                User creator = creatorMap.get(subscribe.getCreatorId());
                if (creator != null) {
                    record.put("creatorName", creator.getNickname());
                }
            }

            records.add(record);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", resultPage.getTotal());
        result.put("current", page);
        result.put("size", size);

        return result;
    }

    @Override
    public Map<String, Object> getPlaylistSubscribers(Long creatorId, Long playlistId,
                                                      Integer page, Integer size) {
        creatorEligibilityService.requireEligible(creatorId, "查看订阅用户");
        Playlist playlist = playlistMapper.selectById(playlistId);
        if (ObjectUtil.isEmpty(playlist) || !playlist.getUserId().equals(creatorId)) {
            throw new BusinessException("无权限查看此歌单的订阅用户");
        }

        Page<PlaylistSubscribe> pageParam = new Page<>(page, size);

        LambdaQueryWrapper<PlaylistSubscribe> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PlaylistSubscribe::getPlaylistId, playlistId)
                .eq(PlaylistSubscribe::getStatus, "active")
                .orderByDesc(PlaylistSubscribe::getStartTime);

        Page<PlaylistSubscribe> resultPage = playlistSubscribeMapper.selectPage(pageParam, wrapper);

        if (resultPage.getRecords().isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("records", new ArrayList<>());
            result.put("total", 0L);
            result.put("current", page);
            result.put("size", size);
            return result;
        }

                      
        Set<Long> userIds = resultPage.getRecords().stream()
                .map(PlaylistSubscribe::getUserId)
                .collect(Collectors.toSet());

        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.in(User::getId, userIds)
                .select(User::getId, User::getNickname, User::getAvatar);

        List<User> users = userMapper.selectList(userWrapper);
        Map<Long, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<Map<String, Object>> records = new ArrayList<>();
        for (PlaylistSubscribe subscribe : resultPage.getRecords()) {
            Map<String, Object> record = new HashMap<>();
            record.put("userId", subscribe.getUserId());
            record.put("subscribeType", subscribe.getSubscribeType());
            record.put("startTime", subscribe.getStartTime());
            record.put("endTime", subscribe.getEndTime());
            record.put("autoRenew", subscribe.getAutoRenew() == 1);

            User user = userMap.get(subscribe.getUserId());
            if (user != null) {
                record.put("userName", user.getNickname());
                record.put("userAvatar", user.getAvatar());
            }

            records.add(record);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", resultPage.getTotal());
        result.put("current", page);
        result.put("size", size);

        return result;
    }

    @Override
    public Map<String, Object> getPlaylistSubscribeStats(Long playlistId) {
        Map<String, Object> statistics = playlistSubscribeMapper.selectSubscriptionStatistics(
                playlistId, LocalDateTime.now().minusDays(30));
        long totalSubscribers = numberValue(statistics, "totalSubscribers");
        long activeSubscribers = numberValue(statistics, "activeSubscribers");
        BigDecimal totalRevenue = decimalValue(statistics, "activeRevenue");
        long autoRenewCount = numberValue(statistics, "activeAutoRenewCount");
        double renewRate = activeSubscribers > 0 ? (double) autoRenewCount / activeSubscribers : 0;

        Map<String, Object> result = new HashMap<>();
        result.put("totalSubscribers", totalSubscribers);
        result.put("activeSubscribers", activeSubscribers);
        result.put("totalRevenue", totalRevenue);
        result.put("monthlyRevenue", totalRevenue);
        result.put("averageRevenue", activeSubscribers > 0 ? totalRevenue.divide(BigDecimal.valueOf(activeSubscribers), 2, BigDecimal.ROUND_HALF_UP) : BigDecimal.ZERO);
        result.put("renewRate", renewRate);

        return result;
    }

    private long numberValue(Map<String, Object> values, String key) {
        Object value = values == null ? null : values.get(key);
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private BigDecimal decimalValue(Map<String, Object> values, String key) {
        Object value = values == null ? null : values.get(key);
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        return BigDecimal.ZERO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer handleExpiredSubscribes() {
        LambdaQueryWrapper<PlaylistSubscribe> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PlaylistSubscribe::getStatus, "active")
                .lt(PlaylistSubscribe::getEndTime, LocalDateTime.now());

        List<PlaylistSubscribe> expiredSubscribes = playlistSubscribeMapper.selectList(wrapper);

        int count = 0;
        for (PlaylistSubscribe subscribe : expiredSubscribes) {
            if (subscribe.getAutoRenew() == 1) {
                try {
                    handleAutoRenew(subscribe.getId());
                    count++;
                } catch (Exception e) {
                    log.error("event=playlist_subscription_expiry_failed subscribeId={} errorType={}",
                            subscribe.getId(), e.getClass().getSimpleName());
                    subscribe.setStatus("expired");
                    playlistSubscribeMapper.updateById(subscribe);
                }
            } else {
                subscribe.setStatus("expired");
                playlistSubscribeMapper.updateById(subscribe);
                count++;
            }
        }

        log.info("event=playlist_subscription_expiry_completed processedCount={}", count);
        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> handleAutoRenew(Long subscribeId) {
        PlaylistSubscribe subscribe = playlistSubscribeMapper.selectById(subscribeId);
        if (subscribe == null) {
            throw new BusinessException("订阅记录不存在");
        }

        if (subscribe.getAutoRenew() != 1) {
            throw new BusinessException("未开启自动续费");
        }
                                            
        subscribe.setAutoRenew(0);
        if (subscribe.getEndTime() == null || !subscribe.getEndTime().isAfter(LocalDateTime.now())) {
            subscribe.setStatus("expired");
        }
        playlistSubscribeMapper.updateById(subscribe);
        log.info("event=playlist_subscription_auto_renew_disabled subscribeId={}", subscribeId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("paymentRequired", true);
        result.put("message", "请重新创建订阅支付订单");

        return result;
    }

    @Override
    public Map<String, Object> getSubscribeDetail(Long subscribeId) {
        PlaylistSubscribe subscribe = playlistSubscribeMapper.selectById(subscribeId);
        if (subscribe == null) {
            throw new BusinessException("订阅记录不存在");
        }

        Playlist playlist = playlistMapper.selectById(subscribe.getPlaylistId());
        User user = userMapper.selectById(subscribe.getUserId());

        Map<String, Object> result = new HashMap<>();
        result.put("id", subscribe.getId());
        result.put("playlistId", subscribe.getPlaylistId());
        result.put("playlistName", playlist != null ? playlist.getName() : "");
        result.put("userId", subscribe.getUserId());
        result.put("userName", user != null ? user.getNickname() : "");
        result.put("subscribeType", subscribe.getSubscribeType());
        result.put("amount", subscribe.getAmount());
        result.put("startTime", subscribe.getStartTime());
        result.put("endTime", subscribe.getEndTime());
        result.put("autoRenew", subscribe.getAutoRenew() == 1);
        result.put("status", subscribe.getStatus());

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> renewSubscribe(Long userId, Long playlistId, String subscribeType) {
        throw new BusinessException("请通过订阅支付接口续费");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean cancelAutoRenew(Long userId, Long playlistId) {
        LambdaQueryWrapper<PlaylistSubscribe> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PlaylistSubscribe::getUserId, userId)
                .eq(PlaylistSubscribe::getPlaylistId, playlistId)
                .eq(PlaylistSubscribe::getStatus, "active");

        PlaylistSubscribe subscribe = playlistSubscribeMapper.selectOne(wrapper);
        if (subscribe == null) {
            throw new BusinessException("订阅记录不存在");
        }

        subscribe.setAutoRenew(0);
        playlistSubscribeMapper.updateById(subscribe);

        log.info("event=playlist_subscription_auto_renew_cancelled userId={} playlistId={}",
                userId, playlistId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean enableAutoRenew(Long userId, Long playlistId) {
        UserAccountStatusUtil.requireCanInteract(userId, userMapper::selectById, "开启自动续费");
        throw new BusinessException("当前支付渠道不支持自动续费，请到期后手动续订");
    }

    @Override
    public Map<String, Object> getExpiringSubscribes(Integer days) {
        LocalDateTime expireThreshold = LocalDateTime.now().plusDays(days);

        LambdaQueryWrapper<PlaylistSubscribe> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PlaylistSubscribe::getStatus, "active")
                .gt(PlaylistSubscribe::getEndTime, LocalDateTime.now())
                .le(PlaylistSubscribe::getEndTime, expireThreshold);

        List<PlaylistSubscribe> subscribes = playlistSubscribeMapper.selectList(wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("records", subscribes);
        result.put("total", subscribes.size());

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean cancelPlaylistSubscribe(Long creatorId, Long playlistId) {
        return paidResourceService.cancelPaidResource(creatorId, "playlist", playlistId);
    }

    @Override
    public Map<String, Object> getMySubscribedPlaylists(Long userId, Integer page, Integer size) {
        if (!UserAccountStatusUtil.canInteract(userId, userMapper::selectById)) {
            Map<String, Object> result = new HashMap<>();
            result.put("records", new ArrayList<>());
            result.put("total", 0);
            result.put("current", page);
            result.put("size", size);
            result.put("accountAvailable", false);
            return result;
        }

        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null ? 20 : Math.max(1, Math.min(size, 100));
        Page<PlaylistSubscribe> pageParam = new Page<>(safePage, safeSize);

        LambdaQueryWrapper<PlaylistSubscribe> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PlaylistSubscribe::getUserId, userId)
                .eq(PlaylistSubscribe::getStatus, "active")
                .gt(PlaylistSubscribe::getEndTime, LocalDateTime.now())
                .orderByDesc(PlaylistSubscribe::getStartTime);

        Page<PlaylistSubscribe> resultPage = playlistSubscribeMapper.selectPage(pageParam, wrapper);

        if (resultPage.getRecords().isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("records", new ArrayList<>());
            result.put("total", 0);
            result.put("current", safePage);
            result.put("size", safeSize);
            return result;
        }

                    
        Set<Long> playlistIds = resultPage.getRecords().stream()
                .map(PlaylistSubscribe::getPlaylistId)
                .collect(Collectors.toSet());
        Set<Long> creatorIds = resultPage.getRecords().stream()
                .map(PlaylistSubscribe::getCreatorId)
                .collect(Collectors.toSet());

                 
        LambdaQueryWrapper<Playlist> playlistWrapper = new LambdaQueryWrapper<>();
        playlistWrapper.in(Playlist::getId, playlistIds);
        List<Playlist> playlists = playlistMapper.selectList(playlistWrapper);
        Map<Long, Playlist> playlistMap = playlists.stream()
                .collect(Collectors.toMap(Playlist::getId, p -> p));

                  
        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.in(User::getId, creatorIds)
                .select(User::getId, User::getNickname, User::getAvatar);
        List<User> creators = userMapper.selectList(userWrapper);
        Map<Long, User> creatorMap = creators.stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<Map<String, Object>> records = new ArrayList<>();
        for (PlaylistSubscribe subscribe : resultPage.getRecords()) {
            Playlist playlist = playlistMap.get(subscribe.getPlaylistId());
            if (playlist == null) {
                continue;
            }

            Map<String, Object> record = new HashMap<>();
            record.put("id", playlist.getId());
            record.put("name", playlist.getName());
            record.put("cover", playlist.getCover());
            record.put("description", playlist.getDescription());
            record.put("songCount", playlist.getSongCount());
            record.put("playCount", playlist.getPlayCount());
            record.put("subscribeType", subscribe.getSubscribeType());
            record.put("endTime", subscribe.getEndTime());
            record.put("autoRenew", subscribe.getAutoRenew() == 1);

            User creator = creatorMap.get(playlist.getUserId());
            if (creator != null) {
                record.put("creatorName", creator.getNickname());
                record.put("creatorAvatar", creator.getAvatar());
            }

            records.add(record);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", records.size());
        result.put("current", page);
        result.put("size", size);

        return result;
    }

    private int getDaysByType(String subscribeType) {
        switch (subscribeType) {
            case "month":
                return 30;
            case "quarter":
                return 90;
            case "year":
                return 365;
            default:
                return 30;
        }
    }

    private BigDecimal getAmountByType(String subscribeType) {
        switch (subscribeType) {
            case "month":
                return new BigDecimal("10");
            case "quarter":
                return new BigDecimal("30");
            case "year":
                return new BigDecimal("120");
            default:
                return new BigDecimal("10");
        }
    }

    private String getTypeByDays(int days) {
        if (days <= 30) {
            return "month";
        }
        if (days <= 90) {
            return "quarter";
        }
        return "year";
    }

    private boolean canAutoRenew(PlaylistSubscribe subscribe) {
        return UserAccountStatusUtil.canInteract(subscribe.getUserId(), userMapper::selectById)
                && UserAccountStatusUtil.canInteract(subscribe.getCreatorId(), userMapper::selectById);
    }
}
