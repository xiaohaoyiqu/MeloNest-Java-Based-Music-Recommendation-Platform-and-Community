


package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.entity.AuditLog;
import com.haoran.music.entity.User;
import com.haoran.music.mapper.AuditLogMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.AuditLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogMapper auditLogMapper;
    private final UserMapper userMapper;

    public AuditLogServiceImpl(AuditLogMapper auditLogMapper,
                               UserMapper userMapper) {
        this.auditLogMapper = auditLogMapper;
        this.userMapper = userMapper;
    }

    @Override
    public void logAudit(Long recordId, String recordType, String action,
                         String oldStatus, String newStatus,
                         Long operatorId, String operatorName, String comment,
                         String operatorIp, String userAgent,
                         Integer durationSeconds, Boolean success) {
        try {
            Long safeOperatorId = operatorId == null ? 0L : operatorId;
            AuditLog auditLog = new AuditLog();
            auditLog.setRecordId(recordId);
            auditLog.setRecordType(truncate(normalizeKey(recordType), 50));
            auditLog.setAction(truncate(normalizeKey(action), 50));
            auditLog.setOldStatus(oldStatus);
            auditLog.setNewStatus(newStatus);
            auditLog.setOperatorId(safeOperatorId);
            auditLog.setOperatorName(truncate(operatorName != null ? operatorName : getModeratorName(safeOperatorId), 100));
            auditLog.setComment(truncate(comment, 500));
            auditLog.setOperatorIp(truncate(operatorIp, 50));
            auditLog.setUserAgent(userAgent);
            auditLog.setOperationTime(LocalDateTime.now());
            auditLog.setDurationSeconds(durationSeconds);
            auditLog.setSuccess(success == null ? Boolean.TRUE : success);

            auditLogMapper.insert(auditLog);
            log.info("Audit log written: recordType={}, recordId={}, action={}, operator={}",
                    recordType, recordId, action, operatorId);
        } catch (Exception e) {
            log.warn("Failed to write audit log: recordId={}, action={}, operator={}, error={}",
                    recordId, action, operatorId, e.getClass().getSimpleName());
        }
    }

    @Override
    public List<AuditLog> getRecordLogs(Long recordId) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AuditLog::getRecordId, recordId)
                .orderByDesc(AuditLog::getOperationTime);
        return auditLogMapper.selectList(wrapper);
    }

    @Override
    public IPage<AuditLog> getOperatorLogs(Long operatorId, LocalDateTime startTime,
                                           LocalDateTime endTime, PageQuery pageQuery) {
        Page<AuditLog> pageParam = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AuditLog::getOperatorId, operatorId);
        applyTimeRange(wrapper, startTime, endTime);
        wrapper.orderByDesc(AuditLog::getOperationTime);
        return auditLogMapper.selectPage(pageParam, wrapper);
    }

    @Override
    public Map<String, Object> getAuditStatistics(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> summary = auditLogMapper.selectAuditSummary(startTime, endTime);
        long totalActions = numberValue(summary, "totalActions");
        long successCount = numberValue(summary, "successCount");
        long failCount = numberValue(summary, "failCount");
        Map<String, Object> stats = new HashMap<>();

        stats.put("totalActions", totalActions);
        stats.put("actionStats", countMap(auditLogMapper.selectActionStats(startTime, endTime)));
        stats.put("typeStats", countMap(auditLogMapper.selectRecordTypeStats(startTime, endTime)));
        stats.put("successRate", totalActions == 0 ? 0 : successCount * 100 / totalActions);
        stats.put("successCount", successCount);
        stats.put("failCount", failCount);
        stats.put("avgDurationSeconds", numberValue(summary, "avgDurationSeconds"));

        return stats;
    }

    @Override
    public List<Map<String, Object>> getModeratorWorkStats(LocalDateTime startTime, LocalDateTime endTime) {
        List<Map<String, Object>> workStats = auditLogMapper.selectModeratorWorkStats(startTime, endTime);
        for (Map<String, Object> stat : workStats) {
            if (stat.get("moderatorName") == null && stat.get("moderatorId") instanceof Number) {
                stat.put("moderatorName", getModeratorName(((Number) stat.get("moderatorId")).longValue()));
            }
        }
        return workStats;
    }

    @Override
    public Map<String, Object> getAuditTypeStats(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> typeStats = new HashMap<>();
        Map<String, Long> stats = countMap(auditLogMapper.selectRecordTypeStats(startTime, endTime));
        typeStats.putAll(stats);
        typeStats.put("actionStats", countMap(auditLogMapper.selectActionStats(startTime, endTime)));
        typeStats.put("total", numberValue(auditLogMapper.selectAuditSummary(startTime, endTime), "totalActions"));
        return typeStats;
    }

    @Override
    public long countActions(Long operatorId, String recordType, String action) {
        try {
            LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
            if (operatorId != null) {
                wrapper.eq(AuditLog::getOperatorId, operatorId);
            }
            if (recordType != null && !recordType.trim().isEmpty()) {
                wrapper.eq(AuditLog::getRecordType, recordType);
            }
            if (action != null && !action.trim().isEmpty()) {
                wrapper.eq(AuditLog::getAction, action);
            }
            wrapper.eq(AuditLog::getSuccess, Boolean.TRUE);
            Long count = auditLogMapper.selectCount(wrapper);
            return count == null ? 0L : count;
        } catch (Exception e) {
            log.warn("Failed to count audit actions: operatorId={}, recordType={}, action={}, error={}",
                    operatorId, recordType, action, e.getClass().getSimpleName());
            return 0L;
        }
    }

    @Override
    public void logAction(Long recordId, String action, Long operatorId, String comment) {
        logAudit(recordId, "manual", action, null, null, operatorId, null,
                comment, null, null, null, Boolean.TRUE);
    }

    private void applyTimeRange(LambdaQueryWrapper<AuditLog> wrapper,
                                LocalDateTime startTime,
                                LocalDateTime endTime) {
        if (startTime != null) {
            wrapper.ge(AuditLog::getOperationTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(AuditLog::getOperationTime, endTime);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String normalizeKey(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "unknown";
        }
        return value;
    }

    private Map<String, Long> countMap(List<Map<String, Object>> rows) {
        Map<String, Long> result = new HashMap<>();
        if (rows == null) {
            return result;
        }
        for (Map<String, Object> row : rows) {
            Object key = row.get("statKey");
            result.put(normalizeKey(key == null ? null : String.valueOf(key)), numberValue(row, "statCount"));
        }
        return result;
    }

    private long numberValue(Map<String, Object> row, String key) {
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
            return 0L;
        }
    }

    private String getModeratorName(Long moderatorId) {
        if (moderatorId == null) {
            return null;
        }
        User user = userMapper.selectById(moderatorId);
        if (user != null && ObjectUtils.isNotEmpty(user.getNickname())) {
            return user.getNickname();
        }
        if (user != null && ObjectUtils.isNotEmpty(user.getUsername())) {
            return user.getUsername();
        }
        return "Moderator #" + moderatorId;
    }
}
