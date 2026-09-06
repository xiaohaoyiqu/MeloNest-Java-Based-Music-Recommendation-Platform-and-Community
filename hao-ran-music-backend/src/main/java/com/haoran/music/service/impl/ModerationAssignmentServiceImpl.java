


package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.haoran.music.common.config.ModerationWorkflowConfig;
import com.haoran.music.common.config.WorkTimeConfig;
import com.haoran.music.common.constant.ModerationConstants;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.vo.ModerationAssignmentVO;
import com.haoran.music.entity.ModerationRecord;
import com.haoran.music.entity.User;
import com.haoran.music.enums.UserRole;
import com.haoran.music.mapper.ModerationRecordMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.ModerationAssignmentService;
import com.haoran.music.service.OnlineStatusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;




@Slf4j
@Service
public class ModerationAssignmentServiceImpl implements ModerationAssignmentService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private OnlineStatusService onlineStatusService;

    @Autowired
    private WorkTimeConfig workTimeConfig;

    @Autowired
    private ModerationRecordMapper moderationRecordMapper;

    @Autowired
    private ModerationWorkflowConfig moderationWorkflowConfig;

    @Override
    public boolean assignModeration(Long moderationId) {
        try {
            ModerationRecord record = moderationRecordMapper.selectById(moderationId);
            if (record == null) {
                log.warn("Moderation record not found: moderationId={}", moderationId);
                return false;
            }
            if (record.getAssignedModeratorId() != null
                    && (ModerationConstants.STATUS_PENDING.equals(record.getStatus())
                    || ModerationConstants.STATUS_IN_PROGRESS.equals(record.getStatus()))) {
                log.info("Moderation already assigned: moderationId={}, moderator={}",
                        moderationId, record.getAssignedModeratorId());
                return true;
            }
            if (!ModerationConstants.STATUS_PENDING.equals(record.getStatus())) {
                log.info("Moderation assignment rejected by state: moderationId={}, status={}",
                        moderationId, record.getStatus());
                return false;
            }

            Long moderatorId = getBestModerator(record.getTargetType());
            if (moderatorId == null) {
                log.warn("No available reviewer: moderationId={}, targetType={}", moderationId, record.getTargetType());
                return false;
            }

            LocalDateTime assignedTime = LocalDateTime.now();
            int claimed = moderationRecordMapper.claimPendingAssignment(
                    moderationId, moderatorId, assignedTime,
                    onlineStatusService.isOnline(moderatorId) ? 1 : 0);
            if (claimed != 1) {
                log.info("Moderation assignment lost concurrent claim: moderationId={}", moderationId);
                return false;
            }

            incrementLoad(moderatorId);
            log.info("Moderation assigned: moderationId={}, moderator={}", moderationId, moderatorId);
            return true;
        } catch (Exception e) {
            log.error("Moderation assignment failed: moderationId={}, error={}", moderationId, e.getClass().getSimpleName());
            return false;
        }
    }

    @Override
    public boolean assignModeration(Long moderationId, Long moderatorId) {
        if (moderatorId == null) {
            throw new BusinessException("Moderator id is required");
        }

        ModerationRecord record = moderationRecordMapper.selectById(moderationId);
        if (record == null) {
            throw new BusinessException("Moderation record does not exist");
        }
        if (record.getAssignedModeratorId() != null) {
            boolean assignedToCurrentModerator = moderatorId.equals(record.getAssignedModeratorId());
            boolean assignableStatus = ModerationConstants.STATUS_PENDING.equals(record.getStatus())
                    || ModerationConstants.STATUS_IN_PROGRESS.equals(record.getStatus());
            if (assignedToCurrentModerator && assignableStatus) {
                return true;
            }
            throw new BusinessException("Moderation record has already been assigned");
        }
        if (!ModerationConstants.STATUS_PENDING.equals(record.getStatus())) {
            throw new BusinessException("Moderation record status does not allow assignment");
        }
        if (!workTimeConfig.isWorkTime()) {
            throw new BusinessException("Current time is outside moderation work time");
        }

        User moderator = userMapper.selectById(moderatorId);
        if (!canReviewTarget(moderator, record.getTargetType())) {
            throw new BusinessException("Current user cannot handle this moderation type");
        }
        if (!onlineStatusService.isOnline(moderatorId)) {
            throw new BusinessException("Current moderator is offline");
        }
        if (!hasQuota(moderatorId)) {
            throw new BusinessException("Current moderator daily quota is exhausted");
        }

        LocalDateTime assignedTime = LocalDateTime.now();
        int claimed = moderationRecordMapper.claimPendingAssignment(
                moderationId, moderatorId, assignedTime, 1);
        if (claimed != 1) {
            ModerationRecord latest = moderationRecordMapper.selectById(moderationId);
            if (latest != null && moderatorId.equals(latest.getAssignedModeratorId())
                    && (ModerationConstants.STATUS_PENDING.equals(latest.getStatus())
                    || ModerationConstants.STATUS_IN_PROGRESS.equals(latest.getStatus()))) {
                return true;
            }
            throw new BusinessException("Moderation task was assigned to another reviewer");
        }

        incrementLoad(moderatorId);
        log.info("Moderation assigned to specified reviewer: moderationId={}, reviewer={}", moderationId, moderatorId);
        return true;
    }

    @Override
    public Long assignTask(String targetType, Long targetId, Long submitterId, String submitterSource) {
        String normalizedTargetType = ModerationConstants.normalizeTargetType(targetType);

        if (!workTimeConfig.isWorkTime()) {
            log.info("Outside work time, task is kept pending: taskType={}, targetId={}", normalizedTargetType, targetId);
            throw new BusinessException("当前非工作时间，任务将在工作时间自动分配");
        }

        List<Long> onlineModeratorIds = onlineStatusService.getOnlineModerators();
        if (onlineModeratorIds.isEmpty()) {
            log.warn("No online reviewers: taskType={}", normalizedTargetType);
            throw new BusinessException("当前无在线审核员，请稍后再试");
        }

        List<User> availableModerators = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .in(User::getId, onlineModeratorIds)
                        .eq(User::getDeleted, 0)
        ).stream()
                .filter(m -> canReviewTarget(m, normalizedTargetType))
                .filter(m -> onlineStatusService.isOnline(m.getId()))
                .collect(Collectors.toList());

        if (availableModerators.isEmpty()) {
            if (ModerationConstants.isAdminOnlyTargetType(normalizedTargetType)) {
                throw new BusinessException("当前无在线管理员可处理该审核类型");
            }
            throw new BusinessException("当前无可用审核员，请稍后再试");
        }

        Map<Long, Integer> currentLoads = getModeratorLoads(availableModerators);
        Long selectedModeratorId = availableModerators.stream()
                .filter(m -> hasQuota(m.getId()))
                .min((m1, m2) -> {
                    int load1 = currentLoads.getOrDefault(m1.getId(), 0);
                    int load2 = currentLoads.getOrDefault(m2.getId(), 0);
                    return Integer.compare(load1, load2);
                })
                .map(User::getId)
                .orElseThrow(() -> new BusinessException("所有审核员今日配额已用完"));

        incrementLoad(selectedModeratorId);
        log.info("Task assigned: taskType={}, targetId={}, reviewer={}",
                normalizedTargetType, targetId, selectedModeratorId);
        return selectedModeratorId;
    }

    @Override
    public int autoAssignPendingModerations() {
        int count = 0;
        try {
            if (!workTimeConfig.isWorkTime()) {
                log.info("Outside work time, skip auto assignment");
                return 0;
            }

            List<Long> onlineModerators = getOnlineModerators();
            if (onlineModerators.isEmpty()) {
                log.warn("No online reviewers, skip auto assignment");
                return 0;
            }

            LambdaQueryWrapper<ModerationRecord> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ModerationRecord::getStatus, ModerationConstants.STATUS_PENDING)
                    .isNull(ModerationRecord::getAssignedModeratorId)
                    .orderByAsc(ModerationRecord::getPriority)
                    .orderByAsc(ModerationRecord::getCreateTime)
                    .last("LIMIT " + moderationWorkflowConfig.getAssignment().getAutoAssignBatchLimit());

            List<ModerationRecord> pendingRecords = moderationRecordMapper.selectList(wrapper);
            for (ModerationRecord record : pendingRecords) {
                try {
                    Long moderatorId = getBestModerator(record.getTargetType());
                    if (moderatorId == null) {
                        log.warn("No available reviewer: recordId={}, targetType={}", record.getId(), record.getTargetType());
                        if (ModerationConstants.isAdminOnlyTargetType(record.getTargetType())) {
                            continue;
                        }
                        break;
                    }

                    LocalDateTime assignedTime = LocalDateTime.now();
                    int claimed = moderationRecordMapper.claimPendingAssignment(
                            record.getId(), moderatorId, assignedTime,
                            onlineStatusService.isOnline(moderatorId) ? 1 : 0);
                    if (claimed != 1) {
                        log.info("Auto assignment skipped concurrent claim: recordId={}", record.getId());
                        continue;
                    }

                    incrementLoad(moderatorId);
                    count++;
                    log.info("Auto assigned moderation task: recordId={}, reviewer={}", record.getId(), moderatorId);
                } catch (Exception e) {
                    log.error("Auto assignment failed for record: recordId={}, error={}", record.getId(), e.getClass().getSimpleName());
                }
            }

            log.info("Auto assignment completed: total={}", count);
        } catch (Exception e) {
            log.error("Auto assignment failed: {}", e.getClass().getSimpleName());
        }
        return count;
    }

    @Override
    public List<ModerationAssignmentVO> getModeratorAssignments(Long moderatorId) {
        LambdaQueryWrapper<ModerationRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModerationRecord::getAssignedModeratorId, moderatorId)
                .in(ModerationRecord::getStatus, ModerationConstants.STATUS_PENDING, ModerationConstants.STATUS_IN_PROGRESS)
                .orderByDesc(ModerationRecord::getAssignedTime);

        List<ModerationRecord> records = moderationRecordMapper.selectList(wrapper);
        List<ModerationAssignmentVO> result = records.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        log.info("Get reviewer assignments: moderatorId={}, count={}", moderatorId, result.size());
        return result;
    }

    @Override
    public int getModeratorWorkload(Long moderatorId) {
        int canonicalCount = moderationRecordMapper.countActiveAssignments(moderatorId);
        String key = moderationWorkflowConfig.getAssignment().getLoadPrefix() + moderatorId;
        redisTemplate.opsForValue().set(key, String.valueOf(canonicalCount),
                moderationWorkflowConfig.getAssignment().getLoadCacheDays(), TimeUnit.DAYS);
        return canonicalCount;
    }

    @Override
    public boolean releaseModeration(Long moderationId, Long moderatorId) {
        try {
            decrementLoad(moderatorId);
            log.info("Release moderation task: moderationId={}, moderatorId={}", moderationId, moderatorId);
            return true;
        } catch (Exception e) {
            log.error("Release moderation task failed: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    @Override
    public boolean reassignModeration(Long moderationId) {
        LambdaQueryWrapper<ModerationRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModerationRecord::getId, moderationId);

        ModerationRecord record = moderationRecordMapper.selectOne(wrapper);
        if (record == null) {
            log.warn("Moderation record not found: moderationId={}", moderationId);
            return false;
        }

        Long oldModeratorId = record.getAssignedModeratorId();
        if (oldModeratorId == null) {
            return assignModeration(moderationId);
        }

        Long newModeratorId = getBestModerator(record.getTargetType(), oldModeratorId);
        if (newModeratorId == null) {
            log.warn("No available reviewer for reassignment: moderationId={}, targetType={}",
                    moderationId, record.getTargetType());
            return false;
        }

        LocalDateTime assignedTime = LocalDateTime.now();
        int reassigned = moderationRecordMapper.reassignActiveAssignment(
                moderationId, oldModeratorId, newModeratorId, assignedTime,
                onlineStatusService.isOnline(newModeratorId) ? 1 : 0);
        if (reassigned != 1) {
            log.info("Moderation reassignment lost concurrent update: moderationId={}", moderationId);
            return false;
        }

        decrementLoad(oldModeratorId);
        incrementLoad(newModeratorId);
        log.info("Reassigned moderation task: moderationId={}, oldReviewer={}, newReviewer={}",
                moderationId, oldModeratorId, newModeratorId);
        return true;
    }

    @Override
    public boolean isModeratorOnline(Long moderatorId) {
        return onlineStatusService.isOnline(moderatorId);
    }

    @Override
    public List<Long> getOnlineModerators() {
        return onlineStatusService.getOnlineModerators();
    }

    @Override
    public void updateModeratorOnlineStatus(Long moderatorId, boolean online) {
        onlineStatusService.setOnlineStatus(moderatorId, online);
    }

    @Override
    public Long getBestModerator() {
        return getBestModerator(null);
    }

    private Long getBestModerator(String targetType) {
        return getBestModerator(targetType, null);
    }

    private Long getBestModerator(String targetType, Long excludedModeratorId) {
        List<Long> onlineModeratorIds = onlineStatusService.getOnlineModerators();
        if (onlineModeratorIds.isEmpty()) {
            return null;
        }

        List<User> moderators = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .in(User::getId, onlineModeratorIds)
                        .eq(User::getDeleted, 0)
        ).stream()
                .filter(m -> canReviewTarget(m, targetType))
                .filter(m -> onlineStatusService.isOnline(m.getId()))
                .collect(Collectors.toList());

        Map<Long, Integer> loads = getAllModeratorLoads();
        return moderators.stream()
                .filter(m -> excludedModeratorId == null || !excludedModeratorId.equals(m.getId()))
                .filter(m -> hasQuota(m.getId()))
                .min(Comparator.comparingInt(m -> loads.getOrDefault(m.getId(), 0)))
                .map(User::getId)
                .orElse(null);
    }

    @Override
    public boolean hasQuota(Long moderatorId) {
        LocalDate today = LocalDate.now();
        int reviewed = moderationRecordMapper.countCompletedReviews(
                moderatorId, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        String key = moderationWorkflowConfig.getAssignment().getTodayPrefix() + moderatorId + ":" + LocalDate.now();
        redisTemplate.opsForValue().set(key, String.valueOf(reviewed),
                moderationWorkflowConfig.getAssignment().getTodayQuotaCacheDays(), TimeUnit.DAYS);

        User moderator = userMapper.selectById(moderatorId);
        int quota = (moderator != null && moderator.getDailyQuota() != null)
                ? moderator.getDailyQuota()
                : moderationWorkflowConfig.getAssignment().getDefaultDailyQuota();

        return reviewed < quota;
    }

    @Override
    public Map<Long, Integer> getAllModeratorLoads() {
        List<User> moderators = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .eq(User::getDeleted, 0)
        ).stream()
                .filter(this::canModerate)
                .collect(Collectors.toList());

        Map<Long, Integer> loads = new HashMap<>();
        for (User moderator : moderators) {
            loads.put(moderator.getId(), getModeratorWorkload(moderator.getId()));
        }
        return loads;
    }

    @Override
    public List<Long> getOnlineModeratorIds() {
        return onlineStatusService.getOnlineModerators();
    }

    @Override
    public List<Long> getActiveModeratorIds() {
        return onlineStatusService.getActiveModerators();
    }

    @Override
    public void completeTask(Long moderatorId) {
        decrementLoad(moderatorId);

        String todayKey = moderationWorkflowConfig.getAssignment().getTodayPrefix() + moderatorId + ":" + LocalDate.now();
        redisTemplate.opsForValue().increment(todayKey);
        redisTemplate.expire(todayKey, moderationWorkflowConfig.getAssignment().getTodayQuotaCacheDays(), TimeUnit.DAYS);

        userMapper.update(null,
                new LambdaUpdateWrapper<User>()
                        .eq(User::getId, moderatorId)
                        .setSql("today_review_count = today_review_count + 1")
                        .setSql("total_review_count = total_review_count + 1")
                        .set(User::getLastReviewTime, LocalDateTime.now())
        );

        log.debug("Moderation task completed: moderatorId={}", moderatorId);
    }

    private boolean canReviewTarget(User user, String targetType) {
        if (!canModerate(user)) {
            return false;
        }
        if (!ModerationConstants.isAdminOnlyTargetType(targetType)) {
            return true;
        }
        return isAdminUser(user);
    }

    private boolean canModerate(User user) {
        if (user == null) {
            return false;
        }
        if (UserRole.canModerate(user.getRole())) {
            return true;
        }
        return Integer.valueOf(1).equals(user.getIsModerator())
                && "active".equals(user.getModeratorStatus());
    }

    private boolean isAdminUser(User user) {
        if (user == null) {
            return false;
        }
        UserRole role = UserRole.fromCode(user.getRole());
        return role == UserRole.ADMIN || role == UserRole.SUPER_ADMIN;
    }

    private Map<Long, Integer> getModeratorLoads(List<User> moderators) {
        if (moderators == null || moderators.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Integer> loads = new HashMap<>();
        for (User moderator : moderators) {
            loads.put(moderator.getId(), getModeratorWorkload(moderator.getId()));
        }
        return loads;
    }

    private void incrementLoad(Long moderatorId) {
        if (moderatorId == null) {
            return;
        }
        String loadKey = moderationWorkflowConfig.getAssignment().getLoadPrefix() + moderatorId;
        redisTemplate.opsForValue().increment(loadKey);
        redisTemplate.expire(loadKey, moderationWorkflowConfig.getAssignment().getLoadCacheDays(), TimeUnit.DAYS);
    }

    private void decrementLoad(Long moderatorId) {
        if (moderatorId == null) {
            return;
        }
        String loadKey = moderationWorkflowConfig.getAssignment().getLoadPrefix() + moderatorId;
        Long value = redisTemplate.opsForValue().decrement(loadKey);
        if (value == null || value <= 0) {
            redisTemplate.opsForValue().set(loadKey, "0", moderationWorkflowConfig.getAssignment().getLoadCacheDays(), TimeUnit.DAYS);
        } else {
            redisTemplate.expire(loadKey, moderationWorkflowConfig.getAssignment().getLoadCacheDays(), TimeUnit.DAYS);
        }
    }

    private ModerationAssignmentVO convertToVO(ModerationRecord record) {
        ModerationAssignmentVO vo = new ModerationAssignmentVO();
        vo.setModerationId(record.getId());
        vo.setModeratorId(record.getAssignedModeratorId());
        vo.setModerationType(record.getTargetType());
        vo.setStatus(record.getStatus());
        vo.setAssignedAt(record.getAssignedTime() != null ? record.getAssignedTime().toString() : null);
        return vo;
    }
}
