   
                      
                        
   

package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.DecorationConfig;
import com.haoran.music.entity.User;
import com.haoran.music.entity.UserDecoration;
import com.haoran.music.mapper.DecorationConfigMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.mapper.UserDecorationMapper;
import com.haoran.music.service.DecorationService;
import com.haoran.music.service.StoreProductPolicyService;
import com.haoran.music.service.UserActivityPointsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

   
           
   
@Slf4j
@Service
public class DecorationServiceImpl implements DecorationService {

    private final UserDecorationMapper userDecorationMapper;
    private final DecorationConfigMapper decorationConfigMapper;
    private final UserActivityPointsService activityPointsService;
    private final UserMapper userMapper;
    private final StoreProductPolicyService storeProductPolicyService;

    public DecorationServiceImpl(
            UserDecorationMapper userDecorationMapper,
            DecorationConfigMapper decorationConfigMapper,
            UserActivityPointsService activityPointsService,
            UserMapper userMapper,
            StoreProductPolicyService storeProductPolicyService) {
        this.userDecorationMapper = userDecorationMapper;
        this.decorationConfigMapper = decorationConfigMapper;
        this.activityPointsService = activityPointsService;
        this.userMapper = userMapper;
        this.storeProductPolicyService = storeProductPolicyService;
    }

