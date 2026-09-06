  
                      
                           
   
package com.haoran.music.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.entity.BadgeGrantEvent;
import com.haoran.music.entity.BadgeRule;
import com.haoran.music.entity.CreatorWork;
import com.haoran.music.entity.User;
import com.haoran.music.entity.UserBadge;
import com.haoran.music.mapper.CreatorWorkMapper;
import com.haoran.music.mapper.UserBadgeMapper;
import com.haoran.music.mapper.BadgeGrantEventMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.vo.badge.UserBadgeVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

   
              
                                        
  
   
@Slf4j
@Service
public class UserBadgeService extends ServiceImpl<UserBadgeMapper, UserBadge> {

    @Resource
    private UserMapper userMapper;

    @Resource
    private ListenHistoryService listenHistoryService;

    @Resource
    private CommentService commentService;

    @Resource
    private FavoriteHistoryService favoriteHistoryService;

    @Resource
    private UserCheckinService userCheckinService;

    @Resource
    private CreatorWorkMapper creatorWorkMapper;

    @Resource
    private UserActivityEnhancedService userActivityEnhancedService;

    @Autowired(required = false)
    private BadgeRuleCatalogService badgeRuleCatalogService;

    @Autowired(required = false)
    private BadgeGrantEventMapper badgeGrantEventMapper;

                                                           

       
                      
       
    private static final Map<String, BadgeConfig> BADGE_CONFIGS = new LinkedHashMap<>();

    static {
                                      
        BADGE_CONFIGS.put("music_master", new BadgeConfig("music_master", "音乐达人", "🎵", "#ff6b6b",
            "累计播放100首歌曲", "播放100首不同歌曲", "achievement", "common"));
        BADGE_CONFIGS.put("music_legend", new BadgeConfig("music_legend", "音乐传奇", "👑", "#ffd700",
            "累计播放500首歌曲", "播放500首不同歌曲", "achievement", "legendary"));
        BADGE_CONFIGS.put("comment_master", new BadgeConfig("comment_master", "评论达人", "💬", "#4facfe",
            "发表50条评论", "发表50条评论", "achievement", "common"));
        BADGE_CONFIGS.put("comment_expert", new BadgeConfig("comment_expert", "评论专家", "📝", "#00f2fe",
            "发表200条评论", "发表200条评论", "achievement", "rare"));
        BADGE_CONFIGS.put("collector", new BadgeConfig("collector", "收藏家", "⭐", "#f093fb",
            "收藏50首歌曲", "收藏50首歌曲", "achievement", "common"));
        BADGE_CONFIGS.put("super_collector", new BadgeConfig("super_collector", "超级收藏家", "💎", "#f5576c",
            "收藏200首歌曲", "收藏200首歌曲", "achievement", "epic"));
        BADGE_CONFIGS.put("social_star", new BadgeConfig("social_star", "社交新星", "🌟", "#67c23a",
            "获得10个粉丝", "获得10个粉丝", "achievement", "common"));
        BADGE_CONFIGS.put("social_influencer", new BadgeConfig("social_influencer", "社交达人", "🎖️", "#85ce61",
            "获得100个粉丝", "获得100个粉丝", "achievement", "rare"));
        BADGE_CONFIGS.put("active_user", new BadgeConfig("active_user", "活跃用户", "🔥", "#fa709a",
            "连续签到7天", "连续签到7天", "achievement", "common"));
        BADGE_CONFIGS.put("loyal_user", new BadgeConfig("loyal_user", "忠实用户", "💚", "#fee140",
            "连续签到30天", "连续签到30天", "achievement", "rare"));
        BADGE_CONFIGS.put("early_bird", new BadgeConfig("early_bird", "早期用户", "🐣", "#a18cd1",
            "注册30天以上", "注册满30天", "achievement", "common"));
        BADGE_CONFIGS.put("veteran", new BadgeConfig("veteran", "元老用户", "🏅", "#fbc2eb",
            "注册1年以上", "注册满1年", "achievement", "epic"));

                                          
        BADGE_CONFIGS.put("active_normal", new BadgeConfig("active_normal", "活跃新星", "🌱", "#90ee90",
            "活跃度达到30分", "保持活跃获得30活跃度评分", "activity", "common"));
        BADGE_CONFIGS.put("active_member", new BadgeConfig("active_member", "活跃会员", "🌿", "#00d2d3",
            "活跃度达到60分", "保持活跃获得60活跃度评分", "activity", "rare"));
        BADGE_CONFIGS.put("active_master", new BadgeConfig("active_master", "活跃大师", "🌻", "#ff9f43",
            "活跃度达到80分", "保持活跃获得80活跃度评分", "activity", "epic"));
        BADGE_CONFIGS.put("active_legend", new BadgeConfig("active_legend", "活跃传奇", "🌺", "#ff6b6b",
            "活跃度达到100分", "保持活跃获得100活跃度评分", "activity", "legendary"));

                                      
        BADGE_CONFIGS.put("vip_monthly", new BadgeConfig("vip_monthly", "月度会员", "💎", "#ffd700",
            "月度VIP会员", "开通月度会员", "vip", "common"));
        BADGE_CONFIGS.put("vip_quarterly", new BadgeConfig("vip_quarterly", "季度会员", "💠", "#ffec8b",
            "季度VIP会员", "开通季度会员", "vip", "rare"));
        BADGE_CONFIGS.put("vip_yearly", new BadgeConfig("vip_yearly", "年度会员", "👑", "#ffeaa7",
            "年度VIP会员", "开通年度会员", "vip", "epic"));

                                      
        BADGE_CONFIGS.put("creator_basic", new BadgeConfig("creator_basic", "创作者", "🎤", "#ff6b6b",
            "认证创作者", "成为认证创作者", "creator", "common"));
        BADGE_CONFIGS.put("creator_pro", new BadgeConfig("creator_pro", "资深创作者", "🎸", "#ff8e8e",
            "发布10首作品", "发布10首原创作品", "creator", "rare"));
        BADGE_CONFIGS.put("creator_master", new BadgeConfig("creator_master", "创作大师", "🎹", "#ff4757",
            "发布50首作品", "发布50首原创作品", "creator", "epic"));
        BADGE_CONFIGS.put("creator_legend", new BadgeConfig("creator_legend", "创作传奇", "🎼", "#c0392b",
            "发布100首作品", "发布100首原创作品", "creator", "legendary"));

                                         
        BADGE_CONFIGS.put("spring_festival_2026", new BadgeConfig("spring_festival_2026", "新春快乐", "🧧", "#e74c3c",
            "2026春节限定", "在2026年春节期间签到", "festival", "legendary"));
        BADGE_CONFIGS.put("valentine_2026", new BadgeConfig("valentine_2026", "情人节限定", "💕", "#fd79a8",
            "2026情人节限定", "在2026年情人节登录", "festival", "epic"));
        BADGE_CONFIGS.put("labor_day_2026", new BadgeConfig("labor_day_2026", "劳动最光荣", "🛠️", "#f39c12",
            "2026劳动节限定", "在2026年劳动节签到", "festival", "rare"));
        BADGE_CONFIGS.put("children_day_2026", new BadgeConfig("children_day_2026", "童心未泯", "🎈", "#55efc4",
            "2026儿童节限定", "在2026年儿童节登录", "festival", "rare"));

                                           
        BADGE_CONFIGS.put("first_song", new BadgeConfig("first_song", "初听之音", "🎧", "#74b9ff",
            "首次播放歌曲", "播放第一首歌曲", "special", "common"));
        BADGE_CONFIGS.put("night_owl", new BadgeConfig("night_owl", "夜猫子", "🦉", "#6c5ce7",
            "深夜听歌达人", "在23:00-2:00播放歌曲超过10次", "special", "rare"));
        BADGE_CONFIGS.put("morning_bird", new BadgeConfig("morning_bird", "早起鸟", "🐦", "#a29bfe",
            "清晨听歌达人", "在5:00-8:00播放歌曲超过10次", "special", "rare"));
        BADGE_CONFIGS.put("explorer", new BadgeConfig("explorer", "音乐探索者", "🗺️", "#00cec9",
            "听歌类型多样", "播放10种不同流派的歌曲", "special", "epic"));
        BADGE_CONFIGS.put("commentator", new BadgeConfig("commentator", "评论家", "✍️", "#e17055",
            "评论大师", "累计获得100条评论点赞", "special", "epic"));
        BADGE_CONFIGS.put("helper", new BadgeConfig("helper", "热心助人", "🤝", "#00b894",
            "帮助他人", "帮助10位新用户完成注册", "special", "rare"));
    }

       
             
       
    private static final Map<String, BadgeCategory> BADGE_CATEGORIES = new HashMap<>();

