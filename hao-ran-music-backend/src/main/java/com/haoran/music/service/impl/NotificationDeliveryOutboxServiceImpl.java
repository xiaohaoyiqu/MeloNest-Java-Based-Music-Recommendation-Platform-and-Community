package com.haoran.music.service.impl;

import com.alibaba.fastjson2.JSON;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.entity.Notification;
import com.haoran.music.entity.NotificationDeliveryOutboxEvent;
import com.haoran.music.mapper.NotificationDeliveryOutboxMapper;
import com.haoran.music.mapper.NotificationMapper;
import com.haoran.music.service.NotificationDeliveryOutboxService;
import com.haoran.music.service.WebSocketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;






@Slf4j
@Service
public class NotificationDeliveryOutboxServiceImpl implements NotificationDeliveryOutboxService {

    private static final String NOTIFICATION_UPSERT = "NOTIFICATION_UPSERT";
    private static final int MAX_ATTEMPTS = 8;
    private static final int MAX_BATCH_SIZE = 100;
    private static final int LEASE_SECONDS = 60;

    private final NotificationDeliveryOutboxMapper outboxMapper;
    private final NotificationMapper notificationMapper;

    @Autowired(required = false)
    private WebSocketService webSocketService;

    public NotificationDeliveryOutboxServiceImpl(NotificationDeliveryOutboxMapper outboxMapper,
                                                  NotificationMapper notificationMapper) {
        this.outboxMapper = outboxMapper;
        this.notificationMapper = notificationMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String record(Notification notification, boolean unreadDelta) {
        if (ObjectUtils.isEmpty(notification)
                || ObjectUtils.isEmpty(notification.getId())
                || ObjectUtils.isEmpty(notification.getUserId())) {
            throw new BusinessException("通知投递事件缺少通知或接收人标识");
        }
        NotificationDeliveryOutboxEvent event = new NotificationDeliveryOutboxEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setNotificationId(notification.getId());
        event.setRecipientId(notification.getUserId());
        event.setEventType(NOTIFICATION_UPSERT);
        event.setUnreadDelta(unreadDelta ? 1 : 0);
        event.setMaxAttempts(MAX_ATTEMPTS);
        if (outboxMapper.insertEvent(event) != 1) {
            throw new BusinessException("通知投递事件写入失败");
        }
        return event.getEventId();
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean dispatchEvent(String eventId) {
        if (ObjectUtils.isEmpty(eventId)) {
            return false;
        }
        String workerId = UUID.randomUUID().toString();
        if (outboxMapper.claimEvent(eventId, workerId, LEASE_SECONDS) != 1) {
            return false;
        }
        NotificationDeliveryOutboxEvent event = outboxMapper.selectClaimedEvent(eventId, workerId);
        if (ObjectUtils.isEmpty(event)) {
            log.warn("event=notification_delivery_lease_lost eventId={} stage=load", eventId);
            return false;
        }
        if (!NOTIFICATION_UPSERT.equals(event.getEventType())) {
            return markTerminalFailure(event, workerId, "UNSUPPORTED_EVENT",
                    "通知投递事件类型不受支持");
        }
        if (ObjectUtils.isEmpty(event.getNotificationId()) || ObjectUtils.isEmpty(event.getRecipientId())) {
            return markTerminalFailure(event, workerId, "INVARIANT_VIOLATION",
                    "通知投递事实不一致");
        }
        try {
            Notification notification = notificationMapper.selectById(event.getNotificationId());
            if (ObjectUtils.isEmpty(notification) || Integer.valueOf(1).equals(notification.getDeleted())) {
                requireMarked(outboxMapper.markSuccess(eventId, workerId));
                log.info("event=notification_delivery_skipped eventId={} notificationId={}",
                        eventId, event.getNotificationId());
                return true;
            }
            if (!Objects.equals(event.getRecipientId(), notification.getUserId())) {
                return markTerminalFailure(event, workerId, "INVARIANT_VIOLATION",
                        "通知投递事实不一致");
            }
            if (ObjectUtils.isEmpty(webSocketService)) {
                throw new DeliveryException("DEPENDENCY_UNAVAILABLE", "WebSocket服务不可用");
            }
            Map<String, Object> message = new HashMap<>();
            message.put("type", "new_notification");
            Map<String, Object> data = JSON.parseObject(JSON.toJSONString(notification), Map.class);
            data.put("unreadDelta", Integer.valueOf(1).equals(event.getUnreadDelta()) ? 1 : 0);
            data.put("deliveryEventId", eventId);
            message.put("data", data);
            message.put("timestamp", System.currentTimeMillis());
            if (!webSocketService.trySendToUser(event.getRecipientId(), message)) {
                throw new DeliveryException("SESSION_UNAVAILABLE", "用户离线或会话发送失败");
            }
            requireMarked(outboxMapper.markSuccess(eventId, workerId));
            log.info("event=notification_delivery_succeeded eventId={} notificationId={} attempt={}",
                    eventId, event.getNotificationId(), safeAttempt(event));
            return true;
        } catch (Exception exception) {
            String category = exception instanceof DeliveryException
                    ? ((DeliveryException) exception).getCategory() : "DELIVERY_ERROR";
            Integer retryDelay = safeAttempt(event) < safeMaxAttempts(event)
                    ? Math.toIntExact(retryDelaySeconds(safeAttempt(event))) : null;
            int marked = outboxMapper.markFailed(eventId, workerId, category,
                    "通知实时投递失败", retryDelay);
            if (marked != 1) {
                log.warn("event=notification_delivery_lease_lost eventId={} stage=mark_failed category={}",
                        eventId, category);
                return false;
            }
            log.warn("event=notification_delivery_failed eventId={} notificationId={} category={} attempt={}",
                    eventId, event.getNotificationId(), category, safeAttempt(event));
            return false;
        }
    }

    @Override
    public int retryDueEvents(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_BATCH_SIZE));
        List<String> eventIds = outboxMapper.selectDueEventIds(safeLimit);
        if (ObjectUtils.isEmpty(eventIds)) {
            return 0;
        }
        int delivered = 0;
        for (String eventId : eventIds) {
            if (dispatchEvent(eventId)) {
                delivered++;
            }
        }
        return delivered;
    }

