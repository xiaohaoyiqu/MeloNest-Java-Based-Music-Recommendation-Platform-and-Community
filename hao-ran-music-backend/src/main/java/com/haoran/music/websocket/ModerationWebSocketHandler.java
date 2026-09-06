


package com.haoran.music.websocket;

import com.alibaba.fastjson2.JSON;
import com.haoran.music.common.config.SecurityConfig;
import com.haoran.music.common.util.JwtUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.User;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.OnlineStatusService;
import com.haoran.music.service.WebSocketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;




@Slf4j
@Component
public class ModerationWebSocketHandler extends TextWebSocketHandler implements WebSocketService {

    private static final long DEFAULT_HEARTBEAT_TIMEOUT_MILLIS = 90_000L;
    private static final int MESSAGE_VERSION = 1;

    private final Map<Long, WebSocketSession> userSessions = new ConcurrentHashMap<>();
    private final Map<String, Long> sessionUserMap = new ConcurrentHashMap<>();
    private final Map<String, Long> sessionHeartbeatMillis = new ConcurrentHashMap<>();
    private final Map<String, Long> sessionAccountCheckMillis = new ConcurrentHashMap<>();
    private final OnlineStatusService onlineStatusService;
    private final JwtUtils jwtUtils;
    private final SecurityConfig securityConfig;

    @Autowired(required = false)
    private UserMapper userMapper;

    @Value("${security.websocket.heartbeat-timeout-ms:90000}")
    private long heartbeatTimeoutMillis = DEFAULT_HEARTBEAT_TIMEOUT_MILLIS;

    @Value("${security.websocket.account-recheck-interval-ms:30000}")
    private long accountRecheckIntervalMillis = 30_000L;

    @Autowired
    public ModerationWebSocketHandler(OnlineStatusService onlineStatusService,
                                      JwtUtils jwtUtils,
                                      SecurityConfig securityConfig) {
        this(onlineStatusService, jwtUtils, securityConfig, DEFAULT_HEARTBEAT_TIMEOUT_MILLIS);
    }