    static {
        BADGE_CATEGORIES.put("achievement", new BadgeCategory("achievement", "成就徽章", "通过使用平台功能获得", 1));
        BADGE_CATEGORIES.put("activity", new BadgeCategory("activity", "活跃度徽章", "根据活跃度获得", 2));
        BADGE_CATEGORIES.put("vip", new BadgeCategory("vip", "VIP徽章", "开通会员获得", 3));
        BADGE_CATEGORIES.put("creator", new BadgeCategory("creator", "创作者徽章", "创作者专属", 4));
        BADGE_CATEGORIES.put("festival", new BadgeCategory("festival", "节日徽章", "节日限定", 5));
        BADGE_CATEGORIES.put("special", new BadgeCategory("special", "特殊成就", "特殊方式获得", 6));
    }

       
            
       
    private static final Map<String, RarityConfig> RARITY_CONFIGS = new HashMap<>();

    static {
        RARITY_CONFIGS.put("common", new RarityConfig("common", "普通", "#999999", 1));
        RARITY_CONFIGS.put("rare", new RarityConfig("rare", "稀有", "#4facfe", 2));
        RARITY_CONFIGS.put("epic", new RarityConfig("epic", "史诗", "#f093fb", 3));
        RARITY_CONFIGS.put("legendary", new RarityConfig("legendary", "传说", "#ffd700", 4));
    }

       
              
       
    private static class BadgeConfig {
        String type;
        String name;
        String icon;
        String color;
        String description;
        String obtainMethod;
        String category;
        String rarity;

        BadgeConfig(String type, String name, String icon, String color,
                   String description, String obtainMethod, String category, String rarity) {
            this.type = type;
            this.name = name;
            this.icon = icon;
            this.color = color;
            this.description = description;
            this.obtainMethod = obtainMethod;
            this.category = category;
            this.rarity = rarity;
        }
    }

       
              
       
    private static class BadgeCategory {
        String code;
        String name;
        String description;
        int displayOrder;

