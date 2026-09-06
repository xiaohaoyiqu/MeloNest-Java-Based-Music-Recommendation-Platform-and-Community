




package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.UserDecoration;
import com.haoran.music.entity.UserEquipmentConfig;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.mapper.UserEquipmentConfigMapper;
import com.haoran.music.service.UserDecorationService;
import com.haoran.music.service.UserEquipmentConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.time.LocalDateTime;




@Slf4j
@Service
public class UserEquipmentConfigServiceImpl extends ServiceImpl<UserEquipmentConfigMapper, UserEquipmentConfig>
        implements UserEquipmentConfigService {

    @Resource
    private UserDecorationService userDecorationService;

    @Resource
    private UserMapper userMapper;

    @Override
    public Map<String, Object> getUserEquipmentConfig(Long userId) {
        LambdaQueryWrapper<UserEquipmentConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserEquipmentConfig::getUserId, userId);
        UserEquipmentConfig config = getOne(wrapper);

        if (config == null) {
            config = new UserEquipmentConfig();
            config.setUserId(userId);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("avatarFrame", config.getAvatarFrameId());
        result.put("profileCard", config.getProfileCardId());
        result.put("profileCardStyle", config.getProfileCardStyle());
        result.put("chatBubble", config.getChatBubbleId());


        List<String> badges = new ArrayList<>();
        if (config.getBadge1Id() != null) badges.add(config.getBadge1Id());
        if (config.getBadge2Id() != null) badges.add(config.getBadge2Id());
        if (config.getBadge3Id() != null) badges.add(config.getBadge3Id());
        if (config.getBadge4Id() != null) badges.add(config.getBadge4Id());
        if (config.getBadge5Id() != null) badges.add(config.getBadge5Id());
        if (config.getBadge6Id() != null) badges.add(config.getBadge6Id());
        if (config.getBadge7Id() != null) badges.add(config.getBadge7Id());
        if (config.getBadge8Id() != null) badges.add(config.getBadge8Id());
        if (config.getBadge9Id() != null) badges.add(config.getBadge9Id());
        if (config.getBadge10Id() != null) badges.add(config.getBadge10Id());
        result.put("badges", badges);

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateEquipmentConfig(Long userId, Map<String, Object> config) {
        UserAccountStatusUtil.requireCanInteract(userMapper.selectById(userId), "更新装备配置");
        if (config == null) {
            throw new IllegalArgumentException("装备配置不能为空");
        }
        Set<String> supportedKeys = new HashSet<>(Arrays.asList(
                "avatarFrame", "profileCard", "profileCardStyle", "chatBubble", "badges"));
        if (!supportedKeys.containsAll(config.keySet())) {
            throw new IllegalArgumentException("装备配置包含不支持的字段");
        }

        Map<String, UserDecoration> ownedDecorations = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        for (UserDecoration decoration : userDecorationService.getUserDecorations(userId)) {
            if (decoration.getExpireTime() == null || decoration.getExpireTime().isAfter(now)) {
                ownedDecorations.put(decoration.getDecorationId(), decoration);
            }
        }

        LambdaQueryWrapper<UserEquipmentConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserEquipmentConfig::getUserId, userId);
        UserEquipmentConfig equipmentConfig = getOne(wrapper);

        if (equipmentConfig == null) {
            equipmentConfig = new UserEquipmentConfig();
            equipmentConfig.setUserId(userId);
        }


        if (config.containsKey("avatarFrame")) {
            equipmentConfig.setAvatarFrameId(validateOwnedDecoration(
                    ownedDecorations, config.get("avatarFrame"), "avatar_frame"));
        }
        if (config.containsKey("profileCard")) {
            equipmentConfig.setProfileCardId(validateOwnedDecoration(
                    ownedDecorations, config.get("profileCard"), "theme"));
        }
        if (config.containsKey("profileCardStyle")) {
            equipmentConfig.setProfileCardStyle(validateStyle(config.get("profileCardStyle")));
        }
        if (config.containsKey("chatBubble")) {
            equipmentConfig.setChatBubbleId(validateOwnedDecoration(
                    ownedDecorations, config.get("chatBubble"), "dialog_box"));
        }


        Object badgesObj = config.get("badges");
        List<String> badges = null;
        if (badgesObj instanceof List) {
            badges = new ArrayList<>();
            for (Object badge : (List<?>) badgesObj) {
                badges.add(String.valueOf(badge));
            }
        } else if (badgesObj instanceof String) {
            String badgesStr = (String) badgesObj;
            badges = badgesStr.trim().isEmpty()
                    ? Collections.emptyList()
                    : Arrays.asList(badgesStr.split(","));
        } else if (badgesObj != null) {
            throw new IllegalArgumentException("徽章配置格式错误");
        }
        if (badges != null) {
            if (badges.size() > 10 || new HashSet<>(badges).size() != badges.size()) {
                throw new IllegalArgumentException("最多装备10个且不能重复的徽章");
            }
            clearBadges(equipmentConfig);
            for (int i = 0; i < badges.size(); i++) {
                String badgeId = validateOwnedDecoration(ownedDecorations, badges.get(i), "badge");
                switch (i) {
                    case 0: equipmentConfig.setBadge1Id(badgeId); break;
                    case 1: equipmentConfig.setBadge2Id(badgeId); break;
                    case 2: equipmentConfig.setBadge3Id(badgeId); break;
                    case 3: equipmentConfig.setBadge4Id(badgeId); break;
                    case 4: equipmentConfig.setBadge5Id(badgeId); break;
                    case 5: equipmentConfig.setBadge6Id(badgeId); break;
                    case 6: equipmentConfig.setBadge7Id(badgeId); break;
                    case 7: equipmentConfig.setBadge8Id(badgeId); break;
                    case 8: equipmentConfig.setBadge9Id(badgeId); break;
                    case 9: equipmentConfig.setBadge10Id(badgeId); break;
                }
            }
        }

        saveOrUpdate(equipmentConfig);
        log.info("更新用户装备配置: userId={}, fields={}", userId, config.keySet());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void equipDecoration(Long userId, String decorationType, String decorationId) {
        UserAccountStatusUtil.requireCanInteract(userMapper.selectById(userId), "装备装饰");

        UserDecoration decoration = userDecorationService.lambdaQuery()
                .eq(UserDecoration::getUserId, userId)
                .eq(UserDecoration::getDecorationId, decorationId)
                .one();
        if (decoration == null) {
            throw new RuntimeException("未拥有该装饰");
        }
        if (decoration.getExpireTime() != null && !decoration.getExpireTime().isAfter(LocalDateTime.now())) {
            throw new RuntimeException("装饰已过期");
        }

        LambdaQueryWrapper<UserEquipmentConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserEquipmentConfig::getUserId, userId);
        UserEquipmentConfig config = getOne(wrapper);

        if (config == null) {
            config = new UserEquipmentConfig();
            config.setUserId(userId);
        }


        switch (decorationType) {
            case "avatar_frame":
                requireDecorationType(decoration, "avatar_frame");
                config.setAvatarFrameId(decorationId);
                break;
            case "profile_card":
                requireDecorationType(decoration, "theme");
                config.setProfileCardId(decorationId);
                break;
            case "chat_bubble":
                requireDecorationType(decoration, "dialog_box");
                config.setChatBubbleId(decorationId);
                break;
            default:
                throw new RuntimeException("不支持的装饰类型: " + decorationType);
        }

        saveOrUpdate(config);
        log.info("装备装饰: userId={}, type={}, decorationId={}", userId, decorationType, decorationId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unequipDecoration(Long userId, String decorationType) {
        LambdaQueryWrapper<UserEquipmentConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserEquipmentConfig::getUserId, userId);
        UserEquipmentConfig config = getOne(wrapper);

        if (config == null) {
            return;
        }


        switch (decorationType) {
            case "avatar_frame":
                config.setAvatarFrameId(null);
                break;
            case "profile_card":
                config.setProfileCardId(null);
                break;
            case "chat_bubble":
                config.setChatBubbleId(null);
                break;
            default:
                throw new RuntimeException("不支持的装饰类型: " + decorationType);
        }

        updateById(config);
        log.info("卸载装饰: userId={}, type={}", userId, decorationType);
    }

    @Override
    public List<String> getEquippedBadges(Long userId) {
        LambdaQueryWrapper<UserEquipmentConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserEquipmentConfig::getUserId, userId);
        UserEquipmentConfig config = getOne(wrapper);

        if (config == null) {
            return new ArrayList<>();
        }

        List<String> badges = new ArrayList<>();
        if (config.getBadge1Id() != null) badges.add(config.getBadge1Id());
        if (config.getBadge2Id() != null) badges.add(config.getBadge2Id());
        if (config.getBadge3Id() != null) badges.add(config.getBadge3Id());
        if (config.getBadge4Id() != null) badges.add(config.getBadge4Id());
        if (config.getBadge5Id() != null) badges.add(config.getBadge5Id());
        if (config.getBadge6Id() != null) badges.add(config.getBadge6Id());
        if (config.getBadge7Id() != null) badges.add(config.getBadge7Id());
        if (config.getBadge8Id() != null) badges.add(config.getBadge8Id());
        if (config.getBadge9Id() != null) badges.add(config.getBadge9Id());
        if (config.getBadge10Id() != null) badges.add(config.getBadge10Id());

        return badges;
    }

    private String validateOwnedDecoration(Map<String, UserDecoration> ownedDecorations,
                                           Object value,
                                           String requiredType) {
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return null;
        }
        String decorationId = String.valueOf(value).trim();
        UserDecoration decoration = ownedDecorations.get(decorationId);
        if (decoration == null) {
            throw new IllegalArgumentException("未拥有或装饰已过期: " + decorationId);
        }
        requireDecorationType(decoration, requiredType);
        return decorationId;
    }

    private void requireDecorationType(UserDecoration decoration, String requiredType) {
        if (!requiredType.equals(decoration.getDecorationType())) {
            throw new IllegalArgumentException("装饰类型不匹配");
        }
    }

    private String validateStyle(Object value) {
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return null;
        }
        String style = String.valueOf(value).trim();
        if (!style.matches("[A-Za-z0-9_-]{1,32}")) {
            throw new IllegalArgumentException("个人卡片样式不合法");
        }
        return style;
    }

    private void clearBadges(UserEquipmentConfig config) {
        config.setBadge1Id(null);
        config.setBadge2Id(null);
        config.setBadge3Id(null);
        config.setBadge4Id(null);
        config.setBadge5Id(null);
        config.setBadge6Id(null);
        config.setBadge7Id(null);
        config.setBadge8Id(null);
        config.setBadge9Id(null);
        config.setBadge10Id(null);
    }
}
