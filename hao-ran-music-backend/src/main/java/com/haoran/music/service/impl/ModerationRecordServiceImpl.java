   
                      
   
package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.constant.ModerationConstants;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.entity.ModerationRecord;
import com.haoran.music.mapper.ModerationRecordMapper;
import com.haoran.music.service.AuditLogService;
import com.haoran.music.service.EmojiService;
import com.haoran.music.service.DecorationCreatorService;
import com.haoran.music.service.ModerationAssignmentService;
import com.haoran.music.service.ModerationRecordService;
import com.haoran.music.service.OnlineStatusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ModerationRecordServiceImpl extends ServiceImpl<ModerationRecordMapper, ModerationRecord>
        implements ModerationRecordService {

    private static final String AUDIT_RECORD_TYPE = "moderation_record";

    @Autowired
    private ModerationAssignmentService assignmentService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private OnlineStatusService onlineStatusService;

    @Autowired
    private EmojiService emojiService;

    @Autowired
    private DecorationCreatorService decorationCreatorService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRecord(String targetType, Long targetId, Long submitterId, String submitterSource, Integer priority) {
        String normalizedTargetType = ModerationConstants.normalizeTargetType(targetType);
        String normalizedSubmitterSource = ModerationConstants.normalizeSubmitterSource(submitterSource);

        if (targetId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Moderation target id is required");
        }
        if (!ModerationConstants.isSupportedTargetType(normalizedTargetType)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Unsupported moderation target type: " + targetType);
        }
        if (requiresOwnerSubmission(normalizedTargetType)) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    "创意工坊作品只能由作者从作品柜提交审核");
        }
        if (!ModerationConstants.isSupportedSubmitterSource(normalizedSubmitterSource)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Unsupported moderation submitter source: " + submitterSource);
        }

        ModerationRecord record = new ModerationRecord();
        record.setTargetType(normalizedTargetType);
        record.setTargetId(targetId);
        record.setSubmitterId(submitterId);
        record.setSubmitterSource(normalizedSubmitterSource);
        record.setPriority(ModerationConstants.normalizePriority(priority));
        record.setStatus(ModerationConstants.STATUS_PENDING);
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());

        save(record);
        logModerationAction(record, "create", null, record.getStatus(), submitterId, null, null);

        log.info("Moderation record created: recordId={}, targetType={}, targetId={}, submitterId={}, source={}",
                record.getId(), normalizedTargetType, targetId, submitterId, normalizedSubmitterSource);
        return record.getId();
    }

    private boolean requiresOwnerSubmission(String targetType) {
        return "emoji_package".equals(targetType) || "decoration".equals(targetType);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long assignModerator(Long recordId) {
        ModerationRecord record = getRequiredRecord(recordId);
        if (record.getAssignedModeratorId() != null) {
            return record.getAssignedModeratorId();
        }
        if (!ModerationConstants.STATUS_PENDING.equals(record.getStatus())) {
            throw new BusinessException("Moderation record status does not allow assignment");
        }

        if (!assignmentService.assignModeration(recordId)) {
            throw new BusinessException("Failed to assign moderation task");
        }
        ModerationRecord assigned = getRequiredRecord(recordId);
        Long moderatorId = assigned.getAssignedModeratorId();
        logModerationAction(assigned, "assign", record.getStatus(), assigned.getStatus(), moderatorId, null, null);

        log.info("Moderation assigned automatically: recordId={}, moderatorId={}, onlineStatus={}",
                recordId, moderatorId, assigned.getModeratorOnlineStatus());
        return moderatorId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long assignModerator(Long recordId, Long moderatorId) {
        boolean assigned = assignmentService.assignModeration(recordId, moderatorId);
        if (!assigned) {
            throw new BusinessException("Failed to assign moderation task");
        }

        ModerationRecord record = getById(recordId);
        if (record != null) {
            logModerationAction(record, "assign", null, record.getStatus(), moderatorId, null, null);
        }
        return moderatorId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeReview(Long recordId, Long reviewerId, String reviewResult, String reviewReason) {
        ModerationRecord record = getRequiredRecord(recordId);
        if (reviewerId == null || !reviewerId.equals(record.getAssignedModeratorId())) {
            throw new BusinessException("Only the assigned moderator can complete this task");
        }
        if (!ModerationConstants.STATUS_IN_PROGRESS.equals(record.getStatus())) {
            throw new BusinessException("Moderation record status does not allow completing review");
        }
        if (!ModerationConstants.STATUS_APPROVED.equals(reviewResult) && !ModerationConstants.STATUS_REJECTED.equals(reviewResult)) {
            throw new BusinessException("Unsupported review result");
        }

        String oldStatus = record.getStatus();
        LocalDateTime reviewTime = LocalDateTime.now();
        Integer durationSeconds = calculateDurationSeconds(record.getAssignedTime(), reviewTime);

        if (getBaseMapper().completeActiveTarget(
                recordId, ModerationConstants.STATUS_IN_PROGRESS, reviewerId,
                reviewResult, reviewReason, reviewTime) != 1) {
            throw new BusinessException("Moderation record was completed or reassigned by another reviewer");
        }

        applyTargetReview(record, reviewerId, reviewResult, reviewReason);

        assignmentService.completeTask(reviewerId);
        record.setReviewerId(reviewerId);
        record.setReviewTime(reviewTime);
        record.setReviewResult(reviewResult);
        record.setReviewReason(reviewReason);
        record.setStatus(reviewResult);
        record.setUpdateTime(reviewTime);
        logModerationAction(record, ModerationConstants.STATUS_APPROVED.equals(reviewResult) ? "approve" : "reject",
                oldStatus, record.getStatus(), reviewerId, reviewReason, durationSeconds);

        log.info("Moderation completed: recordId={}, reviewerId={}, result={}", recordId, reviewerId, reviewResult);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeTargetReview(String targetType, Long targetId, Long reviewerId,
                                     String reviewResult, String reviewReason) {
        String normalizedTargetType = ModerationConstants.normalizeTargetType(targetType);
        if (targetId == null || reviewerId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Moderation target and reviewer are required");
        }
        if (!ModerationConstants.STATUS_APPROVED.equals(reviewResult)
                && !ModerationConstants.STATUS_REJECTED.equals(reviewResult)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Unsupported review result");
        }

        ModerationRecord record = getBaseMapper().selectLatestActiveTarget(normalizedTargetType, targetId);
        if (record == null) {
            throw new BusinessException("Active moderation record does not exist");
        }

        String oldStatus = record.getStatus();
        LocalDateTime reviewTime = LocalDateTime.now();
        if (getBaseMapper().completeActiveTarget(
                record.getId(), record.getStatus(), reviewerId, reviewResult, reviewReason, reviewTime) != 1) {
            throw new BusinessException("Moderation record was completed by another reviewer");
        }

        applyTargetReview(record, reviewerId, reviewResult, reviewReason);

        if (record.getAssignedModeratorId() != null) {
            assignmentService.completeTask(reviewerId);
        }
        Integer durationSeconds = calculateDurationSeconds(record.getAssignedTime(), reviewTime);
        record.setReviewerId(reviewerId);
        record.setReviewTime(reviewTime);
        record.setReviewResult(reviewResult);
        record.setReviewReason(reviewReason);
        record.setStatus(reviewResult);
        logModerationAction(record,
                ModerationConstants.STATUS_APPROVED.equals(reviewResult) ? "approve" : "reject",
                oldStatus, reviewResult, reviewerId, reviewReason, durationSeconds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startReview(Long recordId, Long reviewerId) {
        ModerationRecord record = getRequiredRecord(recordId);
        if (reviewerId == null || !reviewerId.equals(record.getAssignedModeratorId())) {
            throw new BusinessException("Only the assigned moderator can start this task");
        }
        if (ModerationConstants.STATUS_IN_PROGRESS.equals(record.getStatus())) {
            return;
        }
        if (!ModerationConstants.STATUS_PENDING.equals(record.getStatus())) {
            throw new BusinessException("Moderation record status does not allow starting review");
        }

        String oldStatus = record.getStatus();
        record.setStatus(ModerationConstants.STATUS_IN_PROGRESS);
        record.setUpdateTime(LocalDateTime.now());
        updateById(record);
        logModerationAction(record, "start", oldStatus, record.getStatus(), reviewerId, null, null);

        log.info("Moderation started: recordId={}, reviewerId={}", recordId, reviewerId);
    }

    @Override
    public IPage<ModerationRecord> getPendingAssignments(PageQuery pageQuery, String targetType, String submitterSource) {
        return getPendingAssignments(pageQuery, targetType, submitterSource, true);
    }

    @Override
    public IPage<ModerationRecord> getPendingAssignments(PageQuery pageQuery, String targetType,
                                                         String submitterSource, boolean includeAdminOnly) {
        Page<ModerationRecord> page = new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize());
        LambdaQueryWrapper<ModerationRecord> wrapper = scopedQuery(includeAdminOnly);
        wrapper.eq(ModerationRecord::getStatus, ModerationConstants.STATUS_PENDING)
                .isNull(ModerationRecord::getAssignedModeratorId);

        String normalizedTargetType = ModerationConstants.normalizeTargetType(targetType);
        String normalizedSubmitterSource = ModerationConstants.normalizeSubmitterSource(submitterSource);
        if (normalizedTargetType != null) {
            wrapper.eq(ModerationRecord::getTargetType, normalizedTargetType);
        }
        if (normalizedSubmitterSource != null) {
            wrapper.eq(ModerationRecord::getSubmitterSource, normalizedSubmitterSource);
        }

        wrapper.orderByAsc(ModerationRecord::getPriority)
                .orderByAsc(ModerationRecord::getCreateTime);
        return page(page, wrapper);
    }

    @Override
    public IPage<ModerationRecord> getModeratorTasks(Long moderatorId, String status, PageQuery pageQuery) {
        return getModeratorTasks(moderatorId, status, null, pageQuery);
    }

    @Override
    public IPage<ModerationRecord> getModeratorTasks(Long moderatorId, String status, String targetType, PageQuery pageQuery) {
        Page<ModerationRecord> page = new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize());
        LambdaQueryWrapper<ModerationRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModerationRecord::getAssignedModeratorId, moderatorId);

        String normalizedStatus = ModerationConstants.normalize(status);
        if (normalizedStatus != null) {
            wrapper.eq(ModerationRecord::getStatus, normalizedStatus);
        }

        String normalizedTargetType = ModerationConstants.normalizeTargetType(targetType);
        if (normalizedTargetType != null) {
            wrapper.eq(ModerationRecord::getTargetType, normalizedTargetType);
        }

        wrapper.orderByDesc(ModerationRecord::getCreateTime);
        return page(page, wrapper);
    }

    @Override
    public Map<String, Object> getModeratorStats(Long moderatorId) {
        Map<String, Object> stats = new HashMap<>();
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = todayStart.plusDays(1);
        Map<String, Object> aggregate = getBaseMapper().selectModeratorStats(moderatorId, todayStart, tomorrowStart);

        Long todayCompleted = longValue(aggregate, "todayCompleted");
        Long inProgressCount = longValue(aggregate, "inProgressCount");
        Long pendingCount = longValue(aggregate, "pendingCount");
        Long approvedCount = longValue(aggregate, "approvedCount");
        Long rejectedCount = longValue(aggregate, "rejectedCount");
        Long historicalSkippedCount = longValue(aggregate, "historicalSkippedCount");
        Long skippedLogCount = auditLogService.countActions(moderatorId, AUDIT_RECORD_TYPE, "skip");
        Long skippedCount = historicalSkippedCount + skippedLogCount;

        Long reviewedOrAssignedTasks = longValue(aggregate, "reviewedOrAssignedTasks");
        Long totalCompleted = approvedCount + rejectedCount;
        Long avgProcessTime = longValue(aggregate, "avgProcessTime");

        stats.put("todayCompleted", todayCompleted);
        stats.put("inProgress", inProgressCount);
        stats.put("pending", pendingCount);
        stats.put("totalCompleted", totalCompleted);
        stats.put("rejectedCount", rejectedCount);
        stats.put("hasQuota", assignmentService.hasQuota(moderatorId));

        stats.put("totalTasks", reviewedOrAssignedTasks + skippedLogCount);
        stats.put("pendingTasks", pendingCount);
        stats.put("completedTasks", totalCompleted);
        stats.put("approvedTasks", approvedCount);
        stats.put("rejectedTasks", rejectedCount);
        stats.put("skippedTasks", skippedCount);
        stats.put("avgProcessTime", avgProcessTime);

        return stats;
    }

    @Override
    public Map<String, Object> getGlobalStats() {
        return getGlobalStats(true);
    }

    @Override
    public Map<String, Object> getGlobalStats(boolean includeAdminOnly) {
        Map<String, Object> stats = new HashMap<>();
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = todayStart.plusDays(1);
        Map<String, Object> aggregate = getBaseMapper().selectGlobalStats(
                includeAdminOnly, ModerationConstants.getAdminOnlyTargetTypes(), todayStart, tomorrowStart);

        Long totalRecords = longValue(aggregate, "totalRecords");
        Long pendingAssignment = longValue(aggregate, "pendingAssignment");
        Long pendingRecords = longValue(aggregate, "pendingRecords");
        Long inProgress = longValue(aggregate, "inProgress");
        Long completedRecords = longValue(aggregate, "completedRecords");
        Long todayCompleted = longValue(aggregate, "todayCompleted");

        Map<String, Long> byType = new HashMap<>();
        for (String type : ModerationConstants.getTargetTypeNames().keySet()) {
            if (!includeAdminOnly && ModerationConstants.isAdminOnlyTargetType(type)) {
                continue;
            }
            byType.put(type, 0L);
        }
        List<Map<String, Object>> typeRows = getBaseMapper().selectPendingAssignmentByType(
                includeAdminOnly, ModerationConstants.getAdminOnlyTargetTypes());
        for (Map<String, Object> typeRow : typeRows) {
            String type = stringValue(typeRow, "targetType");
            if (type != null && byType.containsKey(type)) {
                byType.put(type, longValue(typeRow, "typeCount"));
            }
        }

        List<Long> onlineModerators = onlineStatusService.getOnlineModerators();
        List<Long> activeModerators = onlineStatusService.getActiveModerators();

        stats.put("pendingAssignment", pendingAssignment);
        stats.put("inProgress", inProgress);
        stats.put("todayCompleted", todayCompleted);
        stats.put("byType", byType);
        stats.put("onlineModeratorCount", onlineModerators.size());
        stats.put("activeModeratorCount", activeModerators.size());
        stats.put("moderatorLoads", assignmentService.getAllModeratorLoads());

        stats.put("totalRecords", totalRecords);
        stats.put("pendingRecords", pendingRecords);
        stats.put("inProgressRecords", inProgress);
        stats.put("completedRecords", completedRecords);
        stats.put("onlineModerators", onlineModerators.size());
        stats.put("activeModerators", activeModerators.size());

        return stats;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void skipReview(Long recordId, Long reviewerId, String skipReason) {
        ModerationRecord record = getRequiredRecord(recordId);
        if (reviewerId == null || !reviewerId.equals(record.getAssignedModeratorId())) {
            throw new BusinessException("Only the assigned moderator can skip this task");
        }
        if (!ModerationConstants.STATUS_PENDING.equals(record.getStatus())
                && !ModerationConstants.STATUS_IN_PROGRESS.equals(record.getStatus())) {
            throw new BusinessException("Moderation record status does not allow skipping");
        }

        String oldStatus = record.getStatus();
        LocalDateTime skipTime = LocalDateTime.now();
        Integer durationSeconds = calculateDurationSeconds(record.getAssignedTime(), skipTime);

        if (getBaseMapper().releaseActiveAssignment(recordId, reviewerId, skipReason, skipTime) != 1) {
            throw new BusinessException("Moderation record was completed, skipped, or reassigned by another reviewer");
        }

        record.setStatus(ModerationConstants.STATUS_PENDING);
        record.setAssignedModeratorId(null);
        record.setAssignedTime(null);
        record.setModeratorOnlineStatus(null);
        record.setReviewReason(skipReason);
        record.setUpdateTime(skipTime);

        assignmentService.releaseModeration(recordId, reviewerId);
        logModerationAction(record, "skip", oldStatus, ModerationConstants.STATUS_PENDING, reviewerId, skipReason, durationSeconds);

        log.info("Moderation skipped: recordId={}, reviewerId={}, reason={}", recordId, reviewerId, skipReason);
    }

    private LambdaQueryWrapper<ModerationRecord> scopedQuery(boolean includeAdminOnly) {
        LambdaQueryWrapper<ModerationRecord> wrapper = new LambdaQueryWrapper<>();
        if (!includeAdminOnly) {
            wrapper.notIn(ModerationRecord::getTargetType, ModerationConstants.getAdminOnlyTargetTypes());
        }
        return wrapper;
    }

    private ModerationRecord getRequiredRecord(Long recordId) {
        ModerationRecord record = getById(recordId);
        if (record == null) {
            throw new BusinessException("Moderation record does not exist");
        }
        return record;
    }

    private void logModerationAction(ModerationRecord record, String action, String oldStatus,
                                     String newStatus, Long operatorId, String comment,
                                     Integer durationSeconds) {
        if (record == null) {
            return;
        }
        auditLogService.logAudit(record.getId(), AUDIT_RECORD_TYPE, action,
                oldStatus, newStatus, operatorId, null, buildAuditComment(record, comment),
                null, null, durationSeconds, Boolean.TRUE);
    }

    private void applyTargetReview(ModerationRecord record,
                                   Long reviewerId,
                                   String reviewResult,
                                   String reviewReason) {
        if (record != null && "emoji_package".equals(record.getTargetType())) {
            emojiService.reviewPackage(record.getTargetId(),
                    ModerationConstants.STATUS_APPROVED.equals(reviewResult),
                    reviewReason,
                    reviewerId);
        } else if (record != null && "decoration".equals(record.getTargetType())) {
            decorationCreatorService.review(record.getTargetId(),
                    ModerationConstants.STATUS_APPROVED.equals(reviewResult),
                    reviewReason,
                    reviewerId);
        }
    }

    private String buildAuditComment(ModerationRecord record, String comment) {
        String target = "targetType=" + record.getTargetType() + ", targetId=" + record.getTargetId();
        if (comment == null || comment.trim().isEmpty()) {
            return target;
        }
        return target + ", comment=" + comment;
    }

    private Integer calculateDurationSeconds(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            return null;
        }
        long seconds = ChronoUnit.SECONDS.between(startTime, endTime);
        if (seconds < 0) {
            return 0;
        }
        if (seconds > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) seconds;
    }

    private Long longValue(Map<String, Object> row, String key) {
        if (row == null || row.get(key) == null) {
            return 0L;
        }
        Object value = row.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            log.debug("Failed to parse moderation statistic {}={}", key, value);
            return 0L;
        }
    }

    private String stringValue(Map<String, Object> row, String key) {
        if (row == null || row.get(key) == null) {
            return null;
        }
        return String.valueOf(row.get(key));
    }
}
