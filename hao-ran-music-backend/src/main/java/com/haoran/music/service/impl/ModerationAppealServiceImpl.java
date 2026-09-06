


package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.config.ModerationWorkflowConfig;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.entity.Moderation;
import com.haoran.music.entity.ModerationAppeal;
import com.haoran.music.entity.ModerationRecord;
import com.haoran.music.mapper.ModerationAppealMapper;
import com.haoran.music.mapper.ModerationMapper;
import com.haoran.music.mapper.ModerationRecordMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.AuditLogService;
import com.haoran.music.service.ModerationAppealService;
import com.haoran.music.service.PrivateAttachmentService;
import com.haoran.music.service.PermissionService;
import com.haoran.music.enums.PrivateAttachmentPurpose;
import com.haoran.music.common.util.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Slf4j
@Service
public class ModerationAppealServiceImpl extends ServiceImpl<ModerationAppealMapper, ModerationAppeal>
        implements ModerationAppealService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_APPROVED = 1;
    private static final int STATUS_REJECTED = 2;

    @Autowired
    private ModerationRecordMapper moderationRecordMapper;

    @Autowired
    private ModerationMapper moderationMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private ModerationWorkflowConfig moderationWorkflowConfig;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PrivateAttachmentService privateAttachmentService;

    @Autowired
    private PermissionService permissionService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitAppeal(Long moderationId, Long userId, String appealReason,
                             String appealContent, String attachments) {
        return submitAppealInternal(moderationId, userId, appealReason, appealContent,
                attachments, null);
    }











    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitAppealWithAssets(Long moderationId, Long userId, String appealReason,
                                       String appealContent, List<Long> attachmentAssetIds) {
        return submitAppealInternal(moderationId, userId, appealReason, appealContent,
                null, attachmentAssetIds);
    }












    private Long submitAppealInternal(Long moderationId, Long userId, String appealReason,
                                      String appealContent, String attachments,
                                      List<Long> attachmentAssetIds) {
        if (moderationId == null || userId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "审核记录和用户不能为空");
        }
        if (isBlank(appealReason)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "申诉原因不能为空");
        }
        validateAttachments(attachments);

        ModerationRecord record = moderationRecordMapper.selectById(moderationId);
        if (record != null) {
            validateNewRecordAppeal(record, userId);
        } else {
            validateLegacyModerationAppeal(moderationId, userId);
        }

        if (userMapper.selectByIdForUpdate(userId) == null) {
            throw new BusinessException(ResultCode.DATA_NOT_EXIST, "用户不存在");
        }
        if (!isUnderAppealLimit(userId, moderationId)) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "申诉次数已达上限");
        }

        ModerationAppeal appeal = new ModerationAppeal();
        appeal.setModerationId(moderationId);
        appeal.setUserId(userId);
        appeal.setAppealReason(appealReason.trim());
        appeal.setAppealContent(appealContent);
        appeal.setAttachments(attachments);
        appeal.setStatus(STATUS_PENDING);
        appeal.setSubmitTime(LocalDateTime.now());
        save(appeal);

        if (ObjectUtils.isNotEmpty(attachmentAssetIds)) {
            privateAttachmentService.bindAssets(userId,
                    PrivateAttachmentPurpose.MODERATION_APPEAL_EVIDENCE.name(), attachmentAssetIds,
                    PrivateAttachmentPurpose.MODERATION_APPEAL_EVIDENCE.getTargetType(), appeal.getId());
        }

        increaseAppealCount(userId, moderationId);
        logAudit(moderationId, "submit_appeal", null, "appeal_pending", userId, appealReason, null);
        log.info("Moderation appeal submitted: appealId={}, moderationId={}, userId={}",
                appeal.getId(), moderationId, userId);
        return appeal.getId();
    }








    @Override
    public Map<String, Object> getAppealDetail(Long appealId, Long viewerId) {
        ModerationAppeal appeal = getById(appealId);
        if (ObjectUtils.isEmpty(appeal)) {
            throw new BusinessException(ResultCode.DATA_NOT_EXIST, "审核申诉不存在");
        }
        boolean owner = viewerId != null && viewerId.equals(appeal.getUserId());
        boolean reviewer = permissionService.isModerator(viewerId);
        if (!owner && !reviewer) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看此审核申诉");
        }
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("appealId", appeal.getId());
        result.put("moderationId", appeal.getModerationId());
        result.put("appealReason", appeal.getAppealReason());
        result.put("appealContent", appeal.getAppealContent());
        result.put("attachmentUrls", appeal.getAttachments());
        result.put("attachmentAssetIds", privateAttachmentService.listTargetAssetIds(
                PrivateAttachmentPurpose.MODERATION_APPEAL_EVIDENCE.getTargetType(), appeal.getId()));
        result.put("status", appeal.getStatus());
        result.put("submitTime", appeal.getSubmitTime());
        if (reviewer) {
            result.put("userId", appeal.getUserId());
            result.put("reviewerId", appeal.getReviewerId());
            result.put("decisionReason", appeal.getDecisionReason());
            result.put("processTime", appeal.getProcessTime());
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processAppeal(Long appealId, Long reviewerId, Integer decision, String decisionReason) {
        if (appealId == null || reviewerId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "申诉ID和审核员不能为空");
        }
        if (!Integer.valueOf(STATUS_APPROVED).equals(decision)
                && !Integer.valueOf(STATUS_REJECTED).equals(decision)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "申诉处理结果不合法");
        }

        ModerationAppeal appeal = getById(appealId);
        if (appeal == null || !Integer.valueOf(STATUS_PENDING).equals(appeal.getStatus())) {
            throw new BusinessException("申诉记录状态异常");
        }

        if (baseMapper.processPending(appealId, reviewerId, decision,
                decisionReason, LocalDateTime.now()) != 1) {
            throw new BusinessException("申诉已被其他操作处理");
        }

        if (Integer.valueOf(STATUS_APPROVED).equals(decision)) {
            restoreModerationAfterAppeal(appeal.getModerationId());
            logAudit(appeal.getModerationId(), "approve_appeal", "rejected", "pending",
                    reviewerId, decisionReason, null);
            log.info("Moderation appeal approved: appealId={}, moderationId={}", appealId, appeal.getModerationId());
        } else {
            logAudit(appeal.getModerationId(), "reject_appeal", "appeal_pending", "appeal_rejected",
                    reviewerId, decisionReason, null);
            log.info("Moderation appeal rejected: appealId={}, moderationId={}, reason={}",
                    appealId, appeal.getModerationId(), decisionReason);
        }
        privateAttachmentService.releaseTargetReferences(
                PrivateAttachmentPurpose.MODERATION_APPEAL_EVIDENCE.getTargetType(), appealId);
    }

    @Override
    public Boolean canAppeal(Long userId, Long moderationId) {
        if (userId == null || moderationId == null || !isUnderAppealLimit(userId, moderationId)) {
            return Boolean.FALSE;
        }

        ModerationRecord record = moderationRecordMapper.selectById(moderationId);
        if (record != null) {
            return Boolean.valueOf(isNewRecordAppealable(record, userId));
        }

        Moderation moderation = moderationMapper.selectById(moderationId);
        return Boolean.valueOf(isLegacyModerationAppealable(moderation, userId));
    }

    private void validateNewRecordAppeal(ModerationRecord record, Long userId) {
        if (!isNewRecordAppealable(record, userId)) {
            throw new BusinessException("只有本人被拒绝且仍在期限内的审核记录可以申诉");
        }
    }

    private boolean isNewRecordAppealable(ModerationRecord record, Long userId) {
        if (record == null || userId == null) {
            return false;
        }
        if (!userId.equals(record.getSubmitterId())) {
            return false;
        }
        if (!"rejected".equals(record.getStatus())) {
            return false;
        }
        return isWithinAppealWindow(record.getReviewTime());
    }

    private void validateLegacyModerationAppeal(Long moderationId, Long userId) {
        Moderation moderation = moderationMapper.selectById(moderationId);
        if (!isLegacyModerationAppealable(moderation, userId)) {
            throw new BusinessException("只有本人被拒绝且仍在期限内的审核记录可以申诉");
        }
    }

    private boolean isLegacyModerationAppealable(Moderation moderation, Long userId) {
        if (moderation == null || userId == null) {
            return false;
        }
        if (!userId.equals(moderation.getSubmitterId())) {
            return false;
        }
        if (!Integer.valueOf(STATUS_REJECTED).equals(moderation.getStatus())) {
            return false;
        }
        return isWithinAppealWindow(moderation.getReviewTime());
    }

    private boolean isWithinAppealWindow(LocalDateTime reviewTime) {
        if (reviewTime == null) {
            return true;
        }
        return !LocalDateTime.now().isAfter(reviewTime.plusDays(moderationWorkflowConfig.getAppeal().getWindowDays()));
    }

    private void restoreModerationAfterAppeal(Long moderationId) {
        if (restoreNewModerationRecord(moderationId)) {
            return;
        }
        restoreLegacyModeration(moderationId);
    }

    private boolean restoreNewModerationRecord(Long moderationId) {
        ModerationRecord record = moderationRecordMapper.selectById(moderationId);
        if (record == null) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<ModerationRecord> updateWrapper = new LambdaUpdateWrapper<ModerationRecord>();
        updateWrapper.eq(ModerationRecord::getId, moderationId)
                .eq(ModerationRecord::getStatus, "rejected")
                .set(ModerationRecord::getStatus, "pending")
                .set(ModerationRecord::getAssignedModeratorId, null)
                .set(ModerationRecord::getAssignedTime, null)
                .set(ModerationRecord::getModeratorOnlineStatus, null)
                .set(ModerationRecord::getReviewerId, null)
                .set(ModerationRecord::getReviewTime, null)
                .set(ModerationRecord::getReviewResult, null)
                .set(ModerationRecord::getReviewReason, null)
                .set(ModerationRecord::getUpdateTime, now);
        if (moderationRecordMapper.update(null, updateWrapper) != 1) {
            throw new BusinessException("原审核记录状态已变化，申诉处理已回滚");
        }
        return true;
    }

    private void restoreLegacyModeration(Long moderationId) {
        Moderation moderation = moderationMapper.selectById(moderationId);
        if (moderation == null) {
            throw new BusinessException(ResultCode.DATA_NOT_EXIST);
        }
        LambdaUpdateWrapper<Moderation> updateWrapper = new LambdaUpdateWrapper<Moderation>();
        updateWrapper.eq(Moderation::getId, moderationId)
                .eq(Moderation::getStatus, STATUS_REJECTED)
                .set(Moderation::getStatus, STATUS_PENDING)
                .set(Moderation::getReviewReason, null)
                .set(Moderation::getReviewTime, null)
                .set(Moderation::getUpdateTime, LocalDateTime.now());
        if (moderationMapper.update(null, updateWrapper) != 1) {
            throw new BusinessException("原审核记录状态已变化，申诉处理已回滚");
        }
    }

    private boolean isUnderAppealLimit(Long userId, Long moderationId) {
        return getAppealCount(userId, moderationId) < moderationWorkflowConfig.getAppeal().getMaxCount();
    }

    private int getAppealCount(Long userId, Long moderationId) {
        long dbCount = count(new LambdaQueryWrapper<ModerationAppeal>()
                .eq(ModerationAppeal::getUserId, userId)
                .eq(ModerationAppeal::getModerationId, moderationId));
        int redisCount = getRedisAppealCount(userId, moderationId);
        return (int) Math.max(dbCount, redisCount);
    }

    private int getRedisAppealCount(Long userId, Long moderationId) {
        try {
            Object value = redisTemplate.opsForValue().get(getAppealCountKey(userId, moderationId));
            if (value == null) {
                return 0;
            }
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            log.debug("Failed to read moderation appeal count from Redis: userId={}, moderationId={}, error={}",
                    userId, moderationId, e.getMessage());
            return 0;
        }
    }

    private void increaseAppealCount(Long userId, Long moderationId) {
        try {
            String key = getAppealCountKey(userId, moderationId);
            redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, moderationWorkflowConfig.getAppeal().getCountCacheDays(), TimeUnit.DAYS);
        } catch (Exception e) {
            log.debug("Failed to update moderation appeal count in Redis: userId={}, moderationId={}, error={}",
                    userId, moderationId, e.getMessage());
        }
    }

    private String getAppealCountKey(Long userId, Long moderationId) {
        return moderationWorkflowConfig.getAppeal().getCountPrefix() + userId + ":" + moderationId;
    }

    private void logAudit(Long moderationId, String action, String oldStatus, String newStatus,
                          Long operatorId, String comment, Integer durationSeconds) {
        auditLogService.logAudit(moderationId, "moderation_appeal", action,
                oldStatus, newStatus, operatorId, null, comment,
                null, null, durationSeconds, Boolean.TRUE);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void validateAttachments(String attachments) {
        if (attachments == null) {
            return;
        }
        if (attachments.length() > 4096) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "附件地址总长度不能超过4096个字符");
        }
        String normalized = attachments.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("data:") || normalized.contains("file:")
                || normalized.contains("..\\") || normalized.contains("../")
                || attachments.indexOf('\r') >= 0 || attachments.indexOf('\n') >= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "附件地址不合法");
        }
    }
}
