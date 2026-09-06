   
                      
   
package com.haoran.music.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.haoran.music.common.config.WorkTimeConfig;
import com.haoran.music.entity.User;
import com.haoran.music.enums.UserRole;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.common.util.UserAccountStatusUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

   
                                                     
   
@Slf4j
@Service
public class OnlineStatusService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private WorkTimeConfig workTimeConfig;

    private static final String ONLINE_PREFIX = "user:online:";
    private static final String LAST_ACTIVITY_PREFIX = "user:last_activity:";
    private static final String WS_CONNECTED_PREFIX = "user:ws:connected:";
    private static final String WS_HEARTBEAT_PREFIX = "user:ws:heartbeat:";
    private static final String WS_META_PREFIX = "user:ws:meta:";
    private static final String DB_SYNC_PREFIX = "user:online:db_sync:";
    private static final int META_VALUE_MAX_LENGTH = 256;
    private static final int DB_SYNC_INTERVAL_SECONDS = 300;
    private static final long REDIS_SCAN_COUNT = 1000L;

       
                                                                                            
       
    public void updateActivity(Long userId) {
        if (userId == null) {
            return;
        }
        if (!workTimeConfig.isWorkTime()) {
            log.debug("Ignore reviewer online update outside work time: userId={}", userId);
            return;
        }
        updateOnlineActivity(userId, "http", null);
    }

       
                                                                            
       
    public void updateUserOnlineActivity(Long userId) {
        if (userId == null) {
            return;
        }
        updateOnlineActivity(userId, "http", null);
    }

    public void recordWebSocketConnected(Long userId, String sessionId, Map<String, Object> metadata) {
        if (userId == null) {
            return;
        }
        Map<String, Object> presence = copyMetadata(metadata);
        presence.put("sessionId", sessionId);
        presence.put("source", "websocket");
        presence.put("connectionStatus", "connected");
        presence.put("lastConnectTime", LocalDateTime.now().toString());
        updateOnlineActivity(userId, "websocket", presence, true);
    }

    public void recordWebSocketHeartbeat(Long userId, Map<String, Object> metadata) {
        if (userId == null) {
            return;
        }
        Map<String, Object> presence = copyMetadata(metadata);
        long nowMillis = System.currentTimeMillis();
        Long previousMillis = parseLong(redisTemplate.opsForHash().get(metaKey(userId), "lastHeartbeatMillis"));
        if (previousMillis != null && previousMillis > 0) {
            presence.put("heartbeatIntervalSeconds", Math.max(0, (nowMillis - previousMillis) / 1000));
        }
        presence.put("lastHeartbeatMillis", nowMillis);
        presence.put("lastHeartbeatTime", LocalDateTime.now().toString());
        presence.put("source", "websocket");
        presence.put("connectionStatus", "connected");
        updateOnlineActivity(userId, "websocket", presence, false);
    }

    public void recordWebSocketDisconnected(Long userId, String reason) {
        if (userId == null) {
            return;
        }
        redisTemplate.delete(wsConnectedKey(userId));
        redisTemplate.delete(wsHeartbeatKey(userId));
        Map<String, Object> presence = new HashMap<>();
        presence.put("connectionStatus", "disconnected");
        presence.put("disconnectReason", reason);
        presence.put("lastDisconnectTime", LocalDateTime.now().toString());
        writePresenceMetadata(userId, presence);
    }

    public void recordWebSocketMessageSent(Long userId) {
        if (userId == null) {
            return;
        }
        String metaKey = metaKey(userId);
        redisTemplate.opsForHash().increment(metaKey, "messagesSent", 1);
        redisTemplate.opsForHash().put(metaKey, "lastPushTime", LocalDateTime.now().toString());
        redisTemplate.expire(metaKey, getPresenceTtlMinutes(), TimeUnit.MINUTES);
    }

    private void updateOnlineActivity(Long userId, String source, Map<String, Object> metadata) {
        updateOnlineActivity(userId, source, metadata, false);
    }

    private void updateOnlineActivity(Long userId, String source, Map<String, Object> metadata, boolean forceDatabaseSync) {
        if (!UserAccountStatusUtil.canInteract(userMapper.selectById(userId))) {
            clearUnavailablePresence(userId);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        int activeTtl = getActiveTtlMinutes();
        int presenceTtl = getPresenceTtlMinutes();

        redisTemplate.opsForValue().set(lastActivityKey(userId), now.toString(),
                workTimeConfig.getOfflineTimeoutMinutes(), TimeUnit.MINUTES);
        redisTemplate.opsForValue().set(onlineKey(userId), "1", activeTtl, TimeUnit.MINUTES);

        if ("websocket".equals(source)) {
            redisTemplate.opsForValue().set(wsConnectedKey(userId), "1", presenceTtl, TimeUnit.MINUTES);
            redisTemplate.opsForValue().set(wsHeartbeatKey(userId), now.toString(), presenceTtl, TimeUnit.MINUTES);
            writePresenceMetadata(userId, metadata);
        }

        boolean shouldPersist = forceDatabaseSync || acquireDatabaseSyncSlot(userId, now);
        if (shouldPersist) {
            markDatabaseSynced(userId, now);
            userMapper.update(null,
                    new LambdaUpdateWrapper<User>()
                            .eq(User::getId, userId)
                            .set(User::getIsOnline, 1)
                            .set(User::getLastOnlineTime, now)
                            .set(User::getLastActiveTime, now)
            );
            log.debug("User presence persisted: userId={}, source={}, time={}", userId, source, now);
        } else {
            log.debug("User presence refreshed in redis: userId={}, source={}, time={}", userId, source, now);
        }
    }

    private boolean acquireDatabaseSyncSlot(Long userId, LocalDateTime now) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                dbSyncKey(userId), now.toString(), DB_SYNC_INTERVAL_SECONDS, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(acquired);
    }

    private void markDatabaseSynced(Long userId, LocalDateTime now) {
        redisTemplate.opsForValue().set(dbSyncKey(userId), now.toString(),
                DB_SYNC_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public void userOffline(Long userId) {
        if (userId == null) {
            return;
        }

        redisTemplate.delete(onlineKey(userId));
        redisTemplate.delete(lastActivityKey(userId));
        redisTemplate.delete(dbSyncKey(userId));
        redisTemplate.delete(wsConnectedKey(userId));
        redisTemplate.delete(wsHeartbeatKey(userId));
        recordWebSocketDisconnected(userId, "logout");

        userMapper.update(null,
                new LambdaUpdateWrapper<User>()
                        .eq(User::getId, userId)
                        .set(User::getIsOnline, 0)
                        .set(User::getLastOnlineTime, LocalDateTime.now())
                        .set(User::getLastActiveTime, LocalDateTime.now())
        );

        log.info("User offline: userId={}", userId);
    }

    public boolean isWorkTime() {
        return workTimeConfig.isWorkTime();
    }

       
                                                                                            
       
    public boolean isOnline(Long userId) {
        if (userId == null || !workTimeConfig.isWorkTime()) {
            return false;
        }
        return isUserOnline(userId);
    }

       
                                                                    
       
    public boolean isUserOnline(Long userId) {
        if (userId == null) {
            return false;
        }
        return "1".equals(redisTemplate.opsForValue().get(onlineKey(userId))) || isWebSocketOnline(userId);
    }

    public boolean isWebSocketOnline(Long userId) {
        if (userId == null) {
            return false;
        }
        return "1".equals(redisTemplate.opsForValue().get(wsConnectedKey(userId)))
                || redisTemplate.opsForValue().get(wsHeartbeatKey(userId)) != null;
    }

       
                                                                        
       
    public boolean isActive(Long userId) {
        if (userId == null || !workTimeConfig.isWorkTime()) {
            return false;
        }
        return isUserActive(userId);
    }

    public boolean isUserActive(Long userId) {
        return getLastActivityTime(userId) != null;
    }

    public Map<String, Object> getUserOnlineStatus(Long userId) {
        Map<String, Object> status = new HashMap<>();
        User user = userId == null ? null : userMapper.selectById(userId);
        Map<String, String> metadata = readPresenceMetadata(userId);
        LocalDateTime lastActivityTime = getLastActivityTime(userId);
        LocalDateTime lastActiveTime = user == null ? null : user.getLastActiveTime();
        LocalDateTime lastOnlineTime = user == null ? null : user.getLastOnlineTime();
        boolean webSocketOnline = isWebSocketOnline(userId);
        boolean online = isUserOnline(userId);
        boolean active = lastActivityTime != null;
        boolean pageVisible = parseBoolean(metadata.get("pageVisible"));
        String visibilityState = metadata.get("visibilityState");
        String heartbeatInterval = metadata.get("heartbeatIntervalSeconds");

        status.put("userId", userId);
        status.put("isOnline", online);
        status.put("isActive", active);
        status.put("webSocketOnline", webSocketOnline);
        status.put("onlineStatus", online ? "online" : "offline");
        status.put("activityStatus", resolveActivityStatus(active, lastActivityTime, lastActiveTime));
        status.put("presenceStatus", resolvePresenceStatus(online, active, webSocketOnline, pageVisible, visibilityState, metadata));
        status.put("connectionStatus", resolveConnectionStatus(online, webSocketOnline, metadata));
        status.put("connectionQuality", resolveConnectionQuality(webSocketOnline, heartbeatInterval));
        status.put("heartbeatIntervalSeconds", parseLong(heartbeatInterval));
        status.put("pageVisible", pageVisible);
        status.put("visibilityState", visibilityState);
        status.put("route", metadata.get("route"));
        status.put("focused", parseBoolean(metadata.get("focused")));
        status.put("navigatorOnline", parseBoolean(metadata.get("navigatorOnline")));
        status.put("playerState", metadata.get("playerState"));
        status.put("playing", parseBoolean(metadata.get("playing")));
        status.put("currentSongId", metadata.get("currentSongId"));
        status.put("lastHeartbeatTime", metadata.get("lastHeartbeatTime"));
        status.put("lastPushTime", metadata.get("lastPushTime"));
        status.put("messagesSent", parseLong(metadata.get("messagesSent")));
        status.put("lastActivityTime", lastActivityTime);
        status.put("lastActiveTime", lastActiveTime);
        status.put("lastOnlineTime", lastOnlineTime);
        status.put("workTime", workTimeConfig.isWorkTime());
        return status;
    }

    public List<Long> getOnlineModerators() {
        if (!workTimeConfig.isWorkTime()) {
            return Collections.emptyList();
        }

        Set<String> keys = scanKeys(ONLINE_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> onlineIds = keys.stream()
                .map(key -> {
                    try {
                        return Long.parseLong(key.substring(ONLINE_PREFIX.length()));
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .filter(this::isOnline)
                .collect(Collectors.toList());
        Set<Long> moderatorIds = loadModeratorIds(onlineIds);
        return onlineIds.stream()
                .filter(moderatorIds::contains)
                .collect(Collectors.toList());
    }

    public List<Long> getActiveModerators() {
        if (!workTimeConfig.isWorkTime()) {
            return Collections.emptyList();
        }

        Set<Long> activeIds = new HashSet<>();
        activeIds.addAll(getOnlineModerators());

        Set<String> activityKeys = scanKeys(LAST_ACTIVITY_PREFIX + "*");
        if (activityKeys != null && !activityKeys.isEmpty()) {
            LocalDateTime expireThreshold = LocalDateTime.now()
                    .minusMinutes(workTimeConfig.getOfflineTimeoutMinutes());

            for (String key : activityKeys) {
                try {
                    Long userId = Long.parseLong(key.substring(LAST_ACTIVITY_PREFIX.length()));
                    String lastActivity = redisTemplate.opsForValue().get(key);
                    if (lastActivity != null) {
                        LocalDateTime lastTime = LocalDateTime.parse(lastActivity);
                        if (lastTime.isAfter(expireThreshold)) {
                            activeIds.add(userId);
                        }
                    }
                } catch (Exception e) {
                    log.debug("Ignore malformed activity key: key={}", key);
                }
            }
        }

        Set<Long> moderatorIds = loadModeratorIds(activeIds);
        activeIds.retainAll(moderatorIds);
        return new ArrayList<>(activeIds);
    }

    public List<User> getOnlineModeratorDetails() {
        List<Long> onlineIds = getOnlineModerators();
        if (onlineIds.isEmpty()) {
            return Collections.emptyList();
        }

        return userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .in(User::getId, onlineIds)
                        .eq(User::getDeleted, 0)
        );
    }

    public List<User> getActiveModeratorDetails() {
        List<Long> activeIds = getActiveModerators();
        if (activeIds.isEmpty()) {
            return Collections.emptyList();
        }

        return userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .in(User::getId, activeIds)
                        .eq(User::getDeleted, 0)
        );
    }

    private boolean canModerate(User user) {
        if (user == null) {
            return false;
        }
        UserRole role = UserRole.fromCode(user.getRole());
        if (role == UserRole.MODERATOR || role == UserRole.ADMIN || role == UserRole.SUPER_ADMIN) {
            return true;
        }
        return Integer.valueOf(1).equals(user.getIsModerator())
                && "active".equals(user.getModeratorStatus());
    }

    private Set<Long> loadModeratorIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptySet();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .filter(this::canModerate)
                .map(User::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public void batchUpdateActivity(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        for (Long userId : userIds) {
            updateUserOnlineActivity(userId);
        }
    }

    public void refreshModeratorOnlineStatus() {
        List<User> moderators = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .eq(User::getDeleted, 0)
        ).stream()
                .filter(this::canModerate)
                .collect(Collectors.toList());

        for (User moderator : moderators) {
            if (isOnline(moderator.getId())) {
                if (!Integer.valueOf(1).equals(moderator.getIsOnline())) {
                    moderator.setIsOnline(1);
                    userMapper.updateById(moderator);
                }
            } else if (!Integer.valueOf(0).equals(moderator.getIsOnline())) {
                moderator.setIsOnline(0);
                userMapper.updateById(moderator);
            }
        }

        log.info("Refreshed moderator online status: count={}", moderators.size());
    }

    public void setOnlineStatus(Long userId, boolean online) {
        if (online) {
            updateUserOnlineActivity(userId);
        } else {
            userOffline(userId);
        }
    }

    public void cleanExpiredOnlineStatus() {
        int cleanedCount = 0;
        Set<String> keys = scanKeys(ONLINE_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            List<Long> redisOnlineUserIds = keys.stream()
                    .map(key -> {
                        try {
                            return Long.parseLong(key.substring(ONLINE_PREFIX.length()));
                        } catch (Exception e) {
                            log.debug("Ignore malformed online key: key={}", key);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            Map<Long, User> usersById = redisOnlineUserIds.isEmpty()
                    ? Collections.emptyMap()
                    : userMapper.selectBatchIds(redisOnlineUserIds).stream()
                    .filter(user -> user.getId() != null)
                    .collect(Collectors.toMap(User::getId, user -> user, (left, right) -> left));
            for (Long userId : redisOnlineUserIds) {
                User user = usersById.get(userId);
                if (user != null && Integer.valueOf(0).equals(user.getIsOnline())) {
                    redisTemplate.delete(onlineKey(userId));
                    cleanedCount++;
                }
            }
        }

        List<User> onlineUsers = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .eq(User::getDeleted, 0)
                        .eq(User::getIsOnline, 1)
                        .last("LIMIT 1000")
        );
        for (User user : onlineUsers) {
            if (user != null && !isUserOnline(user.getId())) {
                userMapper.update(null,
                        new LambdaUpdateWrapper<User>()
                                .eq(User::getId, user.getId())
                                .set(User::getIsOnline, 0)
                );
                cleanedCount++;
            }
        }

        if (cleanedCount > 0) {
            log.info("Cleaned expired online status: count={}", cleanedCount);
        }
    }

    private Set<String> scanKeys(String pattern) {
        Set<String> keys = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions()
                .match(pattern)
                .count(REDIS_SCAN_COUNT)
                .build();

        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        } catch (Exception e) {
            log.warn("event=online_status_redis_key_scan_failed errorType={}",
                    e.getClass().getSimpleName());
        }
        return keys;
    }

    private void writePresenceMetadata(Long userId, Map<String, Object> metadata) {
        if (userId == null || metadata == null || metadata.isEmpty()) {
            return;
        }
        Map<String, String> safe = new HashMap<>();
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            safe.put(entry.getKey(), truncate(String.valueOf(entry.getValue())));
        }
        if (!safe.isEmpty()) {
            redisTemplate.opsForHash().putAll(metaKey(userId), safe);
            redisTemplate.expire(metaKey(userId), getPresenceTtlMinutes(), TimeUnit.MINUTES);
        }
    }

    private void clearUnavailablePresence(Long userId) {
        redisTemplate.delete(onlineKey(userId));
        redisTemplate.delete(lastActivityKey(userId));
        redisTemplate.delete(dbSyncKey(userId));
        redisTemplate.delete(wsConnectedKey(userId));
        redisTemplate.delete(wsHeartbeatKey(userId));
        redisTemplate.delete(metaKey(userId));
        userMapper.update(null,
                new LambdaUpdateWrapper<User>()
                        .eq(User::getId, userId)
                        .set(User::getIsOnline, 0));
    }

    private Map<String, String> readPresenceMetadata(Long userId) {
        if (userId == null) {
            return Collections.emptyMap();
        }
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(metaKey(userId));
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new HashMap<>();
        raw.forEach((key, value) -> {
            if (key != null && value != null) {
                result.put(String.valueOf(key), String.valueOf(value));
            }
        });
        return result;
    }

    private LocalDateTime getLastActivityTime(Long userId) {
        if (userId == null) {
            return null;
        }
        String lastActivity = redisTemplate.opsForValue().get(lastActivityKey(userId));
        if (lastActivity == null) {
            return null;
        }
        try {
            LocalDateTime lastTime = LocalDateTime.parse(lastActivity);
            LocalDateTime expireTime = lastTime.plusMinutes(workTimeConfig.getOfflineTimeoutMinutes());
            return LocalDateTime.now().isBefore(expireTime) ? lastTime : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveActivityStatus(boolean active, LocalDateTime lastActivityTime, LocalDateTime lastActiveTime) {
        if (active) {
            return "active";
        }
        LocalDateTime referenceTime = lastActiveTime != null ? lastActiveTime : lastActivityTime;
        if (referenceTime == null) {
            return "unknown";
        }
        LocalDateTime now = LocalDateTime.now();
        if (referenceTime.isAfter(now.minusDays(1))) {
            return "recent";
        }
        if (referenceTime.isAfter(now.minusDays(7))) {
            return "quiet";
        }
        return "inactive";
    }

    private String resolvePresenceStatus(boolean online,
                                         boolean active,
                                         boolean webSocketOnline,
                                         boolean pageVisible,
                                         String visibilityState,
                                         Map<String, String> metadata) {
        if (!online) {
            return "offline";
        }
        if (webSocketOnline) {
            boolean visible = pageVisible || "visible".equalsIgnoreCase(visibilityState);
            boolean playing = parseBoolean(metadata.get("playing"));
            if (visible || playing) {
                return "active";
            }
            return "background";
        }
        return active ? "active" : "idle";
    }

    private String resolveConnectionStatus(boolean online, boolean webSocketOnline, Map<String, String> metadata) {
        if (webSocketOnline) {
            return "websocket_connected";
        }
        if (online) {
            return "http_active";
        }
        String status = metadata.get("connectionStatus");
        return status == null ? "disconnected" : status;
    }

    private String resolveConnectionQuality(boolean webSocketOnline, String heartbeatIntervalSeconds) {
        if (!webSocketOnline) {
            return "unknown";
        }
        Long interval = parseLong(heartbeatIntervalSeconds);
        if (interval == null) {
            return "fresh";
        }
        if (interval <= 45) {
            return "stable";
        }
        if (interval <= 90) {
            return "delayed";
        }
        return "unstable";
    }

    private Map<String, Object> copyMetadata(Map<String, Object> metadata) {
        return metadata == null ? new HashMap<>() : new HashMap<>(metadata);
    }

    private boolean parseBoolean(String value) {
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private String truncate(String value) {
        if (value == null || value.length() <= META_VALUE_MAX_LENGTH) {
            return value;
        }
        return value.substring(0, META_VALUE_MAX_LENGTH);
    }

    private int getActiveTtlMinutes() {
        return Math.max(1, workTimeConfig.getActiveTimeoutMinutes());
    }

    private int getPresenceTtlMinutes() {
        return Math.max(getActiveTtlMinutes() + 1, 2);
    }

    private String onlineKey(Long userId) {
        return ONLINE_PREFIX + userId;
    }

    private String lastActivityKey(Long userId) {
        return LAST_ACTIVITY_PREFIX + userId;
    }

    private String dbSyncKey(Long userId) {
        return DB_SYNC_PREFIX + userId;
    }

    private String wsConnectedKey(Long userId) {
        return WS_CONNECTED_PREFIX + userId;
    }

    private String wsHeartbeatKey(Long userId) {
        return WS_HEARTBEAT_PREFIX + userId;
    }

    private String metaKey(Long userId) {
        return WS_META_PREFIX + userId;
    }
}
