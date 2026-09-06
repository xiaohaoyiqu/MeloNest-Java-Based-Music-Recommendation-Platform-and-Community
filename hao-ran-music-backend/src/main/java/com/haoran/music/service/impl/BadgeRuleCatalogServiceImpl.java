package com.haoran.music.service.impl;

import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.entity.BadgeRule;
import com.haoran.music.entity.MediaAsset;
import com.haoran.music.mapper.BadgeRuleMapper;
import com.haoran.music.mapper.MediaAssetMapper;
import com.haoran.music.service.BadgeRuleCatalogService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

   
                   
  
                      
   
@Service
public class BadgeRuleCatalogServiceImpl implements BadgeRuleCatalogService {

    private static final int MAX_REPLACEMENT_DEPTH = 5;
    private static final String DEFAULT_ICON = "🏅";

    private final BadgeRuleMapper badgeRuleMapper;
    private final MediaAssetMapper mediaAssetMapper;

    public BadgeRuleCatalogServiceImpl(BadgeRuleMapper badgeRuleMapper,
                                       MediaAssetMapper mediaAssetMapper) {
        this.badgeRuleMapper = badgeRuleMapper;
        this.mediaAssetMapper = mediaAssetMapper;
    }

    @Override
    public BadgeRule findActiveRule(String badgeType, LocalDateTime now) {
        if (ObjectUtils.isEmpty(badgeType)) {
            return null;
        }
        return badgeRuleMapper.selectActiveByType(badgeType.trim(), safeNow(now));
    }

    @Override
    public List<BadgeRule> listActiveRules(LocalDateTime now) {
        List<BadgeRule> rules = badgeRuleMapper.selectActiveRules(safeNow(now));
        return ObjectUtils.isEmpty(rules) ? Collections.emptyList() : rules;
    }

    @Override
    public BadgeRule resolveDisplayRule(Long ruleId) {
        if (ObjectUtils.isEmpty(ruleId)) {
            return null;
        }
        BadgeRule current = badgeRuleMapper.selectById(ruleId);
        String badgeType = ObjectUtils.isEmpty(current) ? null : current.getBadgeType();
        Set<Long> visited = new HashSet<>();
        for (int depth = 0; depth < MAX_REPLACEMENT_DEPTH && ObjectUtils.isNotEmpty(current); depth++) {
            if (!visited.add(current.getId())) {
                return current;
            }
            BadgeRule replacement = badgeRuleMapper.selectPublishedReplacement(current.getId());
            if (ObjectUtils.isEmpty(replacement)
                    || !ObjectUtils.equals(badgeType, replacement.getBadgeType())) {
                return current;
            }
            current = replacement;
        }
        return current;
    }

    @Override
    public String resolveIcon(BadgeRule rule) {
        if (ObjectUtils.isEmpty(rule)) {
            return DEFAULT_ICON;
        }
        if (ObjectUtils.isNotEmpty(rule.getAssetId())) {
            MediaAsset asset = mediaAssetMapper.selectById(rule.getAssetId());
            if (isPublicBadgeAsset(asset)) {
                return asset.getPublicUrl();
            }
        }
        return ObjectUtils.isEmpty(rule.getIconFallback()) ? DEFAULT_ICON : rule.getIconFallback();
    }

       
                          
       
    private boolean isPublicBadgeAsset(MediaAsset asset) {
        return ObjectUtils.isNotEmpty(asset)
                && MediaAsset.STATUS_ACTIVE.equals(asset.getStatus())
                && MediaAsset.SCAN_STATUS_CLEAN.equals(asset.getScanStatus())
                && MediaAsset.VISIBILITY_PUBLIC.equals(asset.getVisibility())
                && "image".equals(asset.getMediaType())
                && ObjectUtils.isNotEmpty(asset.getPublicUrl());
    }

       
                     
       
    private LocalDateTime safeNow(LocalDateTime now) {
        return ObjectUtils.isEmpty(now) ? LocalDateTime.now() : now;
    }
}
