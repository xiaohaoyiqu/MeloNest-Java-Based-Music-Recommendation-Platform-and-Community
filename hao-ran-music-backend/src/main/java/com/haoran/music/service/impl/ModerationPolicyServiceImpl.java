package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.entity.Moderation;
import com.haoran.music.entity.ModerationPolicy;
import com.haoran.music.mapper.ModerationMapper;
import com.haoran.music.mapper.ModerationPolicyMapper;
import com.haoran.music.service.ModerationPolicyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;





@Slf4j
@Service
public class ModerationPolicyServiceImpl extends ServiceImpl<ModerationPolicyMapper, ModerationPolicy>
        implements ModerationPolicyService {

    private static final Set<String> SUPPORTED_SCOPES = new HashSet<>(
            Arrays.asList("all", "creator", "lyric", "song", "album"));

    @Autowired
    private ModerationPolicyMapper policyMapper;

    @Autowired
    private ModerationMapper moderationMapper;




    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePolicy(String policyCode, String policyName, String policyContent,
                             String affectScope, Boolean reauditRequired) {
        String normalizedCode = requireText(policyCode, 64, "规则编码");
        if (!normalizedCode.matches("[A-Za-z0-9_.:-]+")) {
            throw new BusinessException(400, "规则编码格式不正确");
        }
        String normalizedName = requireText(policyName, 100, "规则名称");
        String normalizedContent = requireText(policyContent, 10000, "规则内容");
        String normalizedScope = requireText(affectScope, 20, "影响范围").toLowerCase(Locale.ROOT);
        if (!SUPPORTED_SCOPES.contains(normalizedScope)) {
            throw new BusinessException(400, "影响范围不受支持");
        }
        if (reauditRequired == null) {
            throw new BusinessException(400, "是否复审不能为空");
        }
        log.info("event=moderation_policy_update_started policyCode={} affectScope={} reauditRequired={}",
                normalizedCode, normalizedScope, reauditRequired);


        boolean shouldReaudit = Boolean.TRUE.equals(reauditRequired);

        ModerationPolicy policy = new ModerationPolicy();
        policy.setPolicyCode(normalizedCode);
        policy.setPolicyName(normalizedName);
        policy.setPolicyContent(normalizedContent);
        policy.setAffectScope(normalizedScope);
        policy.setReauditRequired(shouldReaudit ? 1 : 0);
        policy.setEffectiveTime(LocalDateTime.now());
        if (policyMapper.insert(policy) != 1) {
            throw new BusinessException("审核规则写入失败");
        }


        if (shouldReaudit) {
            triggerReaudit(policy);
        }

        log.info("event=moderation_policy_update_completed policyId={} policyCode={} affectScope={}",
                policy.getId(), normalizedCode, normalizedScope);
    }




    @Override
    @Transactional(rollbackFor = Exception.class)
    public void triggerReaudit(ModerationPolicy policy) {
        if (policy == null || policy.getId() == null) {
            throw new BusinessException("审核规则事实不存在");
        }
        String scope = requireText(policy.getAffectScope(), 20, "影响范围").toLowerCase(Locale.ROOT);
        if (!SUPPORTED_SCOPES.contains(scope)) {
            throw new BusinessException(400, "影响范围不受支持");
        }

        LambdaUpdateWrapper<Moderation> update = new LambdaUpdateWrapper<Moderation>()
                .eq(Moderation::getStatus, 1);
        if (!"all".equals(scope)) {
            update.eq(Moderation::getContentType,
                    "creator".equals(scope) ? "creator_work" : scope);
        }
        update.set(Moderation::getStatus, 3)
                .set(Moderation::getPolicyId, policy.getId());
        int affected = moderationMapper.update(null, update);
        log.info("event=moderation_policy_reaudit_marked policyId={} affectScope={} affected={}",
                policy.getId(), scope, affected);
    }

    private String requireText(String value, int maxLength, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessException(400, fieldName + "不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new BusinessException(400, fieldName + "长度超出限制");
        }
        return normalized;
    }
}
