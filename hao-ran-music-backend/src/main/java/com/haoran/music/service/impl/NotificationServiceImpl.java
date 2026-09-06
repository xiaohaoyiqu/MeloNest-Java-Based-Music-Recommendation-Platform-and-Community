package com.haoran.music.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.CacheHelper;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.RedisUtils;
import com.haoran.music.common.util.SecurityCheckUtil;
import com.haoran.music.dto.NotificationDetailVO;
import com.haoran.music.entity.Notification;
import com.haoran.music.mapper.NotificationMapper;
import com.haoran.music.service.NotificationService;
import com.haoran.music.service.NotificationDeliveryOutboxService;
import com.haoran.music.service.WebSocketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;





@Slf4j
@Service
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification> implements NotificationService {

    @Autowired
    private NotificationMapper notificationMapper;

    @Autowired(required = false)
    private WebSocketService webSocketService;

    @Autowired(required = false)
    private NotificationDeliveryOutboxService notificationDeliveryOutboxService;

    @Autowired
    private RedisUtils redisUtils;




    private static final String UNREAD_COUNT_PREFIX = "notification:unread:";




    private static final int CACHE_TTL = 300;




    private static final int AGGREGATE_WINDOW_HOURS = 1;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendApprovalNotification(Long userId, String contentType, String contentName, Long relatedId) {
        if (userId == null) {
            return;
        }

        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType("moderation_approved");
        notification.setTitle("审核通过");
        notification.setContent(String.format("您提交的%s《%s》已通过审核", contentType, contentName));
        notification.setRelatedId(relatedId);
        notification.setIsRead(0);
        notification.setCreateTime(LocalDateTime.now());

        requireNotificationInserted(notification);


        clearUnreadCountCache(userId);


        sendWebSocketNotification(userId, notification);

        log.debug("event=notification_created type={} userId={} notificationId={} relatedId={}",
                notification.getType(), userId, notification.getId(), relatedId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendRejectionNotification(Long userId, String contentType, String reason, Long relatedId) {
        if (userId == null) {
            return;
        }

        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType("moderation_rejected");
        notification.setTitle("审核未通过");
        notification.setContent(String.format("您提交的%s审核未通过。原因：%s", contentType, reason));
        notification.setRelatedId(relatedId);
        notification.setIsRead(0);
        notification.setCreateTime(LocalDateTime.now());

        requireNotificationInserted(notification);


        clearUnreadCountCache(userId);


        sendWebSocketNotification(userId, notification);

        log.debug("event=notification_created type={} userId={} notificationId={} relatedId={}",
                notification.getType(), userId, notification.getId(), relatedId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendCreatorApprovedNotification(Long userId) {
        if (userId == null) {
            return;
        }

        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType("creator_approved");
        notification.setTitle("创作者申请通过");
        notification.setContent("恭喜！您的创作者申请已通过审核，现在可以开始创作了");
        notification.setIsRead(0);
        notification.setCreateTime(LocalDateTime.now());

        requireNotificationInserted(notification);


        clearUnreadCountCache(userId);


        sendWebSocketNotification(userId, notification);

        log.debug("event=notification_created type={} userId={} notificationId={}",
                notification.getType(), userId, notification.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendCreatorRejectedNotification(Long userId, String reason) {
        if (userId == null) {
            return;
        }

        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType("creator_rejected");
        notification.setTitle("创作者申请未通过");
        notification.setContent(String.format("很遗憾，您的创作者申请未通过。原因：%s", reason));
        notification.setIsRead(0);
        notification.setCreateTime(LocalDateTime.now());

        requireNotificationInserted(notification);


        clearUnreadCountCache(userId);


        sendWebSocketNotification(userId, notification);

        log.debug("event=notification_created type={} userId={} notificationId={}",
                notification.getType(), userId, notification.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendSystemNotification(Long userId, String title, String content, String link) {
        if (userId == null) {
            return;
        }

        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType("system");
        notification.setTitle(title);

        if (ObjectUtils.isNotEmpty(content)) {
            SecurityCheckUtil.CheckResult contentCheck = SecurityCheckUtil.checkDescription(content);
            if (!contentCheck.isSafe()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, contentCheck.getMessage());
            }
            notification.setContent(SecurityCheckUtil.escapeHtml(contentCheck.getCleanedValue()));
        } else {
            notification.setContent(content);
        }
        notification.setLink(link);
        notification.setIsRead(0);
        notification.setCreateTime(LocalDateTime.now());

        requireNotificationInserted(notification);


        clearUnreadCountCache(userId);


        sendWebSocketNotification(userId, notification);

        log.debug("event=notification_created type={} userId={} notificationId={}",
                notification.getType(), userId, notification.getId());
    }










    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendSystemNotificationOnce(Long userId, String title, String content,
                                           String link, String businessKey) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(businessKey)) {
            return;
        }
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType("system");
        notification.setTitle(title);
        SecurityCheckUtil.CheckResult contentCheck = SecurityCheckUtil.checkDescription(content);
        if (!contentCheck.isSafe()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, contentCheck.getMessage());
        }
        notification.setContent(SecurityCheckUtil.escapeHtml(contentCheck.getCleanedValue()));
        notification.setLink(link);
        notification.setIsRead(0);
        notification.setCreateTime(LocalDateTime.now());
        if (insertNotificationOnce(notification, businessKey)) {
            clearUnreadCountCache(userId);
            sendWebSocketNotification(userId, notification);
        }
    }

    @Override
    public Long getUnreadCount(Long userId) {
        if (userId == null) {
            return 0L;
        }

        String cacheKey = UNREAD_COUNT_PREFIX + userId;

        return CacheHelper.getOrLoad(
                redisUtils,
                cacheKey,
                () -> notificationMapper.selectCount(
                        new LambdaQueryWrapper<Notification>()
                                .eq(Notification::getUserId, userId)
                                .eq(Notification::getIsRead, 0)
                ),
                CACHE_TTL,
                TimeUnit.SECONDS,
                Long.class
        );
    }

    @Override
    public List<Notification> getUserNotifications(Long userId, Integer limit) {
        if (userId == null) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getCreateTime);

        if (limit != null && limit > 0) {
            wrapper.last("LIMIT " + Math.min(limit, 50));
        }

        return notificationMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markAsRead(Long notificationId, Long userId) {
        if (notificationId == null || userId == null) {
            return false;
        }


        Notification notification = notificationMapper.selectById(notificationId);
        if (notification == null || !notification.getUserId().equals(userId)) {
            return false;
        }

        if (Integer.valueOf(1).equals(notification.getIsRead())) {
            return true;
        }


        boolean success = notificationMapper.update(null,
                new LambdaUpdateWrapper<Notification>()
                        .eq(Notification::getId, notificationId)
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, 0)
                        .set(Notification::getIsRead, 1)
        ) > 0;

        if (success) {

            clearUnreadCountCache(userId);
            sendWebSocketReadEvent(userId, notificationId);
            return true;
        }


        Notification current = notificationMapper.selectById(notificationId);
        return current != null
                && userId.equals(current.getUserId())
                && Integer.valueOf(1).equals(current.getIsRead());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int markAllAsRead(Long userId) {
        if (userId == null) {
            return 0;
        }

        int count = notificationMapper.update(null,
                new LambdaUpdateWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, 0)
                        .set(Notification::getIsRead, 1)
        );


        clearUnreadCountCache(userId);
        if (count > 0) {
            sendWebSocketAllReadEvent(userId, count);
        }

        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteNotification(Long notificationId, Long userId) {
        if (notificationId == null || userId == null) {
            return false;
        }


        Notification notification = notificationMapper.selectById(notificationId);
        if (notification == null || !notification.getUserId().equals(userId)) {
            return false;
        }


        boolean success = notificationMapper.deleteById(notificationId) > 0;

        if (success && Integer.valueOf(0).equals(notification.getIsRead())) {

            clearUnreadCountCache(userId);
        }

        return success;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteReadNotifications(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return 0;
        }
        return notificationMapper.delete(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 1));
    }



    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendRewardNotification(Long creatorId, Long senderId, String senderName, String senderAvatar,
                                      BigDecimal amount, Long resourceId, String resourceType, String resourceName) {
        if (creatorId == null || senderId == null) {
            return;
        }


        String groupId = generateGroupId("reward", creatorId, resourceId);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime timeWindowStart = now.minusHours(AGGREGATE_WINDOW_HOURS);

        Notification existing = lockRecentGroupNotification(creatorId, groupId, timeWindowStart);


        Map<String, Object> metadata = new HashMap<>();
        metadata.put("senderName", senderName);
        metadata.put("amount", amount);
        metadata.put("resourceType", resourceType);
        metadata.put("resourceName", resourceName);
        metadata.put("senderId", senderId);

        if (existing != null) {

            existing.setGroupCount(existing.getGroupCount() + 1);
            existing.setGroupAmount(existing.getGroupAmount().add(amount));
            existing.setCreateTime(now);              
            existing.setSenderName(getAggregatedSenderName(existing, senderName));
            existing.setContent(buildAggregatedContent("reward", existing.getGroupCount(),
                    existing.getGroupAmount(), resourceName));
            requireNotificationUpdated(existing);


            clearUnreadCountCache(creatorId);


            sendWebSocketNotification(creatorId, existing, false);

            log.debug("event=notification_aggregate_updated type={} userId={} groupId={} count={}",
                    "reward", creatorId, groupId, existing.getGroupCount());
        } else {

            Notification notification = new Notification();
            notification.setUserId(creatorId);
            notification.setSenderId(senderId);
            notification.setSenderName(senderName);
            notification.setSenderAvatar(senderAvatar);
            notification.setGroupId(groupId);
            notification.setGroupCount(1);
            notification.setGroupAmount(amount);
            notification.setMetadata(JSON.toJSONString(metadata));
            notification.setType("reward");
            notification.setTitle("打赏通知");
            notification.setContent(String.format("%s打赏了¥%s", senderName, amount));
            notification.setRelatedId(resourceId);
            notification.setLink("/song/" + resourceId);
            notification.setIsRead(0);
            notification.setCreateTime(now);

            requireNotificationInserted(notification);


            clearUnreadCountCache(creatorId);


            sendWebSocketNotification(creatorId, notification);

            log.debug("event=notification_created type={} userId={} notificationId={} relatedId={}",
                    notification.getType(), creatorId, notification.getId(), resourceId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendSubscribeNotification(Long creatorId, Long subscriberId, String subscriberName,
                                         Long playlistId, String playlistName, String subscribeType, BigDecimal amount) {
        if (creatorId == null || subscriberId == null) {
            return;
        }


        String groupId = generateGroupId("subscribe", creatorId, playlistId);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime timeWindowStart = now.minusHours(AGGREGATE_WINDOW_HOURS);

        Notification existing = lockRecentGroupNotification(creatorId, groupId, timeWindowStart);


        Map<String, Object> metadata = new HashMap<>();
        metadata.put("subscriberName", subscriberName);
        metadata.put("playlistName", playlistName);
        metadata.put("subscribeType", subscribeType);
        metadata.put("amount", amount);
        metadata.put("subscriberId", subscriberId);

        if (existing != null) {

            existing.setGroupCount(existing.getGroupCount() + 1);
            existing.setGroupAmount(existing.getGroupAmount().add(amount));
            existing.setCreateTime(now);
            existing.setSenderName(getAggregatedSenderName(existing, subscriberName));
            existing.setContent(String.format("%s、%s等%d人订阅了《%s》",
                    existing.getSenderName(), subscriberName, existing.getGroupCount(), playlistName));
            requireNotificationUpdated(existing);

            clearUnreadCountCache(creatorId);
            sendWebSocketNotification(creatorId, existing, false);

            log.debug("event=notification_aggregate_updated type={} userId={} groupId={} count={}",
                    "subscribe", creatorId, groupId, existing.getGroupCount());
        } else {

            Notification notification = new Notification();
            notification.setUserId(creatorId);
            notification.setSenderId(subscriberId);
            notification.setSenderName(subscriberName);
            notification.setGroupId(groupId);
            notification.setGroupCount(1);
            notification.setGroupAmount(amount);
            notification.setMetadata(JSON.toJSONString(metadata));
            notification.setType("subscribe");
            notification.setTitle("新订阅");
            notification.setContent(String.format("%s订阅了你的歌单《%s》", subscriberName, playlistName));
            notification.setRelatedId(playlistId);
            notification.setLink("/playlist/" + playlistId);
            notification.setIsRead(0);
            notification.setCreateTime(now);

            requireNotificationInserted(notification);

            clearUnreadCountCache(creatorId);
            sendWebSocketNotification(creatorId, notification);

            log.debug("event=notification_created type={} userId={} notificationId={} relatedId={}",
                    notification.getType(), creatorId, notification.getId(), playlistId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendLikeNotification(Long resourceOwnerId, Long likerId, String likerName,
                                    String resourceType, Long resourceId, String resourceName) {
        if (resourceOwnerId == null || likerId == null || resourceOwnerId.equals(likerId)) {
            return;
        }


        String groupId = generateGroupId("like", resourceOwnerId, resourceId);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime timeWindowStart = now.minusHours(AGGREGATE_WINDOW_HOURS);

        Notification existing = lockRecentGroupNotification(resourceOwnerId, groupId, timeWindowStart);


        Map<String, Object> metadata = new HashMap<>();
        metadata.put("likerName", likerName);
        metadata.put("resourceType", resourceType);
        metadata.put("resourceId", resourceId);
        metadata.put("resourceName", resourceName);
        metadata.put("likerId", likerId);

        if (existing != null) {

            existing.setGroupCount(existing.getGroupCount() + 1);
            existing.setCreateTime(now);
            existing.setSenderName(getAggregatedSenderName(existing, likerName));
            existing.setContent(String.format("%s、%s等%d人赞了你的%s",
                    existing.getSenderName(), likerName, existing.getGroupCount(), resourceType));
            requireNotificationUpdated(existing);

            clearUnreadCountCache(resourceOwnerId);
            sendWebSocketNotification(resourceOwnerId, existing, false);

            log.debug("event=notification_aggregate_updated type={} userId={} groupId={} count={}",
                    "like", resourceOwnerId, groupId, existing.getGroupCount());
        } else {

            Notification notification = new Notification();
            notification.setUserId(resourceOwnerId);
            notification.setSenderId(likerId);
            notification.setSenderName(likerName);
            notification.setGroupId(groupId);
            notification.setGroupCount(1);
            notification.setMetadata(JSON.toJSONString(metadata));
            notification.setType("like");
            notification.setTitle("收到点赞");
            notification.setContent(String.format("%s赞了你的%s《%s》", likerName, resourceType, resourceName));
            notification.setRelatedId(resourceId);
            notification.setLink(resourceLink(resourceType, resourceId));
            notification.setIsRead(0);
            notification.setCreateTime(now);

            requireNotificationInserted(notification);

            clearUnreadCountCache(resourceOwnerId);
            sendWebSocketNotification(resourceOwnerId, notification);

            log.debug("event=notification_created type={} userId={} notificationId={} relatedId={}",
                    notification.getType(), resourceOwnerId, notification.getId(), resourceId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendFollowNotification(Long followeeId, Long followerId, String followerName) {
        if (followeeId == null || followerId == null || followeeId.equals(followerId)) {
            return;
        }


        String groupId = generateGroupId("follow", followeeId, null);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime timeWindowStart = now.minusHours(AGGREGATE_WINDOW_HOURS);

        Notification existing = lockRecentGroupNotification(followeeId, groupId, timeWindowStart);


        Map<String, Object> metadata = new HashMap<>();
        metadata.put("followerName", followerName);
        metadata.put("followerId", followerId);

        if (existing != null) {

            existing.setGroupCount(existing.getGroupCount() + 1);
            existing.setCreateTime(now);
            existing.setSenderName(getAggregatedSenderName(existing, followerName));
            existing.setContent(String.format("%s、%s等%d人关注了你",
                    existing.getSenderName(), followerName, existing.getGroupCount()));
            requireNotificationUpdated(existing);

            clearUnreadCountCache(followeeId);
            sendWebSocketNotification(followeeId, existing, false);

            log.debug("event=notification_aggregate_updated type={} userId={} groupId={} count={}",
                    "follow", followeeId, groupId, existing.getGroupCount());
        } else {

            Notification notification = new Notification();
            notification.setUserId(followeeId);
            notification.setSenderId(followerId);
            notification.setSenderName(followerName);
            notification.setGroupId(groupId);
            notification.setGroupCount(1);
            notification.setMetadata(JSON.toJSONString(metadata));
            notification.setType("follow");
            notification.setTitle("新粉丝");
            notification.setContent(String.format("%s关注了你", followerName));
            notification.setRelatedId(followerId);
            notification.setLink("/user/" + followerId);
            notification.setIsRead(0);
            notification.setCreateTime(now);

            requireNotificationInserted(notification);

            clearUnreadCountCache(followeeId);
            sendWebSocketNotification(followeeId, notification);

            log.debug("event=notification_created type={} userId={} notificationId={} relatedId={}",
                    notification.getType(), followeeId, notification.getId(), followerId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendRevenueNotification(Long creatorId, BigDecimal amount, String source) {
        if (creatorId == null) {
            return;
        }


        Notification notification = new Notification();
        notification.setUserId(creatorId);
        notification.setType("revenue");
        notification.setTitle("收益到账");
        notification.setContent(String.format("您收到¥%s收益，来自%s", amount, source));
        notification.setGroupAmount(amount);
        notification.setGroupCount(1);
        notification.setRelatedId(creatorId);
        notification.setLink("/creator/earnings");
        notification.setIsRead(0);
        notification.setCreateTime(LocalDateTime.now());

        requireNotificationInserted(notification);

        clearUnreadCountCache(creatorId);
        sendWebSocketNotification(creatorId, notification);

        log.debug("event=notification_created type={} userId={} notificationId={}",
                notification.getType(), creatorId, notification.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendSubscriptionExpiringNotification(Long userId, String playlistName, int daysLeft) {
        if (userId == null) {
            return;
        }

        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType("subscription_expiring");
        notification.setTitle("订阅即将到期");
        notification.setContent(String.format("您订阅的《%s》将在%d天后到期，请及时续费", playlistName, daysLeft));
        notification.setIsRead(0);
        notification.setCreateTime(LocalDateTime.now());

        requireNotificationInserted(notification);

        clearUnreadCountCache(userId);
        sendWebSocketNotification(userId, notification);

        log.debug("event=notification_created type={} userId={} notificationId={}",
                notification.getType(), userId, notification.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendContentUpdateNotification(Long subscriberId, Long playlistId, String playlistName, int songCount) {
        if (subscriberId == null) {
            return;
        }

        Notification notification = new Notification();
        notification.setUserId(subscriberId);
        notification.setSenderId(playlistId);
        notification.setType("content_update");
        notification.setTitle("内容更新");
        notification.setContent(String.format("您订阅的《%s》更新了%d首歌曲", playlistName, songCount));
        notification.setRelatedId(playlistId);
        notification.setLink("/playlist/" + playlistId);
        notification.setIsRead(0);
        notification.setCreateTime(LocalDateTime.now());

        requireNotificationInserted(notification);

        clearUnreadCountCache(subscriberId);
        sendWebSocketNotification(subscriberId, notification);

        log.debug("event=notification_created type={} userId={} notificationId={} relatedId={}",
                notification.getType(), subscriberId, notification.getId(), playlistId);
    }

    @Override
    public List<NotificationDetailVO> getNotificationGroupDetails(String groupId, Long recipientId) {
        if (groupId == null || groupId.isEmpty()) {
            return new ArrayList<>();
        }
        if (recipientId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getGroupId, groupId)
                .eq(Notification::getUserId, recipientId)
                .eq(Notification::getDeleted, 0)
                .orderByDesc(Notification::getCreateTime);

        List<Notification> notifications = notificationMapper.selectList(wrapper);
        List<NotificationDetailVO> result = new ArrayList<>();

        for (Notification notification : notifications) {
            NotificationDetailVO vo = new NotificationDetailVO();
            vo.setId(notification.getId());
            vo.setSenderId(notification.getSenderId());
            vo.setSenderName(notification.getSenderName());
            vo.setSenderAvatar(notification.getSenderAvatar());
            vo.setTitle(notification.getTitle());
            vo.setContent(notification.getContent());
            vo.setType(notification.getType());
            vo.setIsRead(Integer.valueOf(1).equals(notification.getIsRead()));
            vo.setLink(notification.getLink());
            vo.setCreateTime(notification.getCreateTime());
            vo.setMetadata(notification.getMetadata());
            vo.setResourceId(notification.getRelatedId());


            if (notification.getMetadata() != null) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> meta = JSON.parseObject(notification.getMetadata(), Map.class);
                    if (meta.containsKey("amount")) {
                        vo.setAmount(new BigDecimal(meta.get("amount").toString()));
                    }
                    if (meta.containsKey("resourceName")) {
                        vo.setResourceName(meta.get("resourceName").toString());
                    }
                    if (meta.containsKey("resourceType")) {
                        vo.setResourceType(meta.get("resourceType").toString());
                    }
                } catch (Exception e) {
                    log.warn("event=notification_metadata_parse_failed notificationId={} errorType={}",
                            notification.getId(), e.getClass().getSimpleName());
                }
            }

            result.add(vo);
        }

        return result;
    }











    private boolean insertNotificationOnce(Notification notification, String businessKey) {
        notification.setBusinessKey(businessKey);
        notification.setDeleted(0);
        try {
            if (notificationMapper.insert(notification) != 1) {
                throw new BusinessException("通知写入失败");
            }
            return true;
        } catch (DuplicateKeyException duplicate) {
            log.debug("event=notification_business_replay_skipped businessKey={}", businessKey);
            return false;
        }
    }

    private String safeReason(String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            return "请查看审核记录或联系平台";
        }
        String normalized = reason.trim();
        return normalized.length() <= 200 ? normalized : normalized.substring(0, 200);
    }












    private Notification lockRecentGroupNotification(Long userId, String groupId,
                                                       LocalDateTime timeWindowStart) {
        Notification candidate = notificationMapper.selectOne(
                recentGroupQuery(userId, groupId, timeWindowStart, null, false));
        if (candidate != null) {
            Notification locked = notificationMapper.selectOne(
                    recentGroupQuery(userId, groupId, timeWindowStart, candidate.getId(), true));
            if (locked != null) {
                return locked;
            }
        }

        Long lockedRecipientId = notificationMapper.lockRecipientForNotificationAggregation(userId);
        if (!userId.equals(lockedRecipientId)) {
            throw new BusinessException("通知接收用户不存在");
        }
        return notificationMapper.selectOne(
                recentGroupQuery(userId, groupId, timeWindowStart, null, true));
    }




    private LambdaQueryWrapper<Notification> recentGroupQuery(Long userId, String groupId,
                                                               LocalDateTime timeWindowStart,
                                                               Long notificationId, boolean forUpdate) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(notificationId != null, Notification::getId, notificationId)
                .eq(Notification::getUserId, userId)
                .eq(Notification::getGroupId, groupId)
                .eq(Notification::getIsRead, 0)
                .and(condition -> condition.eq(Notification::getDeleted, 0)
                        .or().isNull(Notification::getDeleted))
                .ge(Notification::getCreateTime, timeWindowStart)
                .orderByDesc(Notification::getCreateTime)
                .last(forUpdate ? "LIMIT 1 FOR UPDATE" : "LIMIT 1");
        return wrapper;
    }





    private String generateGroupId(String type, Long userId, Long relatedId) {
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String relatedIdStr = relatedId != null ? relatedId.toString() : "null";
        return type + ":" + userId + ":" + relatedIdStr + ":" + dateStr;
    }





    private String getAggregatedSenderName(Notification existing, String newName) {
        String existingNames = existing.getSenderName();
        if (existingNames == null) {
            return newName;
        }


        if (existingNames.contains(newName)) {
            return existingNames;
        }


        String[] names = existingNames.split("、");
        if (names.length >= 2) {
            return existingNames;
        }


        return existingNames + "、" + newName;
    }




    private String buildAggregatedContent(String type, int count, BigDecimal amount, String resourceName) {
        switch (type) {
            case "reward":
                return String.format("等%d人给你打赏 ¥%s", count, amount);
            case "subscribe":
                return String.format("等%d人订阅了《%s》", count, resourceName);
            case "like":
                return String.format("等%d人赞了《%s》", count, resourceName);
            case "follow":
                return String.format("等%d人关注了你", count);
            default:
                return String.format("等%d人进行了此操作", count);
        }
    }







    private void requireNotificationInserted(Notification notification) {
        if (notificationMapper.insert(notification) != 1) {
            throw new BusinessException("通知写入失败");
        }
    }







    private void requireNotificationUpdated(Notification notification) {
        if (notificationMapper.updateById(notification) != 1) {
            throw new BusinessException("通知更新失败");
        }
    }




    private void clearUnreadCountCache(Long userId) {
        if (userId != null) {
            String cacheKey = UNREAD_COUNT_PREFIX + userId;
            runAfterCommit(() -> CacheHelper.delete(redisUtils, cacheKey));
        }
    }




    private void sendWebSocketNotification(Long userId, Notification notification) {
        sendWebSocketNotification(userId, notification, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendRewardNotificationOnce(Long rewardId, Long creatorId, Long senderId,
                                           String senderName, String senderAvatar, BigDecimal amount,
                                           Long resourceId, String resourceType) {
        if (rewardId == null || creatorId == null || amount == null) {
            return;
        }
        String visibleName = senderName == null || senderName.trim().isEmpty() ? "匿名用户" : senderName.trim();
        Notification notification = new Notification();
        notification.setUserId(creatorId);
        notification.setSenderId(senderId);
        notification.setSenderName(visibleName);
        notification.setSenderAvatar(senderAvatar);
        notification.setType("reward");
        notification.setTitle("打赏通知");
        notification.setContent(String.format("%s打赏了¥%s", visibleName, amount));
        notification.setGroupCount(1);
        notification.setGroupAmount(amount);
        notification.setRelatedId(resourceId);
        if (resourceId != null && resourceType != null) {
            notification.setLink(resourceLink(resourceType, resourceId));
        }
        notification.setIsRead(0);
        notification.setCreateTime(LocalDateTime.now());
        if (insertNotificationOnce(notification, "reward:" + rewardId)) {
            clearUnreadCountCache(creatorId);
            sendWebSocketNotification(creatorId, notification);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendCommentNotification(Long recipientId, Long senderId, String senderName,
                                        Long commentId, String resourceType, Long resourceId,
                                        String resourceName, boolean reply) {
        if (recipientId == null || senderId == null || commentId == null || recipientId.equals(senderId)) {
            return;
        }
        Notification notification = interactionNotification(
                recipientId, senderId, senderName, "comment", commentId,
                resourceType, resourceId, resourceName);
        notification.setTitle(reply ? "收到回复" : "收到评论");
        notification.setContent(String.format("%s%s了《%s》",
                displayName(senderName), reply ? "回复" : "评论", displayResourceName(resourceName)));
        if (insertNotificationOnce(notification, "comment:" + commentId + ":" + recipientId)) {
            clearUnreadCountCache(recipientId);
            sendWebSocketNotification(recipientId, notification);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendCommentLikeNotification(Long recipientId, Long likerId, String likerName,
                                            Long commentId, String resourceType, Long resourceId,
                                            String resourceName) {
        if (recipientId == null || likerId == null || commentId == null || recipientId.equals(likerId)) {
            return;
        }
        Notification notification = interactionNotification(
                recipientId, likerId, likerName, "like", commentId,
                resourceType, resourceId, resourceName);
        notification.setTitle("评论收到点赞");
        notification.setContent(String.format("%s赞了你在《%s》下的评论",
                displayName(likerName), displayResourceName(resourceName)));
        if (insertNotificationOnce(notification, "comment-like:" + commentId + ":" + likerId)) {
            clearUnreadCountCache(recipientId);
            sendWebSocketNotification(recipientId, notification);
        }
    }

    private Notification interactionNotification(Long recipientId, Long senderId, String senderName,
                                                 String type, Long relatedId, String resourceType,
                                                 Long resourceId, String resourceName) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("resourceType", resourceType);
        metadata.put("resourceId", resourceId);
        metadata.put("resourceName", resourceName);
        metadata.put("commentId", relatedId);

        Notification notification = new Notification();
        notification.setUserId(recipientId);
        notification.setSenderId(senderId);
        notification.setSenderName(displayName(senderName));
        notification.setType(type);
        notification.setRelatedId(relatedId);
        notification.setMetadata(JSON.toJSONString(metadata));
        notification.setLink(resourceLink(resourceType, resourceId));
        notification.setGroupCount(1);
        notification.setIsRead(0);
        notification.setCreateTime(LocalDateTime.now());
        return notification;
    }

    private String resourceLink(String resourceType, Long resourceId) {
        if (resourceType == null || resourceId == null) {
            return null;
        }
        switch (resourceType) {
            case "music-post":
                return "/square?postId=" + resourceId;
            case "music-square-work":
                return "/my/creator?tab=works&workId=" + resourceId;
            case "marketplace-item":
                return "/square/marketplace/" + resourceId;
            default:
                return "/" + resourceType + "/" + resourceId;
        }
    }

    private String displayName(String name) {
        return name == null || name.trim().isEmpty() ? "一位镇民" : name.trim();
    }

    private String displayResourceName(String name) {
        return name == null || name.trim().isEmpty() ? "这份内容" : name.trim();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendModerationResultNotificationOnce(Long userId, String contentType, String contentName,
                                                     Long relatedId, boolean approved, String reason,
                                                     String businessKey) {
        if (userId == null || relatedId == null || businessKey == null) {
            return;
        }
        String safeType = contentType == null || contentType.trim().isEmpty() ? "内容" : contentType.trim();
        String safeName = contentName == null || contentName.trim().isEmpty() ? "" : "《" + contentName.trim() + "》";
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(approved ? "moderation_approved" : "moderation_rejected");
        notification.setTitle(approved ? "审核通过" : "审核未通过");
        notification.setContent(approved
                ? "您提交的" + safeType + safeName + "已通过审核"
                : "您提交的" + safeType + safeName + "审核未通过。原因：" + safeReason(reason));
        notification.setRelatedId(relatedId);
        notification.setIsRead(0);
        notification.setCreateTime(LocalDateTime.now());
        if (insertNotificationOnce(notification, businessKey)) {
            clearUnreadCountCache(userId);
            sendWebSocketNotification(userId, notification);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendCreatorEligibilityNotificationOnce(Long userId, String status, String reason,
                                                       String businessKey) {
        if (userId == null || businessKey == null) {
            return;
        }
        boolean active = "active".equalsIgnoreCase(status);
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(active ? "creator_approved" : "creator_status_changed");
        notification.setTitle(active ? "创作者资格已生效" : "创作者资格已变更");
        notification.setContent(active
                ? "您的创作者资格已生效"
                : "您的创作者资格已变更为" + (status == null ? "不可用" : status)
                    + "。原因：" + safeReason(reason));
        notification.setIsRead(0);
        notification.setCreateTime(LocalDateTime.now());
        if (insertNotificationOnce(notification, businessKey)) {
            clearUnreadCountCache(userId);
            sendWebSocketNotification(userId, notification);
        }
    }









    private void sendWebSocketNotification(Long userId, Notification notification, boolean unreadDelta) {
        if (ObjectUtils.isNotEmpty(notificationDeliveryOutboxService)) {
            String eventId = notificationDeliveryOutboxService.record(notification, unreadDelta);
            runAfterCommit(() -> notificationDeliveryOutboxService.dispatchEvent(eventId));
            return;
        }
        runAfterCommit(() -> {
            if (webSocketService == null) {
                return;
            }
            try {
                Notification deliveryNotification = notification;
                if (notification.getId() != null) {
                    Notification current = notificationMapper.selectById(notification.getId());
                    if (current == null
                            || Integer.valueOf(1).equals(current.getDeleted())
                            || !userId.equals(current.getUserId())) {
                        return;
                    }
                    deliveryNotification = current;
                }
                Map<String, Object> message = new HashMap<>();
                message.put("type", "new_notification");
                Map<String, Object> data = JSON.parseObject(JSON.toJSONString(deliveryNotification), Map.class);
                data.put("unreadDelta", unreadDelta ? 1 : 0);
                message.put("data", data);
                message.put("timestamp", System.currentTimeMillis());
                webSocketService.sendToUser(userId, message);
            } catch (Exception e) {
                log.error("event=notification_websocket_delivery_failed userId={} notificationId={} errorType={}",
                        userId, notification.getId(), e.getClass().getSimpleName());
            }
        });
    }




    private void sendWebSocketReadEvent(Long userId, Long notificationId) {
        runAfterCommit(() -> {
            if (webSocketService == null) {
                return;
            }
            try {
                Map<String, Object> data = new HashMap<>();
                data.put("notificationId", notificationId);

                Map<String, Object> message = new HashMap<>();
                message.put("type", "notification_read");
                message.put("data", data);
                message.put("timestamp", System.currentTimeMillis());
                webSocketService.sendToUser(userId, message);
            } catch (Exception e) {
                log.warn("event=notification_read_sync_failed userId={} notificationId={} errorType={}",
                        userId, notificationId, e.getClass().getSimpleName());
            }
        });
    }




    private void sendWebSocketAllReadEvent(Long userId, int count) {
        runAfterCommit(() -> {
            if (webSocketService == null) {
                return;
            }
            try {
                Map<String, Object> data = new HashMap<>();
                data.put("count", count);

                Map<String, Object> message = new HashMap<>();
                message.put("type", "all_read");
                message.put("data", data);
                message.put("timestamp", System.currentTimeMillis());
                webSocketService.sendToUser(userId, message);
            } catch (Exception e) {
                log.warn("event=notification_all_read_sync_failed userId={} errorType={}",
                        userId, e.getClass().getSimpleName());
            }
        });
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendReportResultNotification(Long reporterId, Long reportId, String result, String reason, Integer reward) {
        if (reporterId == null) {
            return;
        }

        Notification notification = new Notification();
        notification.setUserId(reporterId);
        notification.setRelatedId(reportId);
        notification.setIsRead(0);
        notification.setCreateTime(LocalDateTime.now());


        Map<String, Object> metadata = new HashMap<>();
        metadata.put("reportId", reportId);
        metadata.put("result", result);
        metadata.put("reason", reason);
        if (reward != null && reward > 0) {
            metadata.put("reward", reward);
        }

        if ("approved".equals(result)) {
            notification.setType("system");
            notification.setTitle("举报处理完成");
            notification.setContent(String.format("您提交的举报已处理通过，感谢您的监督！%s",
                    reward != null && reward > 0 ? "获得" + reward + "信用分奖励" : ""));
            notification.setLink("/my/reports");
        } else {
            notification.setType("system");
            notification.setTitle("举报处理结果");
            notification.setContent(String.format("您提交的举报未通过。原因：%s", reason));
            notification.setLink("/my/reports");
        }

        notification.setMetadata(JSON.toJSONString(metadata));
        requireNotificationInserted(notification);

        clearUnreadCountCache(reporterId);
        sendWebSocketNotification(reporterId, notification);

        log.debug("event=notification_created type={} userId={} notificationId={} relatedId={}",
                notification.getType(), reporterId, notification.getId(), reportId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendFeedbackResultNotification(Long feedbackerId, Long feedbackId, String result, String reply) {
        if (feedbackerId == null) {
            return;
        }

        Notification notification = new Notification();
        notification.setUserId(feedbackerId);
        notification.setRelatedId(feedbackId);
        notification.setIsRead(0);
        notification.setCreateTime(LocalDateTime.now());


        Map<String, Object> metadata = new HashMap<>();
        metadata.put("feedbackId", feedbackId);
        metadata.put("result", result);
        if (reply != null && !reply.isEmpty()) {
            metadata.put("reply", reply);
        }

        if ("resolved".equals(result)) {
            notification.setType("system");
            notification.setTitle("反馈已处理");
            notification.setContent(reply != null && !reply.isEmpty()
                    ? "您的反馈已处理，管理员回复：" + reply
                    : "您的反馈已处理，感谢您的建议！");
            notification.setLink("/my/feedback");
        } else {
            notification.setType("system");
            notification.setTitle("反馈已关闭");
            notification.setContent("您的反馈已关闭，感谢您的反馈");
            notification.setLink("/my/feedback");
        }

        notification.setMetadata(JSON.toJSONString(metadata));
        requireNotificationInserted(notification);

        clearUnreadCountCache(feedbackerId);
        sendWebSocketNotification(feedbackerId, notification);

        log.debug("event=notification_created type={} userId={} notificationId={} relatedId={}",
                notification.getType(), feedbackerId, notification.getId(), feedbackId);
    }



    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean recallNotificationByAdmin(Long notificationId, Long adminId, String reason) {
        if (notificationId == null || adminId == null) {
            log.warn("event=notification_revoke_rejected operation={} cause={}",
                    "admin_single", "missing_parameter");
            return false;
        }

        Notification notification = notificationMapper.selectById(notificationId);
        if (notification == null) {
            log.warn("event=notification_revoke_rejected operation={} cause={} notificationId={}",
                    "admin_single", "not_found", notificationId);
            return false;
        }


        boolean success = notificationMapper.deleteById(notificationId) > 0;

        if (success) {

            if (notification.getIsRead() == 0) {
                clearUnreadCountCache(notification.getUserId());
            }

            log.debug("event=notification_revoked operation={} notificationId={} operatorId={} userId={} count={}",
                    "admin_single", notificationId, adminId, notification.getUserId(), 1);
        }

        return success;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchRecallUserNotifications(Long userId, String type) {
        if (userId == null) {
            log.warn("event=notification_revoke_rejected operation={} cause={}",
                    "user_batch", "missing_user_id");
            return 0;
        }

        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId);


        if (type != null && !type.isEmpty()) {
            wrapper.eq(Notification::getType, type);
        }


        wrapper.eq(Notification::getIsRead, 0);

        List<Notification> notifications = notificationMapper.selectList(wrapper);
        int count = notifications.size();

        if (count > 0) {

            notifications.forEach(notification -> notificationMapper.deleteById(notification.getId()));


            clearUnreadCountCache(userId);

            log.debug("event=notification_revoked operation={} userId={} type={} count={}",
                    "user_batch", userId, type, count);
        }

        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchRecallByType(String type, String reason) {
        if (type == null || type.isEmpty()) {
            log.warn("event=notification_revoke_rejected operation={} cause={}",
                    "type_batch", "missing_type");
            return 0;
        }

        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getType, type)
                .eq(Notification::getIsRead, 0);

        List<Notification> notifications = notificationMapper.selectList(wrapper);
        int count = notifications.size();

        if (count > 0) {

            notifications.stream()
                    .map(Notification::getUserId)
                    .distinct()
                    .forEach(this::clearUnreadCountCache);


            notifications.forEach(notification -> notificationMapper.deleteById(notification.getId()));

            log.debug("event=notification_revoked operation={} type={} count={}",
                    "type_batch", type, count);
        }

        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean revokeLikeNotification(Long resourceOwnerId, Long likerId, Long relatedId) {
        if (resourceOwnerId == null || likerId == null) {
            log.warn("event=notification_revoke_rejected operation={} cause={}",
                    "like", "missing_parameter");
            return false;
        }


        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, resourceOwnerId)
                .eq(Notification::getSenderId, likerId)
                .eq(Notification::getType, "like");

        if (relatedId != null) {
            wrapper.eq(Notification::getRelatedId, relatedId);
        }

        List<Notification> notifications = notificationMapper.selectList(wrapper);
        boolean success = false;

        for (Notification notification : notifications) {

            if (notificationMapper.deleteById(notification.getId()) > 0) {

                if (notification.getIsRead() == 0) {
                    clearUnreadCountCache(resourceOwnerId);
                }
                success = true;
            }
        }

        if (success) {
            log.debug("event=notification_revoked operation={} userId={} senderId={} relatedId={} count={}",
                    "like", resourceOwnerId, likerId, relatedId, notifications.size());
        }

        return success;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int revokeCommentNotifications(Long commentId) {
        if (commentId == null) {
            log.warn("event=notification_revoke_rejected operation={} cause={}",
                    "comment", "missing_comment_id");
            return 0;
        }




        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.eq(Notification::getType, "like")
                        .eq(Notification::getRelatedId, commentId))
                .or()
                .eq(Notification::getRelatedId, commentId)
                .eq(Notification::getType, "comment");

        List<Notification> notifications = notificationMapper.selectList(wrapper);
        int count = 0;


        java.util.Set<Long> affectedUserIds = new java.util.HashSet<>();

        for (Notification notification : notifications) {
            if (notificationMapper.deleteById(notification.getId()) > 0) {
                if (notification.getIsRead() == 0) {
                    affectedUserIds.add(notification.getUserId());
                }
                count++;
            }
        }


        for (Long userId : affectedUserIds) {
            clearUnreadCountCache(userId);
        }

        if (count > 0) {
            log.debug("event=notification_revoked operation={} relatedId={} count={}",
                    "comment", commentId, count);
        }

        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean revokeFollowNotification(Long followeeId, Long followerId) {
        if (followeeId == null || followerId == null) {
            log.warn("event=notification_revoke_rejected operation={} cause={}",
                    "follow", "missing_parameter");
            return false;
        }


        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, followeeId)
                .eq(Notification::getSenderId, followerId)
                .eq(Notification::getType, "follow");


        wrapper.eq(Notification::getRelatedId, followerId);


        wrapper.eq(Notification::getIsRead, 0);

        List<Notification> notifications = notificationMapper.selectList(wrapper);
        boolean success = false;

        for (Notification notification : notifications) {

            if (notification.getGroupCount() != null && notification.getGroupCount() > 1) {

                notification.setGroupCount(notification.getGroupCount() - 1);
                requireNotificationUpdated(notification);


                if (notification.getGroupCount() == 1) {

                    notification.setContent(String.format("%s关注了你",
                            notification.getSenderName() != null ? notification.getSenderName() : "某用户"));
                    requireNotificationUpdated(notification);
                }

                clearUnreadCountCache(followeeId);
                success = true;
            } else {

                if (notificationMapper.deleteById(notification.getId()) > 0) {
                    clearUnreadCountCache(followeeId);
                    success = true;
                }
            }
        }

        if (success) {
            log.debug("event=notification_revoked operation={} userId={} senderId={} count={}",
                    "follow", followeeId, followerId, 1);
        }

        return success;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int revokeNotificationsBySender(Long userId, Long senderId) {
        if (userId == null || senderId == null) {
            log.warn("event=notification_revoke_rejected operation={} cause={}",
                    "sender_batch", "missing_parameter");
            return 0;
        }


        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId)
                .eq(Notification::getSenderId, senderId);

        List<Notification> notifications = notificationMapper.selectList(wrapper);
        int count = 0;

        for (Notification notification : notifications) {
            if (notificationMapper.deleteById(notification.getId()) > 0) {
                if (notification.getIsRead() == 0) {
                    clearUnreadCountCache(userId);
                }
                count++;
            }
        }

        if (count > 0) {
            log.debug("event=notification_revoked operation={} userId={} senderId={} count={}",
                    "sender_batch", userId, senderId, count);
        }

        return count;
    }
}
