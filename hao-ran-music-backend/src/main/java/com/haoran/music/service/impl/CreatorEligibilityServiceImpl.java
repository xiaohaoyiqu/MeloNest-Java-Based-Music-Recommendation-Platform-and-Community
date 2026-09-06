


package com.haoran.music.service.impl;

import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.User;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.AuditLogService;
import com.haoran.music.service.CreatorEligibilityService;
import com.haoran.music.service.CreatorEligibilityOutboxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Service
public class CreatorEligibilityServiceImpl implements CreatorEligibilityService {

    private static final Set<String> CREATOR_TYPES = new HashSet<>(Arrays.asList(
            "independent", "signed", "singer", "producer", "lyricist"));

    private final UserMapper userMapper;

    @Resource
    private CreatorEligibilityProjectionService projectionService;

    @Resource
    private CreatorEligibilityOutboxService outboxService;

    @Resource
    private AuditLogService auditLogService;

    public CreatorEligibilityServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public boolean isEligible(Long userId) {
        if (userId == null) {
            return false;
        }
        User user = userMapper.selectById(userId);
        return UserAccountStatusUtil.canInteract(user)
                && Integer.valueOf(1).equals(user.getIsCreator())
                && "active".equalsIgnoreCase(user.getCreatorStatus());
    }

    @Override
    public void requireEligible(Long userId, String action) {
        if (!isEligible(userId)) {
            String operation = action == null || action.trim().isEmpty() ? "执行创作者操作" : action;
            throw new BusinessException(ResultCode.FORBIDDEN,
                    "当前账号不具备有效创作者资格，无法" + operation);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User activate(Long userId, String creatorType, Long operatorId, String reason, BigDecimal feeRate) {
        User user = requireLockedUser(userId);
        UserAccountStatusUtil.requireCanInteract(user, "获得创作者资格");
        String normalizedType = normalizeCreatorType(creatorType);
        String oldStatus = user.getCreatorStatus();

        user.setIsCreator(1);
        user.setCreatorStatus("active");
        user.setCreatorType(normalizedType);
        user.setCreatorApplyTime(LocalDateTime.now());
        if (feeRate != null) {
            user.setFeeRate(feeRate);
        }
        advanceVersion(user);
        updateAuthoritativeUser(user);
        projectionService.synchronize(user);
        auditLogService.logAudit(userId, "creator_eligibility", "activate",
                oldStatus, "active", operatorId, null, reason,
                null, null, null, true);
        outboxService.record(user, "activated", oldStatus, operatorId, reason);
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User changeStatus(Long userId, String status, Long operatorId, String reason) {
        User user = requireLockedUser(userId);
        if (!Integer.valueOf(1).equals(user.getIsCreator())) {
            throw new BusinessException("用户当前不具备创作者身份");
        }
        String normalizedStatus = normalizeStatus(status);
        if ("active".equals(normalizedStatus)) {
            UserAccountStatusUtil.requireCanInteract(user, "恢复创作者资格");
        }
        String oldStatus = user.getCreatorStatus();
        user.setCreatorStatus(normalizedStatus);
        user.setCreatorNote(reason);
        advanceVersion(user);
        updateAuthoritativeUser(user);
        projectionService.synchronize(user);
        auditLogService.logAudit(userId, "creator_eligibility", "status_change",
                oldStatus, normalizedStatus, operatorId, null, reason,
                null, null, null, true);
        outboxService.record(user, "status_changed", oldStatus, operatorId, reason);
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User remove(Long userId, Long operatorId, String reason) {
        User user = requireLockedUser(userId);
        String oldStatus = user.getCreatorStatus();
        user.setIsCreator(0);
        user.setCreatorStatus("removed");
        user.setCreatorNote(reason);
        advanceVersion(user);
        updateAuthoritativeUser(user);
        projectionService.synchronize(user);
        auditLogService.logAudit(userId, "creator_eligibility", "remove",
                oldStatus, "removed", operatorId, null, reason,
                null, null, null, true);
        outboxService.record(user, "removed", oldStatus, operatorId, reason);
        return user;
    }

    private User requireLockedUser(Long userId) {
        if (userId == null) {
            throw new BusinessException("用户ID不能为空");
        }
        User user = userMapper.selectByIdForUpdate(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    private void updateAuthoritativeUser(User user) {
        if (userMapper.updateById(user) != 1) {
            throw new BusinessException("创作者资格状态已变化，请重试");
        }
    }

    private void advanceVersion(User user) {
        long current = user.getCreatorEligibilityVersion() == null
                ? 0L : user.getCreatorEligibilityVersion();
        user.setCreatorEligibilityVersion(current + 1L);
    }

    private String normalizeCreatorType(String creatorType) {
        String normalized = creatorType == null ? "independent"
                : creatorType.trim().toLowerCase(Locale.ROOT);
        if (!CREATOR_TYPES.contains(normalized)) {
            throw new BusinessException("不支持的创作者类型");
        }
        return normalized;
    }

    private String normalizeStatus(String status) {
        String normalized = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
        if ("approved".equals(normalized)) {
            normalized = "active";
        }
        if (!"active".equals(normalized) && !"suspended".equals(normalized)) {
            throw new BusinessException("创作者状态仅支持active或suspended；移除身份请使用移除接口");
        }
        return normalized;
    }

}
