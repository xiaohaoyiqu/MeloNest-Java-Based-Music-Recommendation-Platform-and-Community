   
                      
                       
   

package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.haoran.music.common.config.CreatorConfig;
import com.haoran.music.common.config.VipConfig;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.*;
import com.haoran.music.mapper.*;
import com.haoran.music.service.VipService;
import com.haoran.music.service.UserVipService;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

   
          
   
@Slf4j
@Service
public class VipServiceImpl implements VipService {

    @javax.annotation.Resource
    private com.haoran.music.service.CreatorEligibilityService creatorEligibilityService;

    private final VipOrderMapper vipOrderMapper;
    private final com.haoran.music.common.util.RedisUtils redisUtils;
    private final CreatorVipApplyMapper creatorVipApplyMapper;
    private final UserMapper userMapper;
                                               
    private final VipConfig vipConfig;
    private final CreatorConfig creatorConfig;
    private final VipPurchaseRecordMapper vipPurchaseRecordMapper;
    private final PaidResourceMapper paidResourceMapper;
    private final UserVipMapper userVipMapper;
    private final UserVipService userVipService;

    public VipServiceImpl(VipOrderMapper vipOrderMapper,
                         CreatorVipApplyMapper creatorVipApplyMapper,
                         UserMapper userMapper,
                         VipConfig vipConfig,
                          CreatorConfig creatorConfig,
                          VipPurchaseRecordMapper vipPurchaseRecordMapper,
                          PaidResourceMapper paidResourceMapper,
                          UserVipMapper userVipMapper,
                          @Lazy UserVipService userVipService,
                          com.haoran.music.common.util.RedisUtils redisUtils) {
        this.vipOrderMapper = vipOrderMapper;
        this.creatorVipApplyMapper = creatorVipApplyMapper;
        this.userMapper = userMapper;
        this.vipConfig = vipConfig;
        this.creatorConfig = creatorConfig;
        this.vipPurchaseRecordMapper = vipPurchaseRecordMapper;
        this.paidResourceMapper = paidResourceMapper;
        this.userVipMapper = userVipMapper;
        this.userVipService = userVipService;
        this.redisUtils = redisUtils;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> rechargeVip(Long userId, String vipType) {
               
        User user = userMapper.selectById(userId);
        if (ObjectUtils.isEmpty(user)) {
            throw new BusinessException("用户不存在");
        }
        UserAccountStatusUtil.requireCanInteract(user, "充值VIP");

                 
        VipConfig.VipPrice price = vipConfig.getPrice(vipType);
        if (price == null) {
            throw new BusinessException("不支持的VIP类型");
        }

                
        String orderNo = "VIP" + System.currentTimeMillis() + (int)(Math.random() * 10000);

                  
        VipOrder order = new VipOrder();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setVipType(vipType);
        order.setDays(price.getDays());
        order.setAmount(new BigDecimal(price.getPrice()));
        order.setStatus("pending");

        vipOrderMapper.insert(order);

        log.info("event=vip_purchase_created userId={}", userId);

        Map<String, Object> result = new HashMap<>();
        result.put("orderId", order.getId());
        result.put("orderNo", orderNo);
        result.put("vipType", vipType);
        result.put("days", price.getDays());
        result.put("amount", price.getPrice());
        result.put("message", "VIP充值订单已创建");

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> creatorApplyVip(Long creatorId, Integer applyDays, String reason) {
        creatorEligibilityService.requireEligible(creatorId, "申请创作者VIP");

                 
        Map<String, Object> condition = checkCreatorVipCondition(creatorId);
        if (!(Boolean) condition.get("eligible")) {
            throw new BusinessException((String) condition.get("reason"));
        }

                 
        LambdaQueryWrapper<CreatorVipApply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CreatorVipApply::getCreatorId, creatorId)
                .orderByDesc(CreatorVipApply::getLastApplyTime)
                .last("LIMIT 1");

        CreatorVipApply lastApply = creatorVipApplyMapper.selectOne(wrapper);

        if (lastApply != null && lastApply.getLastApplyTime() != null) {
            if (!creatorConfig.checkVipApplyInterval(lastApply.getLastApplyTime())) {
                throw new BusinessException("申请间隔未满（" + creatorConfig.getVipApplyIntervalDays() + "天）");
            }
        }

               
        CreatorVipApply apply = new CreatorVipApply();
        apply.setCreatorId(creatorId);
        apply.setApplyDays(applyDays != null ? applyDays : creatorConfig.getVipApplyDays());
        apply.setReason(reason);
        apply.setStatus("pending");
        apply.setLastApplyTime(LocalDateTime.now());

        creatorVipApplyMapper.insert(apply);

        log.info("event=creator_vip_application_created creatorId={} applyId={}", creatorId, apply.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("applyId", apply.getId());
        result.put("status", "pending");
        result.put("message", "VIP申请已提交，等待审核");

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reviewCreatorVipApply(Long applyId, Long reviewerId,
                                                    Boolean approved, String reviewReason) {
        if (applyId == null || reviewerId == null || approved == null) {
            throw new BusinessException("VIP申请审核参数不能为空");
        }
        if (reviewReason != null && reviewReason.length() > 1000) {
            throw new BusinessException("VIP申请审核说明不能超过1000字");
        }
        CreatorVipApply apply = creatorVipApplyMapper.selectById(applyId);
        if (apply == null) {
            throw new BusinessException("申请不存在");
        }

        if (!"pending".equals(apply.getStatus())) {
            throw new BusinessException("申请已处理");
        }

        if (approved) {
            creatorEligibilityService.requireEligible(apply.getCreatorId(), "批准创作者VIP申请");
            if (apply.getApplyDays() == null || apply.getApplyDays() <= 0 || apply.getApplyDays() > 365) {
                throw new BusinessException("VIP申请天数不合法");
            }
        }

        String nextStatus = approved ? "approved" : "rejected";
        LocalDateTime reviewTime = LocalDateTime.now();
        int updated = creatorVipApplyMapper.update(null, new UpdateWrapper<CreatorVipApply>()
                .eq("id", applyId)
                .eq("status", "pending")
                .set("status", nextStatus)
                .set("reviewer_id", reviewerId)
                .set("review_time", reviewTime)
                .set("review_reason", reviewReason));
        if (updated != 1) {
            throw new BusinessException("VIP申请已被其他审核人处理");
        }

        Map<String, Object> result = new HashMap<>();

        if (approved) {
            User creator = userMapper.selectById(apply.getCreatorId());
            if (!UserAccountStatusUtil.canInteract(creator)) {
                throw new BusinessException(UserAccountStatusUtil.targetUnavailableMessage(creator) + "，无法通过VIP申请");
            }

                    
            if (!grantVip(apply.getCreatorId(), apply.getApplyDays(),
                    "创作者VIP申请通过", reviewerId)) {
                throw new BusinessException("VIP权益授予失败");
            }

            result.put("status", "approved");
            result.put("message", "VIP申请已批准");

            log.info("event=creator_vip_application_reviewed applyId={} approved=true", applyId);

        } else {
            result.put("status", "rejected");
            result.put("message", "VIP申请已拒绝");
            result.put("reason", reviewReason);

            log.info("event=creator_vip_application_reviewed applyId={} approved=false", applyId);
        }

        return result;
    }

    @Override
    public Map<String, Object> getVipPriceConfig() {
        Map<String, Object> result = new HashMap<>();
        result.put("prices", vipConfig.getPrices());
        return result;
    }

    @Override
    public Map<String, Object> checkVipStatus(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        Map<String, Object> result = new HashMap<>();
        boolean accountCanUseVip = UserAccountStatusUtil.canInteract(user);
        UserVip vip = accountCanUseVip ? userVipMapper.selectActiveVip(userId, LocalDateTime.now()) : null;
        result.put("isVip", vip != null);
        result.put("vipExpireTime", vip == null ? null : vip.getVipExpireTime());

        if (vip != null && vip.getVipExpireTime() != null) {
            long remainingDays = accountCanUseVip
                    ? java.time.Duration.between(LocalDateTime.now(), vip.getVipExpireTime()).toDays()
                    : 0;
            result.put("remainingDays", remainingDays);
        }

        return result;
    }

    @Override
    public Long getVipRemainingDays(Long userId) {
        User user = userMapper.selectById(userId);
        if (!UserAccountStatusUtil.canInteract(user)) {
            return 0L;
        }
        UserVip vip = userVipMapper.selectActiveVip(userId, LocalDateTime.now());
        if (vip == null || vip.getVipExpireTime() == null) {
            return 0L;
        }
        return Math.max(0L, java.time.Duration.between(LocalDateTime.now(), vip.getVipExpireTime()).toDays());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer handleExpiredVips() {
        return userVipMapper.markExpiredVips(LocalDateTime.now());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean grantVip(Long userId, Integer days, String reason, Long operatorId) {
        User user = userMapper.selectById(userId);
        if (!UserAccountStatusUtil.canInteract(user) || days == null || days <= 0) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        UserVip existingVip = userVipMapper.selectActiveVip(userId, now);
        LocalDateTime startTime = existingVip != null && existingVip.getVipExpireTime() != null
                ? existingVip.getVipExpireTime() : now;
        userVipService.grantVip(userId, 1, days, operatorId == null ? "reward" : "admin");
        UserVip currentVip = userVipMapper.selectActiveVip(userId, now);
        if (currentVip == null || currentVip.getVipExpireTime() == null) {
            throw new BusinessException("VIP权益写入失败");
        }
        LocalDateTime newExpireTime = currentVip.getVipExpireTime();

                    
        createVipPurchaseRecord(userId, days, startTime, newExpireTime, reason, operatorId);

        log.info("event=vip_entitlement_granted userId={}", userId);

        return true;
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean grantVipWithSource(Long userId, Integer days, String reason,
                                      Long operatorId, String sourceType) {
        User user = userMapper.selectById(userId);
        if (!UserAccountStatusUtil.canInteract(user) || days == null || days <= 0
                || sourceType == null || sourceType.trim().isEmpty()) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        UserVip existingVip = userVipMapper.selectActiveVip(userId, now);
        LocalDateTime startTime = existingVip != null && existingVip.getVipExpireTime() != null
                ? existingVip.getVipExpireTime() : now;
        userVipService.grantVip(userId, 1, days, sourceType.trim().toLowerCase());
        UserVip currentVip = userVipMapper.selectActiveVip(userId, now);
        if (currentVip == null || currentVip.getVipExpireTime() == null) {
            throw new BusinessException("VIP权益写入失败");
        }
        LocalDateTime newExpireTime = currentVip.getVipExpireTime();
        createVipPurchaseRecord(userId, days, startTime, newExpireTime, reason, operatorId);
        recordVipSource(userId, sourceType, newExpireTime);
        log.info("event=vip_entitlement_granted_with_source userId={}", userId);
        return true;
    }

    @Override
    public Map<String, Object> getVipPrivileges() {
        Map<String, Object> result = new HashMap<>();
        result.put("privileges", vipConfig.getPrivileges());
        return result;
    }

    @Override
    public Map<String, Object> checkCreatorVipCondition(Long creatorId) {
        if (!creatorEligibilityService.isEligible(creatorId)) {
            Map<String, Object> denied = new HashMap<>();
            denied.put("eligible", false);
            denied.put("reason", "当前账号不具备有效创作者资格");
            return denied;
        }
        Map<String, Object> result = new HashMap<>();
        User creator = userMapper.selectById(creatorId);
        if (!UserAccountStatusUtil.canInteract(creator)) {
            result.put("eligible", false);
            result.put("reason", UserAccountStatusUtil.targetUnavailableMessage(creator) + "，无法申请VIP");
            return result;
        }
        result.put("eligible", true);

                 
        LambdaQueryWrapper<PaidResource> resourceWrapper = new LambdaQueryWrapper<>();
        resourceWrapper.eq(PaidResource::getOwnerId, creatorId)
                .eq(PaidResource::getStatus, "active")
                .eq(PaidResource::getIsEnabled, 1);
        Long resourceCount = paidResourceMapper.selectCount(resourceWrapper);

        result.put("paidResourceCount", resourceCount);

               
        BigDecimal totalEarnings = creator != null && creator.getTotalEarnings() != null ?
                creator.getTotalEarnings() : BigDecimal.ZERO;

        if (totalEarnings.compareTo(new BigDecimal(creatorConfig.getMinEarningsForVip())) < 0) {
            result.put("eligible", false);
            result.put("reason", "收益不足，需要至少" + creatorConfig.getMinEarningsForVip() + "元");
            return result;
        }

        result.put("totalEarnings", totalEarnings);
        result.put("minRequired", creatorConfig.getMinEarningsForVip());

        return result;
    }

    @Override
    public Map<String, Object> getCreatorVipApplies(Long creatorId) {
        LambdaQueryWrapper<CreatorVipApply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CreatorVipApply::getCreatorId, creatorId)
                .orderByDesc(CreatorVipApply::getCreateTime);

        java.util.List<CreatorVipApply> list = creatorVipApplyMapper.selectList(wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", list.size());

        return result;
    }

    @Override
    public Map<String, Object> getPendingVipApplies(Integer page, Integer size) {
        Page<CreatorVipApply> pageParam = new Page<>(page, size);

        LambdaQueryWrapper<CreatorVipApply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CreatorVipApply::getStatus, "pending")
                .orderByAsc(CreatorVipApply::getCreateTime);

        Page<CreatorVipApply> resultPage = creatorVipApplyMapper.selectPage(pageParam, wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("list", resultPage.getRecords());
        result.put("total", resultPage.getTotal());
        result.put("page", page);
        result.put("size", size);

        return result;
    }

    @Override
    public Map<String, Object> getUserVipOrders(Long userId, Integer page, Integer size) {
        Page<VipOrder> pageParam = new Page<>(page, size);

        LambdaQueryWrapper<VipOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VipOrder::getUserId, userId)
                .orderByDesc(VipOrder::getCreateTime);

        Page<VipOrder> resultPage = vipOrderMapper.selectPage(pageParam, wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("list", resultPage.getRecords());
        result.put("total", resultPage.getTotal());
        result.put("page", page);
        result.put("size", size);

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> renewVip(Long userId, String vipType) {
        return rechargeVip(userId, vipType);
    }

    @Override
    public Boolean canUseVipPrivilege(Long userId, String privilege) {
                   
        Long remainingDays = getVipRemainingDays(userId);
        if (remainingDays <= 0) {
            return false;
        }

                   
        return vipConfig.hasPrivilege(privilege);
    }

                                                     

       
                
                         
                        
                            
                          
                       
                              
       
    private void createVipPurchaseRecord(Long userId, Integer days,
                                        LocalDateTime startTime, LocalDateTime endTime,
                                        String reason, Long operatorId) {
        VipPurchaseRecord record = new VipPurchaseRecord();
        record.setUserId(userId);
        record.setOrderId(operatorId);                
        record.setVipDays(days);
        record.setStartTime(startTime);
        record.setEndTime(endTime);

        vipPurchaseRecordMapper.insert(record);

        log.info("event=vip_purchase_record_created userId={}", userId);
    }

    @Override
    public void recordVipSource(Long userId, String sourceType, LocalDateTime expireTime) {
        try {
            String key = "vip:source:" + userId;
            long ttl = java.time.Duration.between(LocalDateTime.now(), expireTime).getSeconds();
            if (ttl > 0) {
                redisUtils.set(key, sourceType, ttl, TimeUnit.SECONDS);
                log.info("event=vip_source_recorded userId={}", userId);
            }
        } catch (Exception e) {
            log.error("event=vip_source_record_failed errorType={}", e.getClass().getSimpleName());
        }
    }

    @Override
    public String getVipSourceType(Long userId) {
        try {
            String key = "vip:source:" + userId;
            Object sourceObj = redisUtils.get(key); String source = sourceObj != null ? sourceObj.toString() : null;
            return source != null ? source : "none";
        } catch (Exception e) {
            log.error("event=vip_source_query_failed errorType={}", e.getClass().getSimpleName());
            return "none";
        }
    }
}