        BadgeCategory(String code, String name, String description, int displayOrder) {
            this.code = code;
            this.name = name;
            this.description = description;
            this.displayOrder = displayOrder;
        }
    }

       
               
       
    private static class RarityConfig {
        String code;
        String name;
        String borderColor;
        int displayOrder;

        RarityConfig(String code, String name, String borderColor, int displayOrder) {
            this.code = code;
            this.name = name;
            this.borderColor = borderColor;
            this.displayOrder = displayOrder;
        }
    }

                                                       

       
                    
      
                         
                     
       
    public List<UserBadgeVO> getUserBadgeVOList(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return Collections.emptyList();
        }

        List<UserBadge> badges = lambdaQuery()
            .eq(UserBadge::getUserId, userId)
            .and(w -> w.isNull(UserBadge::getExpireTime)
                .or()
                .gt(UserBadge::getExpireTime, LocalDateTime.now()))
            .list();

        return badges.stream()
            .filter(this::isValidBadge)
            .map(this::convertToVO)
            .sorted(Comparator.comparing(UserBadgeVO::getRarityOrder).reversed())
            .collect(Collectors.toList());
    }

       
                    
      
                                            
       
    public List<UserBadgeVO> getPublicUserBadgeVOList(Long userId) {
        if (ObjectUtils.isEmpty(userId)
                || !UserAccountStatusUtil.canExposePublicContent(userId, userMapper::selectById)) {
            return Collections.emptyList();
        }
        List<UserBadge> badges = lambdaQuery()
                .eq(UserBadge::getUserId, userId)
                .and(w -> w.isNull(UserBadge::getExpireTime)
                        .or()
                        .gt(UserBadge::getExpireTime, LocalDateTime.now()))
                .list();
        return badges.stream()
                .filter(this::isValidBadge)
                .filter(this::isPubliclyVisible)
                .map(this::convertToVO)
                .sorted(Comparator.comparing(UserBadgeVO::getRarityOrder).reversed())
                .collect(Collectors.toList());
    }

       
                                            
       
    private boolean isPubliclyVisible(UserBadge badge) {
        if (ObjectUtils.isEmpty(badgeRuleCatalogService) || ObjectUtils.isEmpty(badge.getRuleId())) {
            return true;
        }
        BadgeRule rule = badgeRuleCatalogService.resolveDisplayRule(badge.getRuleId());
        return ObjectUtils.isNotEmpty(rule) && "public".equals(rule.getVisibility());
    }

       
                  
      
                         
                           
                   
       
    public List<UserBadgeVO> getUserBadgesByCategory(Long userId, String category) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(category)) {
            return Collections.emptyList();
        }

        List<UserBadge> badges = lambdaQuery()
            .eq(UserBadge::getUserId, userId)
            .and(w -> w.isNull(UserBadge::getExpireTime)
                .or()
                .gt(UserBadge::getExpireTime, LocalDateTime.now()))
            .list();

        return badges.stream()
            .filter(this::isValidBadge)
            .map(this::convertToVO)
            .filter(b -> category.equals(b.getCategory()))
            .collect(Collectors.toList());
    }

       
               
      
                        
                   
       
    private boolean isValidBadge(UserBadge badge) {
        if (badge == null) {
            return false;
        }
                 
        if (badge.getExpireTime() != null && badge.getExpireTime().isBefore(LocalDateTime.now())) {
            return false;
        }
        return true;
    }

       
               
      
                        
                   
       
    private UserBadgeVO convertToVO(UserBadge badge) {
        UserBadgeVO vo = new UserBadgeVO();
        BeanUtils.copyProperties(badge, vo);

                 
        if (badge.getExpireTime() == null) {
            vo.setRemainingDays(-1);      
        } else {
            long days = ChronoUnit.DAYS.between(LocalDateTime.now(), badge.getExpireTime());
            vo.setRemainingDays((int) Math.max(0, days));
        }

        BadgeRule rule = badgeRuleCatalogService == null || badge.getRuleId() == null
                ? null : badgeRuleCatalogService.resolveDisplayRule(badge.getRuleId());
        if (rule != null) {
            vo.setBadgeName(rule.getBadgeName());
            vo.setBadgeIcon(badgeRuleCatalogService.resolveIcon(rule));
            vo.setBadgeColor(rule.getBadgeColor());
            vo.setDescription(rule.getDescription());
            vo.setObtainMethod(rule.getObtainMethod());
            vo.setRarity(rule.getRarity());
            vo.setCategory(rule.getCategory());
            vo.setRarityOrder(rarityOrder(rule.getRarity()));
        } else {
                                             
            BadgeConfig config = BADGE_CONFIGS.get(badge.getBadgeType());
            if (config != null) {
            vo.setDescription(config.description);
            vo.setObtainMethod(config.obtainMethod);
            vo.setRarity(config.rarity);
            vo.setCategory(config.category);
            vo.setRarityOrder(RARITY_CONFIGS.getOrDefault(config.rarity,
                new RarityConfig("common", "普通", "#999999", 1)).displayOrder);
            } else {
                vo.setDescription(badge.getBadgeDescription());
                vo.setObtainMethod("系统奖励");
                vo.setRarity("common");
                vo.setCategory(badge.getBadgeType() != null && badge.getBadgeType().startsWith("signin_")
                        ? "achievement" : "special");
                vo.setRarityOrder(1);
            }
        }

        vo.setIsEquipped(Integer.valueOf(1).equals(badge.getIsEquipped()));
        return vo;
    }

       
                       
       
    private int rarityOrder(String rarity) {
        RarityConfig config = RARITY_CONFIGS.get(rarity);
        return config == null ? 1 : config.displayOrder;
    }

                                                     

       
              
      
                             
                             
                             
                             
                             
                             
                             
       
    @Transactional(rollbackFor = Exception.class)
    private void addLegacyUserBadge(Long userId, String badgeType, String badgeName,
                                    String badgeIcon, String badgeColor,
                                    String position, Integer days) {
        if (userMapper.selectByIdForUpdate(userId) == null) {
            throw new IllegalArgumentException("用户不存在");
        }
                      
        UserBadge existing = lambdaQuery()
            .eq(UserBadge::getUserId, userId)
            .eq(UserBadge::getBadgeType, badgeType)
            .one();

        if (existing != null) {
                     
            if (days != null && days > 0) {
                existing.setExpireTime(LocalDateTime.now().plusDays(days));
                updateById(existing);
            }
            return;
        }

                
        UserBadge badge = new UserBadge();
        badge.setUserId(userId);
        badge.setBadgeType(badgeType);
        badge.setBadgeName(badgeName);
        badge.setBadgeIcon(badgeIcon);
        badge.setBadgeColor(badgeColor);
        badge.setPosition(position);
        badge.setIsEquipped(0);
        badge.setObtainTime(LocalDateTime.now());

        if (days != null && days > 0) {
            badge.setExpireTime(LocalDateTime.now().plusDays(days));
        }

        save(badge);
        log.info("颁发徽章成功: userId={}, badgeType={}, badgeName={}", userId, badgeType, badgeName);
    }

       
                       
      
                         
                            
                         
                                   
                              
                         
                         
       
    @Transactional(rollbackFor = Exception.class)
    public boolean grantBadgeByRule(Long userId, String badgeType, Integer days,
                                    Long operatorId, String requestId, String reason) {
        requireConfiguredRuleServices();
        requireOperator(operatorId);
        requireStableRequest(requestId, reason);
        User user = userMapper.selectByIdForUpdate(userId);
        if (ObjectUtils.isEmpty(user)) {
            throw new IllegalArgumentException("用户不存在");
        }
        BadgeRule rule = badgeRuleCatalogService.findActiveRule(badgeType, LocalDateTime.now());
        if (ObjectUtils.isEmpty(rule)) {
            throw new IllegalArgumentException("徽章规则不存在或当前不可授予");
        }
        String evidenceId = "admin:" + requestId.trim();
        Integer effectiveDays = ObjectUtils.isEmpty(days) ? rule.getGrantDays() : days;
        return grantRule(userId, rule, effectiveDays, "admin_decision", evidenceId,
                "operatorDecision=true", operatorId, truncate(reason, 500));
    }

       
                        
      
                         
                            
                               
                               
                                    
                         
       
    @Transactional(rollbackFor = Exception.class)
    public boolean grantSystemBadge(Long userId, String badgeType, String evidenceType,
                                    String evidenceId, String evidenceSummary) {
        requireConfiguredRuleServices();
        if (ObjectUtils.isEmpty(evidenceId) || evidenceId.length() > 128) {
            throw new IllegalArgumentException("徽章证据ID不合法");
        }
        User user = userMapper.selectByIdForUpdate(userId);
        if (ObjectUtils.isEmpty(user)) {
            throw new IllegalArgumentException("用户不存在");
        }
        BadgeRule rule = badgeRuleCatalogService.findActiveRule(badgeType, LocalDateTime.now());
        if (ObjectUtils.isEmpty(rule)) {
            throw new IllegalArgumentException("徽章规则不存在或当前不可授予");
        }
        if (!normalizeCode(evidenceType, "evidence")
                .equals(normalizeCode(rule.getEvidenceType(), "evidence"))) {
            throw new IllegalArgumentException("徽章证据类型与规则不匹配");
        }
        return grantRule(userId, rule, rule.getGrantDays(), evidenceType, evidenceId,
                evidenceSummary, null, "系统规则授予");
    }

       
                            
      
                         
                            
                                   
                              
                           
                                     
       
    @Transactional(rollbackFor = Exception.class)
    public boolean revokeBadge(Long userId, String badgeType, Long operatorId,
                               String requestId, String reason) {
        requireConfiguredRuleServices();
        requireOperator(operatorId);
        requireStableRequest(requestId, reason);
        if (ObjectUtils.isEmpty(userMapper.selectByIdForUpdate(userId))) {
            throw new IllegalArgumentException("用户不存在");
        }
        UserBadge current = lambdaQuery()
                .eq(UserBadge::getUserId, userId)
                .eq(UserBadge::getBadgeType, badgeType)
                .one();
        String safeBadgeType = normalizeCode(badgeType, "badge");
        String businessKey = "badge-revoke:" + userId + ":" + safeBadgeType + ":" + requestId.trim();
        if (ObjectUtils.isEmpty(current)) {
            return ObjectUtils.isNotEmpty(badgeGrantEventMapper.selectByBusinessKey(businessKey));
        }
        BadgeGrantEvent event = grantEvent(businessKey, userId, badgeType,
                current.getRuleVersion(), "revoke",
                "admin_decision", "admin:" + requestId.trim(), "ownership=revoked",
                operatorId, truncate(reason, 500));
        if (badgeGrantEventMapper.insertIgnore(event) == 0) {
            return true;
        }
        LambdaQueryWrapper<UserBadge> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserBadge::getUserId, userId).eq(UserBadge::getBadgeType, badgeType);
        return baseMapper.delete(wrapper) == 1;
    }

       
                              
       
    private boolean grantRule(Long userId, BadgeRule rule, Integer days,
                              String evidenceType, String evidenceId, String evidenceSummary,
                              Long operatorId, String reason) {
        String safeEvidenceType = normalizeCode(evidenceType, "evidence");
        if (ObjectUtils.isEmpty(evidenceId) || evidenceId.length() > 128) {
            throw new IllegalArgumentException("徽章证据ID不合法");
        }
        String evidenceKey = UUID.nameUUIDFromBytes(
                (safeEvidenceType + ":" + evidenceId).getBytes(StandardCharsets.UTF_8)).toString();
        String businessKey = "badge-grant:" + userId + ":" + rule.getBadgeType()
                + ":v" + rule.getRuleVersion() + ":" + evidenceKey;
        BadgeGrantEvent event = grantEvent(businessKey, userId, rule.getBadgeType(),
                rule.getRuleVersion(), "grant", safeEvidenceType, evidenceId,
                truncate(evidenceSummary, 500), operatorId, truncate(reason, 500));
        if (badgeGrantEventMapper.insertIgnore(event) == 0) {
            return false;
        }

        UserBadge badge = lambdaQuery()
                .eq(UserBadge::getUserId, userId)
                .eq(UserBadge::getBadgeType, rule.getBadgeType())
                .one();
        boolean isNew = ObjectUtils.isEmpty(badge);
        if (isNew) {
            badge = new UserBadge();
            badge.setUserId(userId);
            badge.setBadgeType(rule.getBadgeType());
            badge.setIsEquipped(0);
        }
        badge.setObtainTime(LocalDateTime.now());
        badge.setBadgeName(rule.getBadgeName());
        badge.setBadgeDescription(rule.getDescription());
        badge.setBadgeIcon(badgeRuleCatalogService.resolveIcon(rule));
        badge.setBadgeColor(rule.getBadgeColor());
        badge.setPosition(normalizePosition(rule.getDefaultPosition()));
        badge.setRuleId(rule.getId());
        badge.setRuleVersion(rule.getRuleVersion());
        badge.setGrantEventId(event.getEventId());
        Integer safeDays = normalizeGrantDays(days);
        badge.setExpireTime(ObjectUtils.isEmpty(safeDays) ? null : LocalDateTime.now().plusDays(safeDays));

        boolean persisted = isNew ? save(badge) : updateById(badge);
        if (!persisted) {
            throw new IllegalStateException("徽章归属写入失败");
        }
        log.info("event=badge_granted userId={} badgeType={} ruleVersion={} eventId={}",
                userId, rule.getBadgeType(), rule.getRuleVersion(), event.getEventId());
        return true;
    }

       
                       
       
    private BadgeGrantEvent grantEvent(String businessKey, Long userId, String badgeType,
                                       Integer ruleVersion, String action, String evidenceType,
                                       String evidenceId, String evidenceSummary, Long operatorId,
                                       String reason) {
        BadgeGrantEvent event = new BadgeGrantEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setBusinessKey(businessKey);
        event.setUserId(userId);
        event.setBadgeType(badgeType);
        event.setRuleVersion(ObjectUtils.isEmpty(ruleVersion) ? 0 : ruleVersion);
        event.setAction(action);
        event.setEvidenceType(evidenceType);
        event.setEvidenceId(evidenceId);
        event.setEvidenceSummary(evidenceSummary);
        event.setOperatorId(operatorId);
        event.setReason(reason);
        return event;
    }

       
                        
       
    private void requireStableRequest(String requestId, String reason) {
        if (ObjectUtils.isEmpty(requestId)
                || !requestId.matches("[A-Za-z0-9:_-]{8,128}")) {
            throw new IllegalArgumentException("徽章操作请求ID不合法");
        }
        if (ObjectUtils.isEmpty(reason) || reason.trim().length() > 500) {
            throw new IllegalArgumentException("徽章操作原因不能为空且最多500字");
        }
    }

       
                           
       
    private void requireOperator(Long operatorId) {
        if (ObjectUtils.isEmpty(operatorId) || operatorId <= 0) {
            throw new IllegalArgumentException("管理员身份无效");
        }
    }

       
                         
       
    private void requireConfiguredRuleServices() {
        if (ObjectUtils.isEmpty(badgeRuleCatalogService) || ObjectUtils.isEmpty(badgeGrantEventMapper)) {
            throw new IllegalStateException("徽章规则服务尚未完成迁移");
        }
    }

       
                 
       
    private String normalizeCode(String value, String fallback) {
        String normalized = ObjectUtils.isEmpty(value) ? fallback : value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_]{2,64}")) {
            throw new IllegalArgumentException("徽章事件代码不合法");
        }
        return normalized;
    }

       
                 
       
    private String normalizePosition(String position) {
        return Arrays.asList("avatar", "name", "both").contains(position) ? position : "name";
    }

       
                       
       
    private Integer normalizeGrantDays(Integer days) {
        if (ObjectUtils.isEmpty(days)) {
            return null;
        }
        if (days < 1 || days > 3650) {
            throw new IllegalArgumentException("徽章有效天数必须在1到3650之间");
        }
        return days;
    }

       
                      
       
    private String truncate(String value, int maxLength) {
        if (ObjectUtils.isEmpty(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

                                                         

       
                           
      
                         
       
    @Transactional(rollbackFor = Exception.class)
    public void calculateAndUpdateAchievementBadges(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return;
        }

        User user = userMapper.selectByIdForUpdate(userId);
        if (user == null) {
            return;
        }
        UserAccountStatusUtil.requireCanInteract(user, "计算成就徽章");

        if (ObjectUtils.isNotEmpty(badgeRuleCatalogService)
                && ObjectUtils.isNotEmpty(badgeGrantEventMapper)) {
            calculateFromPublishedRules(userId, user);
            return;
        }

                              
        long songCount = getDistinctSongCount(userId);
        if (songCount >= 500) {
            awardBadge(userId, "music_legend");
        }
        if (songCount >= 100) {
            awardBadge(userId, "music_master");
        }

                            
        int commentCount = getCommentCount(userId);
        if (commentCount >= 200) {
            awardBadge(userId, "comment_expert");
        }
        if (commentCount >= 50) {
            awardBadge(userId, "comment_master");
        }

                            
        int favoriteCount = getFavoriteCount(userId);
        if (favoriteCount >= 200) {
            awardBadge(userId, "super_collector");
        }
        if (favoriteCount >= 50) {
            awardBadge(userId, "collector");
        }

                            
        int fansCount = user.getFansCount() != null ? user.getFansCount() : 0;
        if (fansCount >= 100) {
            awardBadge(userId, "social_influencer");
        }
        if (fansCount >= 10) {
            awardBadge(userId, "social_star");
        }

                             
        int consecutiveDays = getConsecutiveCheckinDays(userId);
        if (consecutiveDays >= 30) {
            awardBadge(userId, "loyal_user");
        }
        if (consecutiveDays >= 7) {
            awardBadge(userId, "active_user");
        }

                              
        if (user.getCreateTime() != null) {
            long registeredDays = ChronoUnit.DAYS.between(user.getCreateTime(), LocalDateTime.now());
            if (registeredDays >= 365) {
                awardBadge(userId, "veteran");
            }
            if (registeredDays >= 30) {
                awardBadge(userId, "early_bird");
            }
        }

                   
        if (user.getIsCreator() != null && user.getIsCreator() == 1) {
            awardBadge(userId, "creator_basic");
            int workCount = getCreatorWorkCount(userId);
            if (workCount >= 100) {
                awardBadge(userId, "creator_legend");
            }
            if (workCount >= 50) {
                awardBadge(userId, "creator_master");
            }
            if (workCount >= 10) {
                awardBadge(userId, "creator_pro");
            }
        }

                       
        if (userActivityEnhancedService != null) {
            Integer activityScore = userActivityEnhancedService.getEnhancedActivityScore(userId);
            if (activityScore != null) {
                calculateActivityBadges(userId, activityScore);
            }
        }
    }

       
                             
       
    private void calculateFromPublishedRules(Long userId, User user) {
        List<BadgeRule> rules = badgeRuleCatalogService.listActiveRules(LocalDateTime.now());
        Map<String, Long> metrics = new HashMap<>();
        for (BadgeRule rule : rules) {
            Long value = metricValue(userId, user, rule.getTriggerType(), metrics);
            if (ObjectUtils.isEmpty(value) || ObjectUtils.isEmpty(rule.getThresholdValue())
                    || value < rule.getThresholdValue()) {
                continue;
            }
            String evidenceType = ObjectUtils.isEmpty(rule.getEvidenceType())
                    ? "counter_snapshot" : rule.getEvidenceType();
            grantRule(userId, rule, rule.getGrantDays(), evidenceType,
                    normalizeCode(rule.getTriggerType(), "metric"),
                    "value=" + value + ";threshold=" + rule.getThresholdValue(),
                    null, "自动规则计算");
        }
    }

       
                                 
       
    private Long metricValue(Long userId, User user, String triggerType, Map<String, Long> metrics) {
        String trigger = normalizeCode(triggerType, "manual");
        if ("manual".equals(trigger) || "system_event".equals(trigger)) {
            return null;
        }
        if (metrics.containsKey(trigger)) {
            return metrics.get(trigger);
        }
        Long value;
        switch (trigger) {
            case "distinct_song_count":
                value = getDistinctSongCount(userId);
                break;
            case "comment_count":
                value = (long) getCommentCount(userId);
                break;
            case "favorite_count":
                value = (long) getFavoriteCount(userId);
                break;
            case "fans_count":
                value = ObjectUtils.isEmpty(user.getFansCount()) ? 0L : user.getFansCount().longValue();
                break;
            case "consecutive_checkin_days":
                value = (long) getConsecutiveCheckinDays(userId);
                break;
            case "registered_days":
                value = ObjectUtils.isEmpty(user.getCreateTime()) ? 0L
                        : ChronoUnit.DAYS.between(user.getCreateTime(), LocalDateTime.now());
                break;
            case "creator_status":
                value = Integer.valueOf(1).equals(user.getIsCreator()) ? 1L : 0L;
                break;
            case "creator_work_count":
                value = (long) getCreatorWorkCount(userId);
                break;
            case "activity_score":
                Integer activityScore = ObjectUtils.isEmpty(userActivityEnhancedService)
                        ? null : userActivityEnhancedService.getEnhancedActivityScore(userId);
                value = ObjectUtils.isEmpty(activityScore) ? null : activityScore.longValue();
                break;
            default:
                log.warn("event=badge_rule_trigger_unsupported triggerType={}", trigger);
                value = null;
        }
        if (ObjectUtils.isNotEmpty(value)) {
            metrics.put(trigger, value);
        }
        return value;
    }

       
              
      
                         
                                 
       
    private void calculateActivityBadges(Long userId, Integer activityScore) {
                      
        if (activityScore >= 100) {
            awardBadge(userId, "active_legend");
        }
        if (activityScore >= 80) {
            awardBadge(userId, "active_master");
        }
        if (activityScore >= 60) {
            awardBadge(userId, "active_member");
        }
        if (activityScore >= 30) {
            awardBadge(userId, "active_normal");
        }
    }

       
                  
      
                            
                            
       
    private void awardBadge(Long userId, String badgeType) {
        BadgeConfig config = BADGE_CONFIGS.get(badgeType);
        if (config == null) {
            return;
        }

                    
        Long count = lambdaQuery()
            .eq(UserBadge::getUserId, userId)
            .eq(UserBadge::getBadgeType, badgeType)
            .and(w -> w.isNull(UserBadge::getExpireTime)
                .or()
                .gt(UserBadge::getExpireTime, LocalDateTime.now()))
            .count();

        if (count == null || count == 0) {
            addLegacyUserBadge(userId, badgeType, config.name, config.icon,
                config.color, "name", null);
            log.info("自动颁发徽章: userId={}, badgeType={}, badgeName={}", userId, badgeType, config.name);
        }
    }

                                                       

       
                    
      
                         
                   
       
    private long getDistinctSongCount(Long userId) {
        try {
            if (listenHistoryService != null) {
                Long count = listenHistoryService.getUserHistoryCount(userId);
                return count != null ? count : 0;
            }
        } catch (Exception e) {
                   
        }
        return 0;
    }

       
               
      
                         
                   
       
    private int getCommentCount(Long userId) {
        try {
            if (commentService != null) {
                LambdaQueryWrapper<com.haoran.music.entity.Comment> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(com.haoran.music.entity.Comment::getUserId, userId);
                return (int) commentService.count(wrapper);
            }
        } catch (Exception e) {
                   
        }
        return 0;
    }

       
               
      
                         
                   
       
    private int getFavoriteCount(Long userId) {
        try {
            if (favoriteHistoryService != null) {
                LambdaQueryWrapper<com.haoran.music.entity.FavoriteHistory> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(com.haoran.music.entity.FavoriteHistory::getUserId, userId);
                return (int) favoriteHistoryService.count(wrapper);
            }
        } catch (Exception e) {
                   
        }
        return 0;
    }

       
                 
      
                         
                     
       
    private int getConsecutiveCheckinDays(Long userId) {
        try {
            if (userCheckinService != null) {
                return userCheckinService.getContinuousDays(userId);
            }
        } catch (Exception e) {
                   
        }
        return 0;
    }

       
                
      
                         
                   
       
    private int getCreatorWorkCount(Long userId) {
        try {
            if (creatorWorkMapper != null) {
                LambdaQueryWrapper<CreatorWork> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(CreatorWork::getUserId, userId)
                       .eq(CreatorWork::getStatus, 1);                
                Long count = creatorWorkMapper.selectCount(wrapper);
                return count != null ? count.intValue() : 0;
            }
        } catch (Exception e) {
            log.error("获取创作者作品数量失败: userId={}", userId);
        }
        return 0;
    }

                                                          

       
                        
      
                     
       
    public List<UserBadgeVO> getAvailableBadges() {
        if (ObjectUtils.isNotEmpty(badgeRuleCatalogService)) {
            return badgeRuleCatalogService.listActiveRules(LocalDateTime.now()).stream()
                    .filter(rule -> "public".equals(rule.getVisibility()))
                    .map(this::convertRuleToVO)
                    .sorted(Comparator.comparing(UserBadgeVO::getRarityOrder))
                    .collect(Collectors.toList());
        }
        List<UserBadgeVO> result = new ArrayList<>();

        for (BadgeConfig config : BADGE_CONFIGS.values()) {
            UserBadgeVO vo = new UserBadgeVO();
            vo.setBadgeType(config.type);
            vo.setBadgeName(config.name);
            vo.setBadgeIcon(config.icon);
            vo.setBadgeColor(config.color);
            vo.setDescription(config.description);
            vo.setObtainMethod(config.obtainMethod);
            vo.setRarity(config.rarity);
            vo.setCategory(config.category);
            vo.setPosition("name");
            vo.setIsEquipped(false);
            RarityConfig rarityConfig = RARITY_CONFIGS.get(config.rarity);
            if (rarityConfig != null) {
                vo.setRarityOrder(rarityConfig.displayOrder);
            }
            result.add(vo);
        }

        return result.stream()
            .sorted(Comparator.comparing(UserBadgeVO::getRarityOrder))
            .collect(Collectors.toList());
    }

       
                             
       
    private UserBadgeVO convertRuleToVO(BadgeRule rule) {
        UserBadgeVO vo = new UserBadgeVO();
        vo.setBadgeType(rule.getBadgeType());
        vo.setBadgeName(rule.getBadgeName());
        vo.setBadgeIcon(badgeRuleCatalogService.resolveIcon(rule));
        vo.setBadgeColor(rule.getBadgeColor());
        vo.setDescription(rule.getDescription());
        vo.setObtainMethod(rule.getObtainMethod());
        vo.setRarity(rule.getRarity());
        vo.setCategory(rule.getCategory());
        vo.setPosition(normalizePosition(rule.getDefaultPosition()));
        vo.setIsEquipped(false);
        vo.setRarityOrder(rarityOrder(rule.getRarity()));
        return vo;
    }

       
               
      
                   
       
    public List<Map<String, Object>> getBadgeCategories() {
        List<Map<String, Object>> result = new ArrayList<>();
        List<UserBadgeVO> availableBadges = getAvailableBadges();

        for (BadgeCategory category : BADGE_CATEGORIES.values()) {
            Map<String, Object> categoryMap = new HashMap<>();
            categoryMap.put("code", category.code);
            categoryMap.put("name", category.name);
            categoryMap.put("description", category.description);
            categoryMap.put("displayOrder", category.displayOrder);

                          
            long count = availableBadges.stream()
                    .filter(badge -> category.code.equals(badge.getCategory()))
                    .count();
            categoryMap.put("badgeCount", count);

            result.add(categoryMap);
        }

        return result.stream()
            .sorted(Comparator.comparing(c -> (Integer) c.get("displayOrder")))
            .collect(Collectors.toList());
    }

       
                
      
                    
       
    public List<Map<String, Object>> getRarityConfigs() {
        List<Map<String, Object>> result = new ArrayList<>();

        for (RarityConfig rarity : RARITY_CONFIGS.values()) {
            Map<String, Object> rarityMap = new HashMap<>();
            rarityMap.put("code", rarity.code);
            rarityMap.put("name", rarity.name);
            rarityMap.put("borderColor", rarity.borderColor);
            rarityMap.put("displayOrder", rarity.displayOrder);
            result.add(rarityMap);
        }

        return result.stream()
            .sorted(Comparator.comparing(r -> (Integer) r.get("displayOrder")))
            .collect(Collectors.toList());
    }

       
                  
      
                           
                   
       
    public List<UserBadgeVO> getBadgesByCategory(String category) {
        if (ObjectUtils.isEmpty(category)) {
            return Collections.emptyList();
        }
        return getAvailableBadges().stream()
                .filter(badge -> category.equals(badge.getCategory()))
                .collect(Collectors.toList());
    }

       
                 
      
                         
                   
       
    public Map<String, Object> getUserBadgeStats(Long userId) {
        Map<String, Object> result = new HashMap<>();

        List<UserBadgeVO> badges = getUserBadgeVOList(userId);

             
        result.put("totalBadges", badges.size());

                
        Map<String, Long> categoryStats = new HashMap<>();
        for (BadgeCategory category : BADGE_CATEGORIES.values()) {
            long count = badges.stream()
                .filter(b -> category.code.equals(b.getCategory()))
                .count();
            categoryStats.put(category.code, count);
        }
        result.put("categoryStats", categoryStats);

                 
        Map<String, Long> rarityStats = new HashMap<>();
        for (String rarity : Arrays.asList("common", "rare", "epic", "legendary")) {
            long count = badges.stream()
                .filter(b -> rarity.equals(b.getRarity()))
                .count();
            rarityStats.put(rarity, count);
        }
        result.put("rarityStats", rarityStats);

              
        int totalConfigBadges = ObjectUtils.isNotEmpty(badgeRuleCatalogService)
                ? getAvailableBadges().size() : BADGE_CONFIGS.size();
        double completionRate = totalConfigBadges == 0
                ? 0D : (double) badges.size() / totalConfigBadges * 100;
        result.put("completionRate", String.format("%.1f", completionRate));
        result.put("totalConfigBadges", totalConfigBadges);

        return result;
    }

       
               
      
                          
                          
                          
       
    public void setBadgeEquip(Long userId, Long badgeId, Boolean equip) {
        UserBadge badge = getById(badgeId);
        if (badge == null || !badge.getUserId().equals(userId)) {
            throw new RuntimeException("徽章不存在");
        }
        if (Boolean.TRUE.equals(equip)) {
            UserAccountStatusUtil.requireCanInteract(userMapper.selectById(userId), "佩戴徽章");
        }
        badge.setIsEquipped(Boolean.TRUE.equals(equip) ? 1 : 0);
        if (!updateById(badge)) {
            throw new IllegalStateException("徽章佩戴状态更新失败");
        }
    }
}