    ModerationWebSocketHandler(OnlineStatusService onlineStatusService,
                               JwtUtils jwtUtils,
                               SecurityConfig securityConfig,
                               long heartbeatTimeoutMillis) {
        this.onlineStatusService = onlineStatusService;
        this.jwtUtils = jwtUtils;
        this.securityConfig = securityConfig;
        this.heartbeatTimeoutMillis = Math.max(1_000L, heartbeatTimeoutMillis);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.debug("event=websocket_connection_established sessionId={}", session.getId());

        Long userId = extractUserId(session);
        if (userId == null) {
            log.warn("event=websocket_connection_rejected reason=USER_ID_MISSING sessionId={}", session.getId());
            session.close();
            return;
        }

        WebSocketSession previousSession = userSessions.put(userId, session);
        sessionUserMap.put(session.getId(), userId);
        long connectedAt = System.currentTimeMillis();
        sessionHeartbeatMillis.put(session.getId(), connectedAt);
        sessionAccountCheckMillis.put(session.getId(), connectedAt);
        closePreviousSession(userId, previousSession, session);
        onlineStatusService.recordWebSocketConnected(userId, session.getId(), buildSessionMetadata(session));
        if (!sendConnectMessage(session, userId)) {
            return;
        }

        log.debug("event=websocket_user_connected userId={} onlineCount={}", userId, userSessions.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long currentUserId = sessionUserMap.get(session.getId());
        if (currentUserId == null || userSessions.get(currentUserId) != session) {
            log.warn("event=websocket_message_rejected reason=SESSION_REVOKED sessionId={}", session.getId());
            if (session.isOpen()) {
                session.close(CloseStatus.POLICY_VIOLATION.withReason("session_revoked"));
            }
            return;
        }
        if (!isAccountSessionAllowed(currentUserId, session)) {
            return;
        }

        String payload = message.getPayload();
        log.debug("event=websocket_message_received sessionId={} payloadLength={}",
                session.getId(), payload == null ? 0 : payload.length());
        sessionHeartbeatMillis.put(session.getId(), System.currentTimeMillis());

        try {
            Map<String, Object> messageData = JSON.parseObject(payload, Map.class);
            String type = (String) messageData.get("type");

            if ("ping".equals(type) || "presence".equals(type)) {
                Long userId = sessionUserMap.get(session.getId());
                if (userId != null) {
                    onlineStatusService.recordWebSocketHeartbeat(userId, messageData);
                }
                if ("ping".equals(type)) {
                    sendPongMessage(session, messageData);
                }
                return;
            }

            log.debug("event=websocket_message_ignored sessionId={}", session.getId());
        } catch (Exception e) {
            log.warn("event=websocket_message_rejected sessionId={} errorType={}",
                    session.getId(), e.getClass().getSimpleName());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.debug("event=websocket_connection_closed sessionId={} closeCode={}", session.getId(), status.getCode());

        Long userId = sessionUserMap.remove(session.getId());
        sessionHeartbeatMillis.remove(session.getId());
        sessionAccountCheckMillis.remove(session.getId());
        if (userId != null) {
            boolean currentSessionRemoved = removeSessionIfCurrent(userId, session);
            if (currentSessionRemoved) {
                onlineStatusService.recordWebSocketDisconnected(userId, status.toString());
            } else {
                log.debug("event=websocket_stale_close_ignored userId={} sessionId={}", userId, session.getId());
            }
            log.debug("event=websocket_user_disconnected userId={} onlineCount={}", userId, userSessions.size());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("event=websocket_transport_error sessionId={} errorType={}",
                session.getId(), exception.getClass().getSimpleName());

        Long userId = sessionUserMap.remove(session.getId());
        sessionHeartbeatMillis.remove(session.getId());
        sessionAccountCheckMillis.remove(session.getId());
        if (userId != null) {
            boolean currentSessionRemoved = removeSessionIfCurrent(userId, session);
            if (currentSessionRemoved) {
                onlineStatusService.recordWebSocketDisconnected(userId, "transport_error:" + exception.getClass().getSimpleName());
            }
        }

        if (session.isOpen()) {
            session.close();
        }
    }

    @Override
    public void sendToUser(Long userId, Object message) {
        trySendToUser(userId, message);
    }








    @Override
    public boolean trySendToUser(Long userId, Object message) {
        if (userId == null || message == null) {
            return false;
        }

        WebSocketSession session = userSessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                String jsonMessage = serializeOutboundMessage(message);
                sendText(session, new TextMessage(jsonMessage));
                onlineStatusService.recordWebSocketMessageSent(userId);
                log.debug("event=websocket_message_sent userId={}", userId);
                return true;
            } catch (IOException e) {
                log.warn("event=websocket_send_failed userId={} errorType={}",
                        userId, e.getClass().getSimpleName());
                sessionUserMap.remove(session.getId());
                sessionHeartbeatMillis.remove(session.getId());
                sessionAccountCheckMillis.remove(session.getId());
                if (removeSessionIfCurrent(userId, session)) {
                    onlineStatusService.recordWebSocketDisconnected(userId, "send_error");
                }
                return false;
            }
        } else {
            log.debug("event=websocket_message_not_delivered userId={} reason=SESSION_UNAVAILABLE", userId);
            return false;
        }
    }

    @Override
    public void sendToUser(Long userId, String message) {
        sendToUser(userId, (Object) message);
    }

    @Override
    public void broadcast(Object message) {
        if (message == null) {
            return;
        }

        String jsonMessage = serializeOutboundMessage(message);
        TextMessage textMessage = new TextMessage(jsonMessage);

        userSessions.forEach((userId, session) -> {
            if (!session.isOpen()) {
                removeFailedSession(userId, session, "broadcast_closed");
                return;
            }
            try {
                sendText(session, textMessage);
                onlineStatusService.recordWebSocketMessageSent(userId);
            } catch (IOException e) {
                log.warn("event=websocket_broadcast_send_failed userId={} errorType={}",
                        userId, e.getClass().getSimpleName());
                removeFailedSession(userId, session, "broadcast_error");
            }
        });

        log.debug("event=websocket_broadcast_completed onlineCount={}", userSessions.size());
    }

    @Override
    public int getOnlineUserCount() {
        return userSessions.size();
    }

    @Override
    public boolean isUserOnline(Long userId) {
        if (userId == null) {
            return false;
        }
        WebSocketSession session = userSessions.get(userId);
        return session != null && session.isOpen();
    }





    @Override
    public boolean disconnectUser(Long userId, String reason) {
        if (userId == null) {
            return false;
        }
        WebSocketSession session = userSessions.remove(userId);
        if (session == null) {
            return false;
        }

        sessionUserMap.remove(session.getId());
        sessionHeartbeatMillis.remove(session.getId());
        sessionAccountCheckMillis.remove(session.getId());
        recordDisconnectedSafely(userId, "session_revoked");
        if (session.isOpen()) {
            try {
                session.close(CloseStatus.POLICY_VIOLATION.withReason("session_revoked"));
            } catch (IOException e) {
                log.warn("event=websocket_session_revoke_close_failed userId={} sessionId={} errorType={}",
                        userId, session.getId(), e.getClass().getSimpleName());
            }
        }
        log.info("event=websocket_session_revoked userId={} reason={}", userId, normalizeRevokeReason(reason));
        return true;
    }

    private String normalizeRevokeReason(String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            return "unspecified";
        }
        String normalized = reason.trim().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9_\\-]", "_");
        return normalized.length() <= 40 ? normalized : normalized.substring(0, 40);
    }

    private void closePreviousSession(Long userId, WebSocketSession previousSession, WebSocketSession currentSession) {
        if (previousSession == null || previousSession.getId().equals(currentSession.getId())) {
            return;
        }
        sessionUserMap.remove(previousSession.getId());
        sessionHeartbeatMillis.remove(previousSession.getId());
        sessionAccountCheckMillis.remove(previousSession.getId());
        if (previousSession.isOpen()) {
            try {
                previousSession.close(CloseStatus.NORMAL.withReason("replaced_by_new_connection"));
            } catch (IOException e) {
                log.warn("event=websocket_replaced_session_close_failed userId={} sessionId={} errorType={}",
                        userId, previousSession.getId(), e.getClass().getSimpleName());
            }
        }
        log.debug("event=websocket_session_replaced userId={} previousSessionId={} currentSessionId={}",
                userId, previousSession.getId(), currentSession.getId());
    }

    private void removeFailedSession(Long userId, WebSocketSession session, String reason) {
        sessionUserMap.remove(session.getId());
        sessionHeartbeatMillis.remove(session.getId());
        sessionAccountCheckMillis.remove(session.getId());
        if (removeSessionIfCurrent(userId, session)) {
            onlineStatusService.recordWebSocketDisconnected(userId, reason);
        }
    }

    private boolean removeSessionIfCurrent(Long userId, WebSocketSession session) {
        WebSocketSession current = userSessions.get(userId);
        if (current != null && current.getId().equals(session.getId())) {
            userSessions.remove(userId);
            return true;
        }
        return false;
    }





    private boolean isAccountSessionAllowed(Long userId, WebSocketSession session) {
        if (userMapper == null) {
            return true;
        }
        long now = System.currentTimeMillis();
        Long lastCheckedAt = sessionAccountCheckMillis.get(session.getId());
        long interval = Math.max(1_000L, accountRecheckIntervalMillis);
        if (lastCheckedAt != null && now - lastCheckedAt < interval) {
            return true;
        }

        try {
            User user = userMapper.selectAccountAccessStateById(userId);
            if (!UserAccountStatusUtil.canAuthenticate(user)) {
                disconnectUser(userId, "account_recheck_restricted");
                return false;
            }
            sessionAccountCheckMillis.put(session.getId(), now);
            return true;
        } catch (RuntimeException e) {
            log.warn("event=websocket_account_recheck_failed userId={} errorType={}",
                    userId, e.getClass().getSimpleName());
            disconnectUser(userId, "account_recheck_failed");
            return false;
        }
    }




    private void recordDisconnectedSafely(Long userId, String reason) {
        try {
            onlineStatusService.recordWebSocketDisconnected(userId, reason);
        } catch (RuntimeException e) {
            log.warn("event=websocket_disconnect_status_record_failed userId={} errorType={}",
                    userId, e.getClass().getSimpleName());
        }
    }

    private Long extractUserId(WebSocketSession session) {
        Object userIdAttr = session.getAttributes().get("userId");
        if (userIdAttr != null) {
            if (userIdAttr instanceof Long) {
                return (Long) userIdAttr;
            }
            try {
                return Long.parseLong(userIdAttr.toString());
            } catch (NumberFormatException e) {
                log.warn("event=websocket_user_attribute_invalid errorType={}",
                        e.getClass().getSimpleName());
            }
        }

        if (securityConfig == null || !securityConfig.isQueryTokenEnabled()) {
            return null;
        }
        String token = extractQueryParam(session.getUri(), "token");
        if (token == null || token.isEmpty() || jwtUtils == null || !jwtUtils.validateToken(token)) {
            return null;
        }
        try {
            return jwtUtils.getUserIdFromToken(token);
        } catch (Exception e) {
            log.warn("event=websocket_token_user_resolution_failed errorType={}",
                    e.getClass().getSimpleName());
            return null;
        }
    }

    private String extractQueryParam(URI uri, String name) {
        if (uri == null || uri.getRawQuery() == null) {
            return null;
        }
        for (String part : uri.getRawQuery().split("&")) {
            int separator = part.indexOf('=');
            if (separator <= 0 || !name.equals(part.substring(0, separator))) {
                continue;
            }
            try {
                return URLDecoder.decode(part.substring(separator + 1), "UTF-8");
            } catch (Exception e) {
                log.warn("event=websocket_query_parameter_decode_failed errorType={}",
                        e.getClass().getSimpleName());
                return null;
            }
        }
        return null;
    }

    private Map<String, Object> buildSessionMetadata(WebSocketSession session) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        URI uri = session.getUri();
        if (uri != null) {
            metadata.put("path", uri.getPath());
        }
        metadata.put("sessionId", session.getId());
        return metadata;
    }

    private void sendText(WebSocketSession session, TextMessage message) throws IOException {
        synchronized (session) {
            if (!session.isOpen()) {
                throw new IOException("WebSocket session closed");
            }
            try {
                session.sendMessage(message);
            } catch (IllegalStateException e) {
                throw new IOException("WebSocket session closed during send", e);
            }
        }
    }





    @Scheduled(fixedDelayString = "${security.websocket.cleanup-interval-ms:30000}")
    public void cleanupStaleSessions() {
        cleanupStaleSessions(System.currentTimeMillis());
    }

    void cleanupStaleSessions(long nowMillis) {
        for (Map.Entry<String, Long> entry : sessionHeartbeatMillis.entrySet()) {
            Long lastHeartbeat = entry.getValue();
            if (lastHeartbeat == null || nowMillis - lastHeartbeat <= heartbeatTimeoutMillis) {
                continue;
            }

            String sessionId = entry.getKey();
            sessionHeartbeatMillis.remove(sessionId, lastHeartbeat);
            sessionAccountCheckMillis.remove(sessionId);
            Long userId = sessionUserMap.remove(sessionId);
            if (userId == null) {
                continue;
            }

            WebSocketSession session = userSessions.get(userId);
            boolean currentSessionRemoved = session != null && sessionId.equals(session.getId())
                    && removeSessionIfCurrent(userId, session);
            if (currentSessionRemoved) {
                onlineStatusService.recordWebSocketDisconnected(userId, "heartbeat_timeout");
            }

            if (session != null && session.isOpen()) {
                try {
                    session.close(CloseStatus.SESSION_NOT_RELIABLE.withReason("heartbeat_timeout"));
                } catch (IOException e) {
                log.debug("event=websocket_stale_session_close_failed sessionId={} errorType={}",
                        sessionId, e.getClass().getSimpleName());
                }
            }
        }
    }

    private boolean sendConnectMessage(WebSocketSession session, Long userId) {
        try {
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("type", "connected");
            message.put("userId", userId);
            sendText(session, new TextMessage(serializeOutboundMessage(message)));
            return true;
        } catch (IOException e) {
            log.warn("event=websocket_connect_message_failed errorType={}", e.getClass().getSimpleName());
            removeFailedSession(userId, session, "connect_send_error");
            return false;
        }
    }

    private void sendPongMessage(WebSocketSession session, Map<String, Object> pingMessage) {
        try {
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("type", "pong");
            message.put("clientTimestamp", pingMessage.get("clientTimestamp"));
            sendText(session, new TextMessage(serializeOutboundMessage(message)));
        } catch (IOException e) {
            log.warn("event=websocket_pong_send_failed errorType={}", e.getClass().getSimpleName());
        }
    }

    public void sendModerationUpdate(Long userId, Long moderationId, String status) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", "moderation_update");
        message.put("moderationId", moderationId);
        message.put("status", status);
        sendToUser(userId, message);
    }

    public void sendPendingCountUpdate(Long userId, Long pendingCount) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", "pending_count");
        message.put("count", pendingCount);
        sendToUser(userId, message);
    }

    private String serializeOutboundMessage(Object message) {
        Map<String, Object> source = new LinkedHashMap<>();
        if (message instanceof String) {
            try {
                Object parsed = JSON.parse((String) message);
                if (parsed instanceof Map) {
                    source.putAll((Map<String, Object>) parsed);
                } else {
                    source.put("data", message);
                }
            } catch (Exception ignored) {
                source.put("data", message);
            }
        } else if (message instanceof Map) {
            source.putAll((Map<String, Object>) message);
        } else {
            source.put("data", message);
        }

        String type = source.get("type") == null ? "message" : String.valueOf(source.get("type"));
        source.put("version", MESSAGE_VERSION);
        source.put("type", type);
        source.putIfAbsent("messageId", UUID.randomUUID().toString());
        source.putIfAbsent("traceId", UUID.randomUUID().toString());
        source.putIfAbsent("timestamp", System.currentTimeMillis());

        if (!source.containsKey("data")) {
            Map<String, Object> payload = new LinkedHashMap<>(source);
            payload.remove("version");
            payload.remove("type");
            payload.remove("messageId");
            payload.remove("traceId");
            payload.remove("timestamp");
            if (!payload.isEmpty()) {
                source.put("data", payload);
            }
        }
        return JSON.toJSONString(source);
    }
}
