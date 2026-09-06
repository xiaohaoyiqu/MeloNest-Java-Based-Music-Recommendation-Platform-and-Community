package com.haoran.music.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.haoran.music.service.UserSessionRevocationService;
import com.haoran.music.service.WebSocketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;








@Slf4j
@Service
public class RedisUserSessionRevocationService implements UserSessionRevocationService, MessageListener {

    public static final String REVOCATION_CHANNEL = "haoran:user-session:revoked:v1";

    private static final int MESSAGE_VERSION = 1;
    private static final int MAX_PAYLOAD_BYTES = 1024;
    private static final int MAX_REASON_LENGTH = 40;
    private static final int MAX_TRACKED_EVENT_IDS = 4096;
    private static final long MAX_EVENT_AGE_MILLIS = 5 * 60_000L;
    private static final long MAX_CLOCK_SKEW_MILLIS = 60_000L;
    private static final Pattern REASON_PATTERN = Pattern.compile("[a-z0-9_-]{1,40}");

    private final WebSocketService webSocketService;
    private final StringRedisTemplate redisTemplate;
    private final String instanceId = UUID.randomUUID().toString();
    private final Map<String, Long> processedEventIds = new ConcurrentHashMap<>();







    public RedisUserSessionRevocationService(WebSocketService webSocketService,
                                             StringRedisTemplate redisTemplate) {
        this.webSocketService = webSocketService;
        this.redisTemplate = redisTemplate;
    }







    @Override
    public void revokeWebSocketSessions(Long userId, String reason) {
        if (userId == null || userId <= 0) {
            return;
        }

        String normalizedReason = normalizeReason(reason);
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    revokeNow(userId, normalizedReason);
                }
            });
            return;
        }
        revokeNow(userId, normalizedReason);
    }







    @Override
    public void onMessage(Message message, byte[] pattern) {
        if (message == null || message.getBody() == null
                || message.getBody().length == 0 || message.getBody().length > MAX_PAYLOAD_BYTES) {
            log.warn("event=session_revocation_message_rejected reason=INVALID_SIZE");
            return;
        }

        try {
            JSONObject event = JSON.parseObject(new String(message.getBody(), StandardCharsets.UTF_8));
            if (!isValidEvent(event)) {
                log.warn("event=session_revocation_message_rejected reason=INVALID_SCHEMA");
                return;
            }
            if (instanceId.equals(event.getString("originInstanceId"))) {
                return;
            }
            if (!markEventProcessed(event.getString("eventId"), System.currentTimeMillis())) {
                log.debug("event=session_revocation_message_ignored reason=DUPLICATE_EVENT");
                return;
            }

            Long userId = event.getLong("userId");
            String reason = event.getString("reason");
            boolean disconnected = webSocketService.disconnectUser(userId, reason);
            log.info("event=session_revocation_message_applied userId={} disconnected={}",
                    userId, disconnected);
        } catch (RuntimeException e) {
            log.warn("event=session_revocation_message_rejected reason=PROCESSING_ERROR errorType={}",
                    e.getClass().getSimpleName());
        }
    }




    private void revokeNow(Long userId, String reason) {
        boolean disconnected = false;
        try {
            disconnected = webSocketService.disconnectUser(userId, reason);
        } catch (RuntimeException e) {
            log.warn("event=session_revocation_local_failed userId={} errorType={}",
                    userId, e.getClass().getSimpleName());
        }

        JSONObject event = new JSONObject();
        event.put("version", MESSAGE_VERSION);
        event.put("eventId", UUID.randomUUID().toString());
        event.put("originInstanceId", instanceId);
        event.put("userId", userId);
        event.put("reason", reason);
        event.put("occurredAtEpochMillis", System.currentTimeMillis());
        try {
            redisTemplate.convertAndSend(REVOCATION_CHANNEL, event.toJSONString());
            log.info("event=session_revocation_published userId={} localDisconnected={}",
                    userId, disconnected);
        } catch (RuntimeException e) {
            log.warn("event=session_revocation_publish_failed userId={} localDisconnected={} errorType={}",
                    userId, disconnected, e.getClass().getSimpleName());
        }
    }




    private boolean isValidEvent(JSONObject event) {
        if (event == null || !Integer.valueOf(MESSAGE_VERSION).equals(event.getInteger("version"))) {
            return false;
        }
        Long userId = event.getLong("userId");
        Long occurredAt = event.getLong("occurredAtEpochMillis");
        String reason = event.getString("reason");
        long now = System.currentTimeMillis();
        return userId != null && userId > 0
                && occurredAt != null && occurredAt > 0
                && occurredAt >= now - MAX_EVENT_AGE_MILLIS
                && occurredAt <= now + MAX_CLOCK_SKEW_MILLIS
                && REASON_PATTERN.matcher(reason == null ? "" : reason).matches()
                && isUuid(event.getString("eventId"))
                && isUuid(event.getString("originInstanceId"));
    }




    private boolean markEventProcessed(String eventId, long now) {
        Long existing = processedEventIds.putIfAbsent(eventId, now);
        if (existing != null) {
            return false;
        }
        if (processedEventIds.size() > MAX_TRACKED_EVENT_IDS) {
            long expiredBefore = now - MAX_EVENT_AGE_MILLIS - MAX_CLOCK_SKEW_MILLIS;
            processedEventIds.entrySet().removeIf(entry -> entry.getValue() < expiredBefore);
            int overflow = processedEventIds.size() - MAX_TRACKED_EVENT_IDS;
            if (overflow > 0) {
                processedEventIds.keySet().stream().limit(overflow)
                        .forEach(processedEventIds::remove);
            }
        }
        return true;
    }




    private boolean isUuid(String value) {
        if (value == null || value.length() != 36) {
            return false;
        }
        try {
            return value.equals(UUID.fromString(value).toString());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }




    private String normalizeReason(String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            return "unspecified";
        }
        String normalized = reason.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_\\-]", "_");
        return normalized.length() <= MAX_REASON_LENGTH
                ? normalized : normalized.substring(0, MAX_REASON_LENGTH);
    }
}