    @Override
    public List<UserDecoration> getUserDecorations(Long userId) {
        LambdaQueryWrapper<UserDecoration> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDecoration::getUserId, userId)
                .eq(UserDecoration::getDeleted, 0)
                .orderByDesc(UserDecoration::getObtainTime);
        return userDecorationMapper.selectList(wrapper);
    }

    @Override
    public List<UserDecoration> getUserDecorationsByType(Long userId, String decorationType) {
        LambdaQueryWrapper<UserDecoration> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDecoration::getUserId, userId)
                .eq(UserDecoration::getDecorationType, decorationType)
                .eq(UserDecoration::getDeleted, 0)
                .orderByDesc(UserDecoration::getIsEquipped)
                .orderByDesc(UserDecoration::getObtainTime);
        return userDecorationMapper.selectList(wrapper);
    }

    @Override
    public UserDecoration getEquippedDecoration(Long userId, String decorationType) {
        LambdaQueryWrapper<UserDecoration> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDecoration::getUserId, userId)
                .eq(UserDecoration::getDecorationType, decorationType)
                .eq(UserDecoration::getIsEquipped, 1)
                .eq(UserDecoration::getDeleted, 0);
        return userDecorationMapper.selectOne(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean equipDecoration(Long userId, String decorationId) {
        UserAccountStatusUtil.requireCanInteract(userMapper.selectById(userId), "装备装饰");

                    
        UserDecoration userDecoration = getUserDecoration(userId, decorationId);
        if (userDecoration == null) {
            throw new BusinessException("未拥有该装饰");
        }

                 
        if (userDecoration.getExpireTime() != null &&
            userDecoration.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException("装饰已过期");
        }

                     
        unequipDecoration(userId, userDecoration.getDecorationType());

                
        userDecoration.setIsEquipped(1);
        userDecorationMapper.updateById(userDecoration);

        log.info("用户 {} 装备装饰: {}", userId, decorationId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unequipDecoration(Long userId, String decorationType) {
        LambdaQueryWrapper<UserDecoration> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDecoration::getUserId, userId)
                .eq(UserDecoration::getDecorationType, decorationType)
                .eq(UserDecoration::getIsEquipped, 1)
                .eq(UserDecoration::getDeleted, 0);

        UserDecoration equipped = userDecorationMapper.selectOne(wrapper);
        if (equipped != null) {
            equipped.setIsEquipped(0);
            userDecorationMapper.updateById(equipped);
        }

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean grantDecoration(Long userId, String decorationId, String source) {
        User user = userMapper.selectByIdForUpdate(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

                  
        if (hasDecoration(userId, decorationId)) {
            log.info("用户 {} 已拥有装饰: {}", userId, decorationId);
            return true;
        }

                 
        DecorationConfig config = getDecorationConfig(decorationId);
        if (config == null) {
            throw new BusinessException("装饰不存在");
        }

                 
        UserDecoration userDecoration = new UserDecoration();
        userDecoration.setUserId(userId);
        userDecoration.setDecorationType(config.getDecorationType());
        userDecoration.setDecorationId(decorationId);
        userDecoration.setDecorationName(config.getDecorationName());
        userDecoration.setIsEquipped(0);
        userDecoration.setObtainTime(LocalDateTime.now());
        userDecoration.setSource(source);
        userDecoration.setRarity(config.getRarity());

                       
        if (config.getIsPermanent() == 0 && config.getDurationDays() != null) {
            userDecoration.setExpireTime(LocalDateTime.now().plusDays(config.getDurationDays()));
        }

        userDecorationMapper.insert(userDecoration);

        log.info("event=decoration_granted userId={} decorationId={} source={}",
                userId, decorationId, source);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean redeemDecoration(Long userId, String decorationId) {
        User user = userMapper.selectByIdForUpdate(userId);
        UserAccountStatusUtil.requireCanInteract(user, "兑换装饰");

                 
        DecorationConfig config = getDecorationConfig(decorationId);
        if (config == null) {
            throw new BusinessException("装饰不存在");
        }

        storeProductPolicyService.requirePurchasable("decoration", config.getId());
        if (userId.equals(config.getCreatorId())) {
            throw new BusinessException("不能兑换自己的装饰");
        }

                  
        if (!"points".equals(config.getObtainType()) || config.getPointsCost() == null) {
            throw new BusinessException("该装饰不可兑换");
        }

                  
        if (hasDecoration(userId, decorationId)) {
            throw new BusinessException("已拥有该装饰");
        }

                    
        Integer currentPoints = activityPointsService.getUserTotalPoints(userId);
        if (currentPoints < config.getPointsCost()) {
            throw new BusinessException("活跃值不足，需要" + config.getPointsCost() + "活跃值");
        }

                
        Integer newBalance = activityPointsService.consumePoints(
                userId,
                config.getPointsCost(),
                "redeem",
                "兑换装饰：" + config.getDecorationName()
        );

        if (newBalance < 0) {
            throw new BusinessException("活跃值不足");
        }

               
        return grantDecoration(userId, decorationId, "points");
    }

    @Override
    public List<DecorationConfig> getDecorationConfigs(String decorationType) {
        LambdaQueryWrapper<DecorationConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DecorationConfig::getIsEnabled, 1)
                .eq(DecorationConfig::getDeleted, 0);

        if (decorationType != null && !decorationType.isEmpty()) {
            wrapper.eq(DecorationConfig::getDecorationType, decorationType);
        }

        wrapper.orderByAsc(DecorationConfig::getSortOrder)
                .orderByDesc(DecorationConfig::getCreateTime);

        return decorationConfigMapper.selectList(wrapper);
    }

    @Override
    public boolean hasDecoration(Long userId, String decorationId) {
        return getUserDecoration(userId, decorationId) != null;
    }

       
                
       
    private UserDecoration getUserDecoration(Long userId, String decorationId) {
        LambdaQueryWrapper<UserDecoration> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDecoration::getUserId, userId)
                .eq(UserDecoration::getDecorationId, decorationId)
                .eq(UserDecoration::getDeleted, 0);
        return userDecorationMapper.selectOne(wrapper);
    }

       
             
       
    private DecorationConfig getDecorationConfig(String decorationId) {
        LambdaQueryWrapper<DecorationConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DecorationConfig::getDecorationId, decorationId)
                .eq(DecorationConfig::getDeleted, 0);
        return decorationConfigMapper.selectOne(wrapper);
    }
}
