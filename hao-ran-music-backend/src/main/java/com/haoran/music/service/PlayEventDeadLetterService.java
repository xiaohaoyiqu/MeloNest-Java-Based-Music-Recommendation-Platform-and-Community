


package com.haoran.music.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.haoran.music.entity.PlayEventDeadLetter;
import com.haoran.music.kafka.PlayEventListener;
import com.haoran.music.mapper.PlayEventDeadLetterMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;






@Slf4j
@Service
public class PlayEventDeadLetterService {

    private static final int KAFKA_RETRY_COUNT = 3;
    private static final int MAX_ERROR_LENGTH = 1800;

    @Resource
    private PlayEventDeadLetterMapper deadLetterMapper;
    @Autowired
    private ObjectProvider<PlayEventListener> playEventListenerProvider;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordFailure(String eventId,
                              String topic,
                              Integer partitionId,
                              Long offsetValue,
                              String messageKey,
                              String payload,
                              String errorMessage) {
        String safeError = truncate(errorMessage);
        deadLetterMapper.insertOrUpdateFailure(
                eventId,
                topic,
                partitionId,
                offsetValue,
                messageKey,
                payload,
                KAFKA_RETRY_COUNT,
                safeError);
        log.error("播放事件已写入死信表: topic={}, partition={}, offset={}, eventId={}, error={}",
                topic, partitionId, offsetValue, eventId, safeError);
    }

    public Map<String, Object> list(String status, String eventId, int page, int size) {
        int current = page <= 0 ? 1 : page;
        int pageSize = Math.min(Math.max(size, 1), 100);
        Page<PlayEventDeadLetter> pageParam = new Page<>(current, pageSize);
        LambdaQueryWrapper<PlayEventDeadLetter> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq(PlayEventDeadLetter::getStatus, status.trim().toUpperCase());
        }
        if (eventId != null && !eventId.trim().isEmpty()) {
            wrapper.eq(PlayEventDeadLetter::getEventId, eventId.trim());
        }
        wrapper.orderByDesc(PlayEventDeadLetter::getLastFailedAt)
                .orderByDesc(PlayEventDeadLetter::getId);
        Page<PlayEventDeadLetter> result = deadLetterMapper.selectPage(pageParam, wrapper);

        Map<String, Object> response = new HashMap<>();
        response.put("records", result.getRecords());
        response.put("total", result.getTotal());
        response.put("page", result.getCurrent());
        response.put("size", result.getSize());
        response.put("pages", result.getPages());
        return response;
    }

    public PlayEventDeadLetter getDetail(Long id) {
        return id == null ? null : deadLetterMapper.selectById(id);
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("statusSummary", deadLetterMapper.selectStatusSummary());
        return result;
    }

    public PlayEventDeadLetter replay(Long id, Long operatorId, String reason) {
        requireOperator(operatorId);
        PlayEventDeadLetter deadLetter = requirePending(id);
        try {
            playEventListenerProvider.getObject().replayDeadLetter(
                    deadLetter.getPayload(),
                    deadLetter.getTopic(),
                    deadLetter.getPartitionId() == null ? 0 : deadLetter.getPartitionId(),
                    deadLetter.getOffsetValue() == null ? -1L : deadLetter.getOffsetValue());
            deadLetterMapper.markResolved(id, operatorId, buildReason("replay_success", reason));
            return deadLetterMapper.selectById(id);
        } catch (Exception e) {
            String safeError = truncate(e.getMessage());
            deadLetterMapper.markReplayFailed(id, safeError);
            throw new IllegalStateException("播放事件死信重放失败: " + safeError, e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public PlayEventDeadLetter ignore(Long id, Long operatorId, String reason) {
        requireOperator(operatorId);
        String normalizedReason = normalizeRequiredReason(reason);
        requirePending(id);
        deadLetterMapper.markIgnored(id, operatorId, normalizedReason);
        return deadLetterMapper.selectById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public PlayEventDeadLetter resolve(Long id, Long operatorId, String reason) {
        requireOperator(operatorId);
        String normalizedReason = normalizeRequiredReason(reason);
        requirePending(id);
        deadLetterMapper.markResolved(id, operatorId, normalizedReason);
        return deadLetterMapper.selectById(id);
    }

    private String truncate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "Kafka 播放事件处理失败";
        }
        String normalized = value.trim();
        return normalized.length() > MAX_ERROR_LENGTH
                ? normalized.substring(0, MAX_ERROR_LENGTH)
                : normalized;
    }

    private PlayEventDeadLetter requirePending(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("死信ID不能为空");
        }
        PlayEventDeadLetter deadLetter = deadLetterMapper.selectById(id);
        if (deadLetter == null) {
            throw new IllegalArgumentException("播放事件死信不存在");
        }
        if (!"PENDING".equalsIgnoreCase(deadLetter.getStatus())) {
            throw new IllegalStateException("只有 PENDING 状态的死信可处理");
        }
        return deadLetter;
    }

    private void requireOperator(Long operatorId) {
        if (operatorId == null) {
            throw new IllegalArgumentException("请先登录");
        }
    }

    private String normalizeRequiredReason(String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("处理原因不能为空");
        }
        return truncateReason(reason);
    }

    private String buildReason(String action, String reason) {
        String normalized = reason == null || reason.trim().isEmpty()
                ? action
                : action + ": " + reason.trim();
        return truncateReason(normalized);
    }

    private String truncateReason(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() > 500 ? normalized.substring(0, 500) : normalized;
    }
}
