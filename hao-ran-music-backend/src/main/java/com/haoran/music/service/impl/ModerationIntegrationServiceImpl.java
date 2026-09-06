   
                      
   
package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.haoran.music.common.constant.ModerationConstants;
import com.haoran.music.common.dto.ModerationCreateDTO;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.common.vo.ModerationRecordVO;
import com.haoran.music.common.vo.ModeratorVO;
import com.haoran.music.common.vo.WorkStatusVO;
import com.haoran.music.entity.ModerationRecord;
import com.haoran.music.entity.User;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.ModerationAssignmentService;
import com.haoran.music.service.ModerationIntegrationService;
import com.haoran.music.service.ModerationRecordService;
import com.haoran.music.service.OnlineStatusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

   
            
   
@Slf4j
@Service
public class ModerationIntegrationServiceImpl implements ModerationIntegrationService {

    @Autowired
    private ModerationRecordService moderationRecordService;

    @Autowired
    private OnlineStatusService onlineStatusService;

    @Autowired
    private ModerationAssignmentService assignmentService;

    @Autowired
    private UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitForModeration(ModerationCreateDTO dto) {
        return submitForModeration(
                dto.getTargetType(),
                dto.getTargetId(),
                dto.getSubmitterId(),
                dto.getSubmitterSource(),
                dto.getPriority() != null ? dto.getPriority() : ModerationConstants.DEFAULT_PRIORITY
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitForModeration(String targetType, Long targetId, Long submitterId, String submitterSource) {
        return submitForModeration(targetType, targetId, submitterId, submitterSource,
                ModerationConstants.DEFAULT_PRIORITY);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitForModeration(String targetType, Long targetId, Long submitterId,
                                    String submitterSource, Integer priority) {
        return moderationRecordService.createRecord(targetType, targetId, submitterId, submitterSource, priority);
    }

    @Override
    public ModerationRecordVO getRecordDetail(Long recordId) {
        ModerationRecord record = moderationRecordService.getById(recordId);
        if (record == null) {
            return null;
        }
        return convertToVO(record);
    }

    @Override
    public IPage<ModerationRecordVO> getPendingAssignments(String targetType, String submitterSource, PageQuery pageQuery) {
        IPage<ModerationRecord> page = moderationRecordService.getPendingAssignments(pageQuery, targetType, submitterSource);
        return page.convert(this::convertToVO);
    }

    @Override
    public IPage<ModerationRecordVO> getModeratorTasks(Long moderatorId, String status, PageQuery pageQuery) {
        IPage<ModerationRecord> page = moderationRecordService.getModeratorTasks(moderatorId, status, pageQuery);
        return page.convert(this::convertToVO);
    }

    @Override
    public List<ModeratorVO> getOnlineModerators() {
        List<User> users = onlineStatusService.getOnlineModeratorDetails();
        return users.stream().map(this::convertToModeratorVO).collect(Collectors.toList());
    }

    @Override
    public List<ModeratorVO> getActiveModerators() {
        List<User> users = onlineStatusService.getActiveModeratorDetails();
        return users.stream().map(this::convertToModeratorVO).collect(Collectors.toList());
    }

    @Override
    public WorkStatusVO getWorkStatus() {
        WorkStatusVO vo = new WorkStatusVO();
        vo.setCurrentTime(LocalDateTime.now().toString());

        List<ModeratorVO> onlineModerators = getOnlineModerators();
        List<ModeratorVO> activeModerators = getActiveModerators();
        vo.setOnlineModerators(onlineModerators);
        vo.setActiveModerators(activeModerators);
        vo.setOnlineModeratorCount(onlineModerators.size());
        vo.setActiveModeratorCount(activeModerators.size());

        Map<Long, Integer> loads = assignmentService.getAllModeratorLoads();
        vo.setModeratorLoads(loads);

        Map<String, Object> stats = moderationRecordService.getGlobalStats();
        vo.setPendingAssignmentCount((Long) stats.get("pendingAssignment"));
        vo.setInProgressCount((Long) stats.get("inProgress"));
        vo.setTodayCompletedCount((Long) stats.get("todayCompleted"));

        return vo;
    }

    @Override
    public ModeratorVO getModeratorStats(Long moderatorId) {
        User user = userMapper.selectById(moderatorId);
        if (user == null) {
            return null;
        }

        ModeratorVO vo = convertToModeratorVO(user);
        Map<String, Object> stats = moderationRecordService.getModeratorStats(moderatorId);
        vo.setCurrentTaskCount(((Long) stats.getOrDefault("inProgress", 0L)).intValue());
        vo.setHasQuota((Boolean) stats.get("hasQuota"));

        Integer dailyQuota = user.getDailyQuota() != null ? user.getDailyQuota() : 100;
        Long todayCompleted = (Long) stats.getOrDefault("todayCompleted", 0L);
        if (dailyQuota > 0) {
            vo.setTodayCompletionRate(todayCompleted.doubleValue() / dailyQuota);
        }

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long recordId, Long reviewerId, String reviewReason) {
        moderationRecordService.completeReview(recordId, reviewerId, ModerationConstants.STATUS_APPROVED, reviewReason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long recordId, Long reviewerId, String reviewReason) {
        moderationRecordService.completeReview(recordId, reviewerId, ModerationConstants.STATUS_REJECTED, reviewReason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeTargetReview(String targetType, Long targetId, Long reviewerId,
                                     String reviewResult, String reviewReason) {
        moderationRecordService.completeTargetReview(
                targetType, targetId, reviewerId, reviewResult, reviewReason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void skip(Long recordId, Long reviewerId, String skipReason) {
        moderationRecordService.skipReview(recordId, reviewerId, skipReason);
    }

    @Override
    public boolean isApproved(String targetType, Long targetId) {
        ModerationRecord record = getLatestRecord(targetType, targetId);
        return record != null && ModerationConstants.STATUS_APPROVED.equals(record.getStatus());
    }

    @Override
    public boolean isRejected(String targetType, Long targetId) {
        ModerationRecord record = getLatestRecord(targetType, targetId);
        return record != null && ModerationConstants.STATUS_REJECTED.equals(record.getStatus());
    }

    @Override
    public String getModerationStatus(String targetType, Long targetId) {
        ModerationRecord record = getLatestRecord(targetType, targetId);
        return record != null ? record.getStatus() : null;
    }

    private ModerationRecord getLatestRecord(String targetType, Long targetId) {
        String normalizedTargetType = ModerationConstants.normalizeTargetType(targetType);
        List<ModerationRecord> records = moderationRecordService.list(
                new LambdaQueryWrapper<ModerationRecord>()
                        .eq(ModerationRecord::getTargetType, normalizedTargetType)
                        .eq(ModerationRecord::getTargetId, targetId)
                        .orderByDesc(ModerationRecord::getCreateTime)
                        .last("LIMIT 1")
        );
        return records.isEmpty() ? null : records.get(0);
    }

    private ModerationRecordVO convertToVO(ModerationRecord record) {
        ModerationRecordVO vo = new ModerationRecordVO();
        vo.setId(record.getId());
        vo.setTargetType(record.getTargetType());
        vo.setTargetTypeName(ModerationConstants.getTargetTypeName(record.getTargetType()));
        vo.setTargetId(record.getTargetId());
        vo.setSubmitterId(record.getSubmitterId());
        vo.setSubmitterSource(record.getSubmitterSource());
        vo.setAssignedModeratorId(record.getAssignedModeratorId());
        vo.setAssignedTime(record.getAssignedTime());
        vo.setModeratorOnlineStatus(record.getModeratorOnlineStatus());
        vo.setPriority(record.getPriority());
        vo.setStatus(record.getStatus());
        vo.setStatusName(ModerationConstants.getStatusName(record.getStatus()));
        vo.setReviewerId(record.getReviewerId());
        vo.setReviewTime(record.getReviewTime());
        vo.setReviewResult(record.getReviewResult());
        vo.setReviewReason(record.getReviewReason());
        vo.setCreateTime(record.getCreateTime());
        vo.setUpdateTime(record.getUpdateTime());

        if (record.getAssignedTime() == null && record.getCreateTime() != null) {
            long minutes = ChronoUnit.MINUTES.between(record.getCreateTime(), LocalDateTime.now());
            vo.setWaitingMinutes(minutes);
        }

        if (record.getReviewTime() != null && record.getCreateTime() != null) {
            long minutes = ChronoUnit.MINUTES.between(record.getCreateTime(), record.getReviewTime());
            vo.setProcessingMinutes(minutes);
        }

        if (record.getSubmitterId() != null) {
            User submitter = userMapper.selectById(record.getSubmitterId());
            if (submitter != null) {
                vo.setSubmitterName(submitter.getNickname() != null ? submitter.getNickname() : submitter.getUsername());
            }
        }

        if (record.getAssignedModeratorId() != null) {
            User moderator = userMapper.selectById(record.getAssignedModeratorId());
            if (moderator != null) {
                vo.setModeratorName(moderator.getNickname() != null ? moderator.getNickname() : moderator.getUsername());
            }
        }

        if (record.getReviewerId() != null) {
            User reviewer = userMapper.selectById(record.getReviewerId());
            if (reviewer != null) {
                vo.setReviewerName(reviewer.getNickname() != null ? reviewer.getNickname() : reviewer.getUsername());
            }
        }

        return vo;
    }

    private ModeratorVO convertToModeratorVO(User user) {
        ModeratorVO vo = new ModeratorVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setModeratorStatus(user.getModeratorStatus());
        vo.setModeratorNote(user.getModeratorNote());
        vo.setIsOnline(user.getIsOnline());
        vo.setLastOnlineTime(user.getLastOnlineTime());
        vo.setTodayReviewCount(user.getTodayReviewCount());
        vo.setTotalReviewCount(user.getTotalReviewCount());
        vo.setDailyQuota(user.getDailyQuota());
        vo.setLastReviewTime(user.getLastReviewTime());
        vo.setOnlineStatus(Integer.valueOf(1).equals(user.getIsOnline()) ? "在线" : "离线");
        return vo;
    }
}
