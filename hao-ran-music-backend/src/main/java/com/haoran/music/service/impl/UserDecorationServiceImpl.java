   
                      
                             
   

package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.entity.DecorationConfig;
import com.haoran.music.entity.UserDecoration;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.mapper.UserDecorationMapper;
import com.haoran.music.service.DecorationConfigService;
import com.haoran.music.service.UserDecorationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

   
                
   
@Slf4j
@Service
public class UserDecorationServiceImpl extends ServiceImpl<UserDecorationMapper, UserDecoration>
        implements UserDecorationService {

    @Resource
    private DecorationConfigService decorationConfigService;

    @Resource
    private UserMapper userMapper;

    @Override
    public boolean hasDecoration(Long userId, String decorationId) {
        LambdaQueryWrapper<UserDecoration> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDecoration::getUserId, userId)
                .eq(UserDecoration::getDecorationId, decorationId);
        return count(wrapper) > 0;
    }

    @Override
    public List<UserDecoration> getUserDecorations(Long userId) {
        LambdaQueryWrapper<UserDecoration> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDecoration::getUserId, userId)
                .orderByDesc(UserDecoration::getObtainTime);
        return list(wrapper);
    }

    @Override
    public List<UserDecoration> getUserDecorationsByType(Long userId, String decorationType) {
        LambdaQueryWrapper<UserDecoration> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDecoration::getUserId, userId)
                .eq(UserDecoration::getDecorationType, decorationType)
                .orderByDesc(UserDecoration::getObtainTime);
        return list(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addDecoration(Long userId, String decorationId, String source) {
        UserAccountStatusUtil.requireCanInteract(userMapper.selectById(userId), "获得装饰");

                  
        if (hasDecoration(userId, decorationId)) {
            return false;
        }

                 
        DecorationConfig config = decorationConfigService.getByDecorationId(decorationId);
        if (config == null) {
            throw new RuntimeException("装饰不存在: " + decorationId);
        }

                   
        UserDecoration userDecoration = new UserDecoration();
        userDecoration.setUserId(userId);
        userDecoration.setDecorationType(config.getDecorationType());
        userDecoration.setDecorationId(config.getDecorationId());
        userDecoration.setDecorationName(config.getDecorationName());
        userDecoration.setIsEquipped(0);
        userDecoration.setObtainTime(LocalDateTime.now());
        userDecoration.setSource(source);
        userDecoration.setSourceDescription(getSourceDescription(source));
        userDecoration.setRarity(config.getRarity());

                 
        if (config.getIsPermanent() == 0 && config.getDurationDays() != null) {
            userDecoration.setExpireTime(LocalDateTime.now().plusDays(config.getDurationDays()));
        }

        save(userDecoration);
        log.info("event=user_decoration_granted userId={} decorationId={} source={}",
                userId, decorationId, source);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void equipDecoration(Long userId, String decorationId) {
        LambdaQueryWrapper<UserDecoration> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDecoration::getUserId, userId)
                .eq(UserDecoration::getDecorationId, decorationId);
        UserDecoration decoration = getOne(wrapper);

        if (decoration == null) {
            throw new RuntimeException("未拥有该装饰");
        }

                 
        if (decoration.getExpireTime() != null && decoration.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("装饰已过期");
        }

                     
        LambdaQueryWrapper<UserDecoration> unequipWrapper = new LambdaQueryWrapper<>();
        unequipWrapper.eq(UserDecoration::getUserId, userId)
                .eq(UserDecoration::getDecorationType, decoration.getDecorationType())
                .eq(UserDecoration::getIsEquipped, 1);
        UserDecoration unequipDecoration = getOne(unequipWrapper);
        if (unequipDecoration != null) {
            unequipDecoration.setIsEquipped(0);
            updateById(unequipDecoration);
        }

                 
        decoration.setIsEquipped(1);
        updateById(decoration);

        log.info("装备装饰: userId={}, decorationId={}", userId, decorationId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unequipDecoration(Long userId, String decorationType) {
        LambdaQueryWrapper<UserDecoration> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDecoration::getUserId, userId)
                .eq(UserDecoration::getDecorationType, decorationType)
                .eq(UserDecoration::getIsEquipped, 1);
        UserDecoration decoration = getOne(wrapper);

        if (decoration != null) {
            decoration.setIsEquipped(0);
            updateById(decoration);
            log.info("卸载装饰: userId={}, decorationType={}", userId, decorationType);
        }
    }

       
             
       
    private String getSourceDescription(String source) {
        switch (source) {
            case "sign": return "签到获取";
            case "activity": return "活动获取";
            case "vip": return "VIP专属";
            case "achievement": return "成就奖励";
            case "points": return "活跃值兑换";
            case "payment": return "购买获取";
            default: return "其他";
        }
    }
       
                  
       
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addUserBadge(Long userId, String badgeType, String badgeName, String badgeIcon,
                                String badgeColor, String displayPosition, int days) {
        UserAccountStatusUtil.requireCanInteract(userMapper.selectById(userId), "获得徽章");

                 
        String decorationId = "badge_" + badgeType;
        
                  
        if (hasDecoration(userId, decorationId)) {
            log.info("用户已拥有该徽章: userId={}, badgeType={}", userId, badgeType);
            return false;
        }

                   
        UserDecoration userDecoration = new UserDecoration();
        userDecoration.setUserId(userId);
        userDecoration.setDecorationType("badge");
        userDecoration.setDecorationId(decorationId);
        userDecoration.setDecorationName(badgeName);
        userDecoration.setIsEquipped(0);
        userDecoration.setObtainTime(LocalDateTime.now());
        userDecoration.setSource("achievement");
        userDecoration.setSourceDescription("成就奖励获取");
        userDecoration.setRarity("rare");

                 
        if (days > 0) {
            userDecoration.setExpireTime(LocalDateTime.now().plusDays(days));
        }

        save(userDecoration);
        log.info("用户获得徽章: userId={}, badgeName={}, days={}", userId, badgeName, days);
        return true;
    }
}