    @Override
    public boolean retryFailedEvent(String eventId) {
        if (ObjectUtils.isEmpty(eventId)
                || !eventId.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
                        + "[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
                || outboxMapper.requeueFailed(eventId) != 1) {
            return false;
        }
        log.info("event=notification_delivery_requeued eventId={}", eventId);
        return dispatchEvent(eventId);
    }

    @Override
    public Map<String, Object> getStatusSummary() {
        Map<String, Object> result = new HashMap<>();
        result.put("statuses", outboxMapper.selectStatusSummary());
        result.put("maxAttempts", MAX_ATTEMPTS);
        result.put("maxBatchSize", MAX_BATCH_SIZE);
        result.put("leaseSeconds", LEASE_SECONDS);
        return result;
    }

    @Override
    public List<Map<String, Object>> getRecentFailures(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_BATCH_SIZE));
        return outboxMapper.selectRecentFailures(safeLimit);
    }

    private void requireMarked(int changed) {
        if (changed != 1) {
            throw new DeliveryException("LEASE_LOST", "通知投递租约已失效");
        }
    }




    private boolean markTerminalFailure(NotificationDeliveryOutboxEvent event, String workerId,
                                        String category, String message) {
        int marked = outboxMapper.markTerminalFailed(
                event.getEventId(), workerId, category, message);
        if (marked == 1) {
            log.warn("event=notification_delivery_terminal_failed eventId={} notificationId={} category={}",
                    event.getEventId(), event.getNotificationId(), category);
        } else {
            log.warn("event=notification_delivery_lease_lost eventId={} stage=mark_terminal category={}",
                    event.getEventId(), category);
        }
        return false;
    }

    private int safeAttempt(NotificationDeliveryOutboxEvent event) {
        return ObjectUtils.isEmpty(event.getAttemptCount()) ? 1 : Math.max(1, event.getAttemptCount());
    }

    private int safeMaxAttempts(NotificationDeliveryOutboxEvent event) {
        return ObjectUtils.isEmpty(event.getMaxAttempts()) ? MAX_ATTEMPTS : event.getMaxAttempts();
    }

    private long retryDelaySeconds(int attempt) {
        return Math.min(900L, 15L * (1L << Math.min(6, Math.max(0, attempt - 1))));
    }




    private static class DeliveryException extends RuntimeException {
        private final String category;

        DeliveryException(String category, String message) {
            super(message);
            this.category = category;
        }

        String getCategory() {
            return category;
        }
    }
}
