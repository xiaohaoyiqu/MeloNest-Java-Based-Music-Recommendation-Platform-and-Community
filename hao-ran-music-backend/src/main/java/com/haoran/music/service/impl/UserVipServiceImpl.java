package com.haoran.music.service.impl;
import javax.annotation.Resource;
import com.haoran.music.common.util.RedisUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.enums.VipLevel;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.dto.user.VipPurchaseDTO;
import com.haoran.music.entity.User;
import com.haoran.music.entity.UserVip;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.mapper.UserVipMapper;
import com.haoran.music.service.UserVipService;
import com.haoran.music.vo.user.UserVipVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;





@Slf4j
@Service
public class UserVipServiceImpl extends ServiceImpl<UserVipMapper, UserVip> implements UserVipService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserVipMapper userVipMapper;

    @Resource
    private RedisUtils redisUtils;
    @Override
    public UserVipVO getUserVipInfo(Long userId) {
        UserVipVO vo = new UserVipVO();
        vo.setUserId(String.valueOf(userId));
        boolean accountCanUseVip = UserAccountStatusUtil.canInteract(userId, userMapper::selectById);


        UserVip vip = userVipMapper.selectActiveVip(userId, LocalDateTime.now());

        if (ObjectUtils.isNotEmpty(vip)) {
            VipLevel level = VipLevel.fromCode(vip.getVipLevel());
            vo.setVipLevel(String.valueOf(vip.getVipLevel()));
            vo.setVipLevelName(level.getName());
            vo.setVipStatus(String.valueOf(vip.getVipStatus()));
            vo.setVipStatusName(accountCanUseVip
                    ? (vip.getVipStatus() == 1 ? "生效中" : "已过期")
                    : "账号异常，VIP权益暂停");
            vo.setVipStartTime(vip.getVipStartTime());
            vo.setVipExpireTime(vip.getVipExpireTime());


            if (ObjectUtils.isNotEmpty(vip.getVipExpireTime())) {
                long days = ChronoUnit.DAYS.between(LocalDateTime.now(), vip.getVipExpireTime());
                vo.setRemainingDays(String.valueOf((int) Math.max(0, days)));
            }

            vo.setVip(String.valueOf(accountCanUseVip));
            vo.setLifetimeVip(String.valueOf(false));              
            vo.setAutoRenew(String.valueOf(vip.getAutoRenew() == 1));
            vo.setTotalVipDays(String.valueOf(vip.getTotalVipDays()));
            vo.setTotalSpending(String.valueOf(vip.getTotalSpending() != null ? vip.getTotalSpending() / 100.0 : 0.0));
        } else {
            vo.setVipLevel(String.valueOf(VipLevel.FREE.getCode()));
            vo.setVipLevelName(VipLevel.FREE.getName());
            vo.setVipStatus("0");
            vo.setVipStatusName("免费用户");
            vo.setVip(String.valueOf(false));
            vo.setLifetimeVip(String.valueOf(false));
            vo.setAutoRenew(String.valueOf(false));
        }

        VipLevel privilegeLevel = Boolean.parseBoolean(vo.getVip())
                ? VipLevel.fromCode(Integer.parseInt(vo.getVipLevel()))
                : VipLevel.FREE;
        vo.setPrivileges(getVipPrivileges(privilegeLevel));

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVipVO purchaseVip(Long userId, VipPurchaseDTO dto) {
        throw new BusinessException("VIP支付服务尚未接入，不能直接购买并授予权益");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVipVO renewVip(Long userId) {
        throw new BusinessException("VIP续费支付服务尚未接入，不能直接延长权益");
    }

    @Override
    public void setAutoRenew(Long userId, Boolean autoRenew, Integer cycle) {
        if (!Boolean.FALSE.equals(autoRenew)) {
            throw new BusinessException("自动续费支付服务尚未接入，暂不能开启");
        }

        UserVip vip = userVipMapper.selectActiveVip(userId, LocalDateTime.now());
        if (ObjectUtils.isEmpty(vip)) {
            throw new RuntimeException("无有效的VIP记录");
        }

        vip.setAutoRenew(0);
        if (userVipMapper.updateById(vip) != 1) {
            throw new BusinessException("关闭自动续费失败，请稍后重试");
        }

        log.info("event=vip_auto_renew_disabled userId={}", userId);
    }

    @Override
    public Boolean isVip(Long userId) {
        if (!UserAccountStatusUtil.canInteract(userId, userMapper::selectById)) {
            return false;
        }
        UserVip vip = userVipMapper.selectActiveVip(userId, LocalDateTime.now());
        return ObjectUtils.isNotEmpty(vip);
    }

    @Override
    public Map<Long, LocalDateTime> getActiveVipExpirations(Collection<Long> userIds) {
        List<UserVip> activeVips = selectActiveVips(userIds);
        if (activeVips.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, LocalDateTime> result = new HashMap<>();
        for (UserVip vip : activeVips) {
            result.merge(vip.getUserId(), vip.getVipExpireTime(),
                    (left, right) -> left.isAfter(right) ? left : right);
        }
        return result;
    }

    @Override
    public Map<Long, VipLevel> getActiveVipLevels(Collection<Long> userIds) {
        List<UserVip> activeVips = selectActiveVips(userIds);
        if (activeVips.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, VipLevel> result = new HashMap<>();
        for (UserVip vip : activeVips) {
            result.merge(vip.getUserId(), VipLevel.fromCode(vip.getVipLevel()),
                    (left, right) -> left.getCode() >= right.getCode() ? left : right);
        }
        return result;
    }

    private List<UserVip> selectActiveVips(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> distinctUserIds = userIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .limit(500)
                .collect(Collectors.toList());
        if (distinctUserIds.isEmpty()) {
            return Collections.emptyList();
        }
        HashSet<Long> interactableIds = userMapper.selectBatchIds(distinctUserIds).stream()
                .filter(UserAccountStatusUtil::canInteract)
                .map(User::getId)
                .collect(Collectors.toCollection(HashSet::new));
        if (interactableIds.isEmpty()) {
            return Collections.emptyList();
        }
        LocalDateTime now = LocalDateTime.now();
        return userVipMapper.selectList(new LambdaQueryWrapper<UserVip>()
                .in(UserVip::getUserId, interactableIds)
                .eq(UserVip::getVipStatus, 1)
                .gt(UserVip::getVipExpireTime, now));
    }

    @Override
    public Boolean isVipOrAbove(Long userId, VipLevel vipLevel) {
        if (!UserAccountStatusUtil.canInteract(userId, userMapper::selectById)) {
            return false;
        }
        UserVip vip = userVipMapper.selectActiveVip(userId, LocalDateTime.now());
        if (ObjectUtils.isEmpty(vip)) {
            return false;
        }
        return vip.getVipLevel() >= vipLevel.getCode();
    }

    @Override
    public VipLevel getVipLevel(Long userId) {
        if (!UserAccountStatusUtil.canInteract(userId, userMapper::selectById)) {
            return VipLevel.FREE;
        }
        UserVip vip = userVipMapper.selectActiveVip(userId, LocalDateTime.now());
        if (ObjectUtils.isEmpty(vip)) {
            return VipLevel.FREE;
        }
        return VipLevel.fromCode(vip.getVipLevel());
    }

    @Override
    public void updateVipExpireTime(Long userId, LocalDateTime expireTime) {
        UserVip vip = userVipMapper.selectActiveVip(userId, LocalDateTime.now());
        if (ObjectUtils.isNotEmpty(vip)) {
            vip.setVipExpireTime(expireTime);
            userVipMapper.updateById(vip);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processExpiredVips() {
        log.info("event=vip_expiration_processing_started");

        int expiredCount = userVipMapper.markExpiredVips(LocalDateTime.now());

        log.info("event=vip_expiration_processing_completed processedCount={}", expiredCount);
    }

    @Override
    public void sendExpiringVipReminders() {
        log.info("event=vip_expiration_reminder_started");

        LocalDateTime threeDaysLater = LocalDateTime.now().plusDays(3);
        List<UserVip> expiringVips = userVipMapper.selectExpiringVips(LocalDateTime.now(), threeDaysLater);

        for (UserVip vip : expiringVips) {

            log.info("event=vip_expiration_reminder_queued userId={}", vip.getUserId());
        }

        log.info("event=vip_expiration_reminder_completed queuedCount={}", expiringVips.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processAutoRenew() {
        log.warn("event=vip_auto_renew_task_skipped reason=payment_contract_unavailable");
    }

    @Override
    public void recordPrivilegeUsage(Long userId, String privilege) {
        if (userId == null || privilege == null || privilege.isEmpty()) {
            return;
        }
        if (!UserAccountStatusUtil.canInteract(userId, userMapper::selectById)) {
            return;
        }

        try {

            String key = "vip:privilege:usage:" + userId;
            String field = privilege;


            redisUtils.hIncrBy(key, field, 1);

            redisUtils.expire(key, 30, java.util.concurrent.TimeUnit.DAYS);

            log.debug("event=vip_privilege_usage_recorded userId={}", userId);

        } catch (Exception e) {
            log.error("event=vip_privilege_usage_record_failed userId={} errorType={}",
                    userId, e.getClass().getSimpleName());
        }
    }

    @Override
    public List<String> getVipPrivileges(VipLevel vipLevel) {
        if (vipLevel == VipLevel.FREE) {
            return Arrays.asList("标准音质", "每日听歌不限时");
        }

        List<String> privileges = new ArrayList<>();
        privileges.addAll(Arrays.asList(
                "高音质播放",
                "无损音质下载",
                "专属歌单",
                "无广告体验"
        ));

        if (vipLevel == VipLevel.YEARLY_VIP) {
            privileges.add("优先客服");
            privileges.add("专属活动");
        }

        return privileges;
    }








    @Override
    @Transactional(rollbackFor = Exception.class)
    public void grantVip(Long userId, int vipLevel, int days, String source) {
        UserAccountStatusUtil.requireCanInteract(userId, userMapper::selectById, "获得VIP奖励");
        if (VipLevel.fromCode(vipLevel) == VipLevel.FREE || days <= 0) {
            throw new BusinessException("VIP等级或有效天数不合法");
        }
        String normalizedSource = normalizeVipSource(source);
        log.info("event=vip_entitlement_granted userId={}", userId);

        VipLevel level = VipLevel.fromCode(vipLevel);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireTime;


        UserVip existingVip = userVipMapper.selectLatestVipForUpdate(userId);

        if (existingVip != null) {
            boolean currentlyActive = Integer.valueOf(1).equals(existingVip.getVipStatus())
                    && existingVip.getVipExpireTime() != null
                    && existingVip.getVipExpireTime().isAfter(now);
            LocalDateTime extensionBase = currentlyActive ? existingVip.getVipExpireTime() : now;
            expireTime = extensionBase.plusDays(days);
            int effectiveLevel = currentlyActive
                    ? Math.max(VipLevel.fromCode(existingVip.getVipLevel()).getCode(), level.getCode())
                    : level.getCode();
            existingVip.setVipLevel(effectiveLevel);
            existingVip.setVipStatus(1);
            if (!currentlyActive || existingVip.getVipStartTime() == null) {
                existingVip.setVipStartTime(now);
            }
            existingVip.setVipExpireTime(expireTime);
            existingVip.setTotalVipDays((existingVip.getTotalVipDays() == null ? 0 : existingVip.getTotalVipDays()) + days);

            if ("payment".equals(existingVip.getSource()) || "payment".equals(normalizedSource)) {
                existingVip.setSource("payment");
            } else {
                existingVip.setSource(normalizedSource);
            }
            if (userVipMapper.updateById(existingVip) != 1) {
                throw new BusinessException("VIP权益更新失败");
            }
        } else {
            expireTime = now.plusDays(days);

            UserVip vip = new UserVip();
            vip.setUserId(userId);
            vip.setVipLevel(vipLevel);
            vip.setVipStatus(1);
            vip.setVipStartTime(now);
            vip.setVipExpireTime(expireTime);
            vip.setTotalVipDays(days);
            vip.setAutoRenew(0);
            vip.setSource(normalizedSource);
            if (userVipMapper.insert(vip) != 1) {
                throw new BusinessException("VIP权益创建失败");
            }
        }


        if (expireTime != null) {
            try {
                String key = "vip:source:" + userId;
                long ttl = ChronoUnit.SECONDS.between(now, expireTime);
                if (ttl > 0) {
                    redisUtils.set(key, normalizedSource, ttl, java.util.concurrent.TimeUnit.SECONDS);
                    log.info("event=vip_source_recorded userId={}", userId);
                }
            } catch (Exception e) {
                log.error("event=vip_source_record_failed userId={}", userId);
            }
        }
    }

    private String normalizeVipSource(String source) {
        if (source == null) {
            return "unknown";
        }
        String normalized = source.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "payment":
            case "redeem":
            case "sign":
            case "achievement":
            case "gift":
            case "reward":
            case "admin":
            case "renew":
                return normalized;
            default:
                return "unknown";
        }
    }
}
