package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.User;
import com.haoran.music.entity.UserActivityPoints;
import com.haoran.music.entity.VipExchangeOrder;
import com.haoran.music.entity.VipExchangePackage;
import com.haoran.music.mapper.UserActivityPointsMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.mapper.VipExchangeOrderMapper;
import com.haoran.music.mapper.VipExchangePackageMapper;
import com.haoran.music.service.UserActivityPointsService;
import com.haoran.music.service.UserVipService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

   
                      
                                                        
   
@Slf4j
@Service
public class UserActivityPointsServiceImpl extends ServiceImpl<UserActivityPointsMapper, UserActivityPoints>
        implements UserActivityPointsService {

    private static final Pattern REQUEST_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{16,64}$");

    @Resource
    private UserMapper userMapper;

    @Resource
    private UserVipService userVipService;

    @Resource
    private VipExchangePackageMapper vipExchangePackageMapper;

    @Resource
    private VipExchangeOrderMapper vipExchangeOrderMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer addPoints(Long userId, Integer points, String type, String description) {
        return addPoints(userId, points, type, description, null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer addPoints(Long userId, Integer points, String type, String description,
                             Long relatedId, String relatedType) {
        User user = userMapper.selectByIdForUpdate(userId);
        if (ObjectUtils.isEmpty(user)) {
            throw new BusinessException("用户不存在");
        }
        UserAccountStatusUtil.requireCanInteract(user, "变更活跃值");

        int delta = points == null ? 0 : points;
        Integer currentBalance = getUserTotalPoints(userId);
        int newBalance = currentBalance + delta;
        if (newBalance < 0) {
            throw new BusinessException("活跃值不足");
        }

        UserActivityPoints record = new UserActivityPoints();
        record.setUserId(userId);
        record.setPoints(delta);
        record.setBeforePoints(currentBalance);
        record.setBalance(newBalance);
        record.setType(type);
        record.setDescription(description);
        record.setCreateTime(LocalDateTime.now());
        record.setRelatedId(relatedId);
        record.setRelatedType(relatedType);
        save(record);

        log.info("event=user_activity_points_changed userId={} changeType={}", userId, type);

        return newBalance;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer consumePoints(Long userId, Integer points, String type, String description) {
        Integer currentBalance = getUserTotalPoints(userId);
        int cost = Math.abs(points == null ? 0 : points);
        if (currentBalance < cost) {
            throw new BusinessException("活跃值不足");
        }
        return addPoints(userId, -cost, type, description);
    }

    @Override
    public Integer getUserTotalPoints(Long userId) {
        Integer total = baseMapper.getUserTotalPoints(userId);
        return total == null ? 0 : total;
    }

    @Override
    public Integer getTodayPoints(Long userId) {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        Integer total = baseMapper.getTodayPoints(userId, start, start.plusDays(1));
        return total == null ? 0 : total;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void expirePoints(Integer days) {
        log.info("event=user_activity_points_expiration_skipped reason=non_expiring_policy");
    }

    @Override
    public List<Map<String, Object>> getVipExchangePackages() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (VipExchangePackage vipPackage : vipExchangePackageMapper.selectAllEnabled()) {
            validatePackage(vipPackage);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("packageCode", vipPackage.getPackageCode());
            item.put("packageName", vipPackage.getPackageName());
            item.put("vipDays", vipPackage.getVipDays());
            item.put("costPoints", vipPackage.getCostPoints());
            item.put("ruleVersion", vipPackage.getRuleVersion());
            result.add(item);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> redeemVip(Long userId, String packageCode, String requestId) {
        if (packageCode == null || packageCode.trim().isEmpty()) {
            throw new BusinessException("VIP兑换套餐不能为空");
        }
        if (requestId == null || !REQUEST_ID_PATTERN.matcher(requestId).matches()) {
            throw new BusinessException("兑换请求标识无效");
        }

        User user = userMapper.selectByIdForUpdate(userId);
        if (ObjectUtils.isEmpty(user)) {
            throw new BusinessException("用户不存在");
        }
        UserAccountStatusUtil.requireCanInteract(user, "兑换VIP");

        VipExchangeOrder existing = vipExchangeOrderMapper.selectByUserAndRequestId(userId, requestId);
        if (existing != null) {
            if (!packageCode.equals(existing.getPackageCode())) {
                throw new BusinessException("兑换请求标识已用于其他套餐");
            }
            if (!"granted".equals(existing.getStatus())) {
                throw new BusinessException("兑换正在处理中，请稍后查询");
            }
            return toExchangeResult(existing, true);
        }

        VipExchangePackage vipPackage = vipExchangePackageMapper.selectEnabledByCode(packageCode);
        validatePackage(vipPackage);

        int beforePoints = getUserTotalPoints(userId);
        if (beforePoints < vipPackage.getCostPoints()) {
            throw new BusinessException("活跃值不足");
        }
        int afterPoints = beforePoints - vipPackage.getCostPoints();

        VipExchangeOrder order = new VipExchangeOrder();
        order.setRequestId(requestId);
        order.setUserId(userId);
        order.setPackageId(vipPackage.getId());
        order.setPackageCode(vipPackage.getPackageCode());
        order.setRuleVersion(vipPackage.getRuleVersion());
        order.setVipLevel(vipPackage.getVipLevel());
        order.setVipDays(vipPackage.getVipDays());
        order.setCostPoints(vipPackage.getCostPoints());
        order.setBeforePoints(beforePoints);
        order.setAfterPoints(afterPoints);
        order.setStatus("pending");
        if (vipExchangeOrderMapper.insert(order) != 1) {
            throw new IllegalStateException("VIP兑换订单创建失败");
        }

        UserActivityPoints record = new UserActivityPoints();
        record.setUserId(userId);
        record.setPoints(-vipPackage.getCostPoints());
        record.setBeforePoints(beforePoints);
        record.setBalance(afterPoints);
        record.setType("redeem");
        record.setDescription("兑换" + vipPackage.getPackageName());
        record.setRelatedId(order.getId());
        record.setCreateTime(LocalDateTime.now());
        if (baseMapper.insert(record) != 1) {
            throw new IllegalStateException("VIP兑换活跃值流水创建失败");
        }

        userVipService.grantVip(userId, vipPackage.getVipLevel(), vipPackage.getVipDays(), "redeem");
        if (vipExchangeOrderMapper.markGranted(order.getId(), userId, beforePoints, afterPoints) != 1) {
            throw new IllegalStateException("VIP兑换订单终态更新失败");
        }
        order.setStatus("granted");

        log.info("event=user_activity_points_vip_exchange_succeeded userId={} packageCode={}",
                userId, packageCode);
        return toExchangeResult(order, false);
    }

    private void validatePackage(VipExchangePackage vipPackage) {
        if (vipPackage == null || !Integer.valueOf(1).equals(vipPackage.getStatus())
                || vipPackage.getId() == null || vipPackage.getPackageCode() == null
                || vipPackage.getVipLevel() == null || vipPackage.getVipLevel() < 1
                || vipPackage.getVipDays() == null || vipPackage.getVipDays() <= 0
                || vipPackage.getCostPoints() == null || vipPackage.getCostPoints() <= 0
                || vipPackage.getRuleVersion() == null || vipPackage.getRuleVersion() <= 0) {
            throw new BusinessException("VIP兑换套餐不可用");
        }
    }

    private Map<String, Object> toExchangeResult(VipExchangeOrder order, boolean replayed) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", order.getId());
        result.put("requestId", order.getRequestId());
        result.put("packageCode", order.getPackageCode());
        result.put("ruleVersion", order.getRuleVersion());
        result.put("vipLevel", order.getVipLevel());
        result.put("vipDays", order.getVipDays());
        result.put("costPoints", order.getCostPoints());
        result.put("beforePoints", order.getBeforePoints());
        result.put("afterPoints", order.getAfterPoints());
        result.put("status", order.getStatus());
        result.put("replayed", replayed);
        return result;
    }
}
