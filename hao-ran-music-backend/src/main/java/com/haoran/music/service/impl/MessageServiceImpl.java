




package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.haoran.music.common.config.PrivateMessageConfig;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.SecurityCheckUtil;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.Conversation;
import com.haoran.music.entity.Message;
import com.haoran.music.entity.Album;
import com.haoran.music.entity.MV;
import com.haoran.music.entity.Playlist;
import com.haoran.music.entity.Song;
import com.haoran.music.entity.User;
import com.haoran.music.mapper.AlbumMapper;
import com.haoran.music.mapper.ConversationMapper;
import com.haoran.music.mapper.MessageMapper;
import com.haoran.music.mapper.MVMapper;
import com.haoran.music.mapper.PlaylistMapper;
import com.haoran.music.mapper.SongMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.ContentAccessService;
import com.haoran.music.service.MessageService;
import com.haoran.music.service.UserBlacklistService;
import com.haoran.music.service.PrivateAttachmentService;
import com.haoran.music.enums.PrivateAttachmentPurpose;
import lombok.extern.slf4j.Slf4j;
import com.haoran.music.common.util.CacheHelper;
import com.haoran.music.common.util.RedisUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;




@Slf4j
@Service
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message> implements MessageService {

    private static final int MAX_MEDIA_REFERENCE_LENGTH = 2048;

    private final MessageMapper messageMapper;
    private final ConversationMapper conversationMapper;
    private final UserMapper userMapper;
    private final RedisUtils redisUtils;
    private final UserBlacklistService userBlacklistService;
    private final PrivateMessageConfig privateMessageConfig;
    private final SongMapper songMapper;
    private final AlbumMapper albumMapper;
    private final MVMapper mvMapper;
    private final PlaylistMapper playlistMapper;
    private final ContentAccessService contentAccessService;
    private final PrivateAttachmentService privateAttachmentService;

    public MessageServiceImpl(MessageMapper messageMapper,
                              ConversationMapper conversationMapper,
                              UserMapper userMapper,
                              RedisUtils redisUtils,
                              UserBlacklistService userBlacklistService,
                              PrivateMessageConfig privateMessageConfig,
                              SongMapper songMapper,
                              AlbumMapper albumMapper,
                              MVMapper mvMapper,
                              PlaylistMapper playlistMapper,
                               ContentAccessService contentAccessService,
                               PrivateAttachmentService privateAttachmentService) {
        this.messageMapper = messageMapper;
        this.conversationMapper = conversationMapper;
        this.userMapper = userMapper;
        this.redisUtils = redisUtils;
        this.userBlacklistService = userBlacklistService;
        this.privateMessageConfig = privateMessageConfig;
        this.songMapper = songMapper;
        this.albumMapper = albumMapper;
        this.mvMapper = mvMapper;
        this.playlistMapper = playlistMapper;
        this.contentAccessService = contentAccessService;
        this.privateAttachmentService = privateAttachmentService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long sendMessage(Long senderId, Long receiverId, String messageType,
                           String content, Long resourceId, String resourceData) {
        return sendMessageInternal(senderId, receiverId, messageType, content, resourceId, false);
    }

    private Long sendMessageInternal(Long senderId, Long receiverId, String messageType,
                                     String content, Long resourceId, boolean contentAlreadyNormalized) {

        if (ObjectUtils.isEmpty(senderId) || ObjectUtils.isEmpty(receiverId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "发送者和接收者ID不能为空");
        }
        if (senderId.equals(receiverId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不能给自己发送消息");
        }
        if (ObjectUtils.isEmpty(messageType)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "消息类型不能为空");
        }

        User sender = userMapper.selectById(senderId);
        User receiver = userMapper.selectById(receiverId);
        if (!UserAccountStatusUtil.canInteract(sender)) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    UserAccountStatusUtil.currentUnavailableMessage(sender) + "，无法发送消息");
        }
        if (!UserAccountStatusUtil.canInteract(receiver)) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    UserAccountStatusUtil.targetUnavailableMessage(receiver) + "，无法发送消息");
        }

        Boolean senderBlacklisted = userBlacklistService.isBlacklisted(senderId, receiverId);
        if (Boolean.TRUE.equals(senderBlacklisted)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "您已将该用户加入黑名单，无法发送消息");
        }


        Boolean receiverBlacklisted = userBlacklistService.isBlacklisted(receiverId, senderId);
        if (Boolean.TRUE.equals(receiverBlacklisted)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "对方已将您加入黑名单，无法发送消息");
        }


        if (isUserBlocked(senderId, receiverId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "您已被对方屏蔽，无法发送消息");
        }

        requireSharedResourceAccess(messageType, resourceId, senderId, receiverId);


        Message message = new Message();
        message.setSenderId(senderId);
        message.setReceiverId(receiverId);
        message.setMessageType(messageType);
        String storedContent = contentAlreadyNormalized ? content : normalizeMessageContent(messageType, content);
        message.setContent(storedContent);
        message.setResourceId(resourceId);

        message.setResourceData(null);
        message.setIsRead(0);
        message.setIsRecalled(0);
        message.setIsDeletedBySender(0);
        message.setIsDeletedByReceiver(0);
        message.setStatus("normal");

        messageMapper.insert(message);


        updateOrCreateConversation(message);


        incrementUnreadCount(receiverId, senderId);

        log.debug("发送消息成功: senderId={}, receiverId={}, type={}, messageId={}",
                senderId, receiverId, messageType, message.getId());

        return message.getId();
    }

    @Override
    public Long sendTextMessage(Long senderId, Long receiverId, String content) {
        if (ObjectUtils.isEmpty(content) || content.trim().isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "消息内容不能为空");
        }
        int maxLength = privateMessageConfig.getTextMaxLength();
        if (content.length() > maxLength) {
            content = content.substring(0, maxLength) + "...";
        }
        return sendMessage(senderId, receiverId, "text", content.trim(), null, null);
    }

    @Override
    public Long sendEmojiMessage(Long senderId, Long receiverId, String emojiCode) {
        if (ObjectUtils.isEmpty(emojiCode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "表情代码不能为空");
        }
        return sendMessage(senderId, receiverId, "emoji", emojiCode, null, null);
    }

    @Override
    public Long sendImageMessage(Long senderId, Long receiverId, String imageUrl) {
        if (ObjectUtils.isEmpty(imageUrl)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "图片URL不能为空");
        }
        return sendMessage(senderId, receiverId, "image", imageUrl, null, null);
    }









    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long sendImageMessage(Long senderId, Long receiverId, Long attachmentAssetId) {
        if (ObjectUtils.isEmpty(attachmentAssetId) || attachmentAssetId <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "私有图片资产ID不能为空");
        }
        Long messageId = sendMessage(senderId, receiverId, "image", "[图片]", null, null);
        int updated = messageMapper.update(null, new UpdateWrapper<Message>()
                .eq("id", messageId)
                .eq("sender_id", senderId)
                .eq("message_type", "image")
                .set("content", null)
                .set("resource_id", attachmentAssetId));
        if (updated != 1) {
            throw new BusinessException("图片消息资产登记失败");
        }
        privateAttachmentService.bindAssets(senderId,
                PrivateAttachmentPurpose.MESSAGE_IMAGE.name(),
                Collections.singletonList(attachmentAssetId),
                PrivateAttachmentPurpose.MESSAGE_IMAGE.getTargetType(), messageId);
        return messageId;
    }

    @Override
    public Long sendSongMessage(Long senderId, Long receiverId, Long songId, String songData) {
        if (ObjectUtils.isEmpty(songId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "歌曲ID不能为空");
        }
        return sendMessage(senderId, receiverId, "song", "分享了一首歌曲", songId, songData);
    }

    @Override
    public Long sendAlbumMessage(Long senderId, Long receiverId, Long albumId, String albumData) {
        if (ObjectUtils.isEmpty(albumId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "专辑ID不能为空");
        }
        return sendMessage(senderId, receiverId, "album", "分享了一张专辑", albumId, albumData);
    }

    @Override
    public Long sendMvMessage(Long senderId, Long receiverId, Long mvId, String mvData) {
        if (ObjectUtils.isEmpty(mvId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "视频ID不能为空");
        }
        return sendMessage(senderId, receiverId, "mv", "分享了一个视频", mvId, mvData);
    }

    @Override
    public Long sendPlaylistMessage(Long senderId, Long receiverId, Long playlistId, String playlistData) {
        if (ObjectUtils.isEmpty(playlistId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "歌单ID不能为空");
        }
        return sendMessage(senderId, receiverId, "playlist", "分享了一个歌单", playlistId, playlistData);
    }

    @Override
    public IPage<Message> getChatMessages(Long currentUserId, Long otherUserId, PageQuery pageQuery) {
        if (ObjectUtils.isEmpty(currentUserId) || ObjectUtils.isEmpty(otherUserId)) {
            return new Page<>();
        }

        int current = pageQuery.getPage();
        int size = pageQuery.getSize();
        long offset = (long) (current - 1) * size;
        Page<Message> page = new Page<>(current, size);

        List<Message> messages = messageMapper.getMessagesBetweenUsers(
                currentUserId, otherUserId, offset, size);


        Collections.reverse(messages);

        page.setRecords(messages);
        page.setTotal(messageMapper.countMessagesBetweenUsers(currentUserId, otherUserId));

        return page;
    }

    @Override
    public IPage<Map<String, Object>> getChatMessageViews(Long currentUserId, Long otherUserId, PageQuery pageQuery) {
        return toMessageViewPage(getChatMessages(currentUserId, otherUserId, pageQuery), currentUserId);
    }

    @Override
    public List<Map<String, Object>> getConversations(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return new ArrayList<>();
        }

        List<Conversation> conversations = conversationMapper.getConversationsByUserId(userId);
        if (ObjectUtils.isEmpty(conversations)) {
            return new ArrayList<>();
        }

        List<Long> otherUserIds = conversations.stream()
                .map(conv -> conv.getUserAId().equals(userId) ? conv.getUserBId() : conv.getUserAId())
                .distinct()
                .collect(Collectors.toList());
        Map<Long, User> otherUsersById = ObjectUtils.isEmpty(otherUserIds)
                ? Collections.emptyMap()
                : userMapper.selectBatchIds(otherUserIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user, (left, right) -> left, LinkedHashMap::new));

        return conversations.stream().map(conv -> {
            Map<String, Object> result = new HashMap<>();


            boolean isUserA = conv.getUserAId().equals(userId);
            Long otherUserId = isUserA ? conv.getUserBId() : conv.getUserAId();
            int unreadCount = isUserA ? conv.getUserAUnreadCount() : conv.getUserBUnreadCount();
            boolean pinned = isUserA ? conv.getUserAPinned() == 1 : conv.getUserBPinned() == 1;


            User otherUser = otherUsersById.get(otherUserId);

            result.put("conversationId", conv.getId());
            result.put("otherUserId", otherUserId);
            result.put("otherUserNickname", otherUser != null ? otherUser.getNickname() : "未知用户");
            result.put("otherUserName", otherUser != null ? otherUser.getUsername() : "未知用户");
            result.put("otherUserAvatar", otherUser != null ? otherUser.getAvatar() : null);
            result.put("otherUserStatus", otherUser != null ? otherUser.getStatus() : null);
            result.put("otherUserIsBanned", otherUser != null ? otherUser.getIsBanned() : null);
            result.put("otherUserDeleted", otherUser == null || CommonConstants.DELETED.equals(otherUser.getDeleted()));
            result.put("lastMessage", conv.getLastMessage());
            result.put("lastMessageType", conv.getLastMessageType());
            result.put("lastMessageTime", conv.getLastMessageTime());
            result.put("unreadCount", unreadCount);
            result.put("isPinned", pinned);
            result.put("isBlocked", isUserA ? conv.getUserABlocked() == 1 : conv.getUserBBlocked() == 1);

            return result;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markAsRead(Long messageId, Long userId) {
        if (ObjectUtils.isEmpty(messageId) || ObjectUtils.isEmpty(userId)) {
            return false;
        }

        Message message = messageMapper.selectById(messageId);
        if (message == null || !message.getReceiverId().equals(userId)) {
            return false;
        }
        if (Integer.valueOf(1).equals(message.getIsRead())
                || Integer.valueOf(1).equals(message.getIsRecalled())) {
            return true;
        }

        int updated = messageMapper.markActiveMessageAsRead(messageId, userId, LocalDateTime.now());

        if (updated > 0) {

            decrementUnreadCount(userId, message.getSenderId());
            return true;
        }

        Message current = messageMapper.selectById(messageId);
        return current != null
                && userId.equals(current.getReceiverId())
                && (Integer.valueOf(1).equals(current.getIsRead())
                || Integer.valueOf(1).equals(current.getIsRecalled()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int markAllAsRead(Long currentUserId, Long otherUserId) {
        if (ObjectUtils.isEmpty(currentUserId) || ObjectUtils.isEmpty(otherUserId)) {
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = messageMapper.update(null, new LambdaUpdateWrapper<Message>()
                .eq(Message::getReceiverId, currentUserId)
                .eq(Message::getSenderId, otherUserId)
                .eq(Message::getIsRead, 0)
                .eq(Message::getIsRecalled, 0)
                .eq(Message::getIsDeleted, 0)
                .set(Message::getIsRead, 1)
                .set(Message::getReadTime, now));


        Conversation conversation = conversationMapper.getConversationBetweenUsers(currentUserId, otherUserId);
        if (conversation != null) {
            boolean isUserA = conversation.getUserAId().equals(currentUserId);
            if (isUserA) {
                conversation.setUserAUnreadCount(0);
            } else {
                conversation.setUserBUnreadCount(0);
            }
            conversationMapper.updateById(conversation);


            clearUnreadCountCache(currentUserId, otherUserId);
        }

        log.debug("标记所有消息为已读: currentUserId={}, otherUserId={}, count={}",
                currentUserId, otherUserId, updated);

        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public boolean recallMessage(Long messageId, Long userId) {
        if (ObjectUtils.isEmpty(messageId) || ObjectUtils.isEmpty(userId)) {
            return false;
        }

        Message message = messageMapper.selectById(messageId);
        if (message == null) {
            return false;
        }


        if (!message.getSenderId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权撤回此消息");
        }


        if (Integer.valueOf(1).equals(message.getIsRecalled())) {
            return true;
        }


        LocalDateTime createTime = message.getCreateTime();
        if (createTime == null) {
            return false;
        }

        LocalDateTime recallDeadline = createTime.plusMinutes(privateMessageConfig.getRecallTimeLimitMinutes());
        if (LocalDateTime.now().isAfter(recallDeadline)) {
            return false;
        }


        int unreadUpdated = messageMapper.recallUnreadMessage(messageId, userId);
        int updated = unreadUpdated > 0
                ? unreadUpdated
                : messageMapper.recallReadMessage(messageId, userId);

        if (updated > 0) {
            if (unreadUpdated > 0) {
                decrementUnreadCount(message.getReceiverId(), message.getSenderId());
            }
            refreshConversationSummaryAfterRecall(message);
            if ("image".equals(message.getMessageType()) && ObjectUtils.isNotEmpty(message.getResourceId())) {
                privateAttachmentService.releaseTargetReferences(
                        PrivateAttachmentPurpose.MESSAGE_IMAGE.getTargetType(), messageId);
            }
            log.debug("撤回消息成功: messageId={}, userId={}", messageId, userId);
        }

        if (updated > 0) {
            return true;
        }


        Message current = messageMapper.selectById(messageId);
        return current != null
                && userId.equals(current.getSenderId())
                && Integer.valueOf(1).equals(current.getIsRecalled());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteMessage(Long messageId, Long userId) {
        if (ObjectUtils.isEmpty(messageId) || ObjectUtils.isEmpty(userId)) {
            return false;
        }

        Message message = messageMapper.selectById(messageId);
        if (message == null) {
            return false;
        }


        boolean isSender = message.getSenderId().equals(userId);
        boolean isReceiver = message.getReceiverId().equals(userId);

        if (!isSender && !isReceiver) {
            return false;
        }


        if (isSender) {
            message.setIsDeletedBySender(1);
        }
        if (isReceiver) {
            message.setIsDeletedByReceiver(1);
        }

        int updated = messageMapper.updateById(message);


        if (message.getIsDeletedBySender() == 1 && message.getIsDeletedByReceiver() == 1) {
            messageMapper.deleteById(messageId);
        }

        if (updated > 0) {
            return true;
        }


        Message current = messageMapper.selectById(messageId);
        return current != null
                && userId.equals(current.getSenderId())
                && Integer.valueOf(1).equals(current.getIsRecalled());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchDeleteMessages(List<Long> messageIds, Long userId) {
        List<Long> ids = normalizeMessageIds(messageIds, 50);
        List<Message> messages = messageMapper.selectBatchIds(ids);
        Map<Long, Message> byId = messages.stream().collect(Collectors.toMap(Message::getId, message -> message));

        for (Long id : ids) {
            Message message = byId.get(id);
            if (message == null || !isMessageParticipant(message, userId)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "包含不存在或无权操作的消息");
            }
        }

        int deleted = 0;
        for (Long id : ids) {
            Message message = byId.get(id);
            boolean sender = message.getSenderId().equals(userId);
            boolean alreadyDeleted = sender
                    ? Integer.valueOf(1).equals(message.getIsDeletedBySender())
                    : Integer.valueOf(1).equals(message.getIsDeletedByReceiver());
            if (alreadyDeleted) {
                continue;
            }
            if (sender) message.setIsDeletedBySender(1);
            else message.setIsDeletedByReceiver(1);
            if (messageMapper.updateById(message) <= 0) {
                throw new BusinessException(ResultCode.ERROR, "批量删除消息失败");
            }
            if (Integer.valueOf(1).equals(message.getIsDeletedBySender())
                    && Integer.valueOf(1).equals(message.getIsDeletedByReceiver())) {
                messageMapper.deleteById(id);
            }
            deleted++;
        }
        return deleted;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> forwardMessages(List<Long> messageIds, Long senderId, Long receiverId) {
        List<Long> ids = normalizeMessageIds(messageIds, 20);
        if (senderId == null || receiverId == null || senderId.equals(receiverId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "接收人不合法");
        }
        List<Message> messages = messageMapper.selectBatchIds(ids);
        Map<Long, Message> byId = messages.stream().collect(Collectors.toMap(Message::getId, message -> message));

        for (Long id : ids) {
            Message message = byId.get(id);
            if (message == null || !isVisibleTo(message, senderId)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "包含不存在、已删除或无权转发的消息");
            }
            if (Integer.valueOf(1).equals(message.getIsRecalled())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "撤回的消息不能转发");
            }
            if (!"normal".equals(message.getStatus())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "当前消息状态不支持转发");
            }
            if ("image".equals(message.getMessageType())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "私密图片暂不支持转发，请重新发送图片");
            }
        }

        List<Long> forwardedIds = new ArrayList<>(ids.size());
        for (Long id : ids) {
            Message source = byId.get(id);
            forwardedIds.add(sendMessageInternal(senderId, receiverId, source.getMessageType(),
                    source.getContent(), source.getResourceId(), true));
        }
        return forwardedIds;
    }

    private List<Long> normalizeMessageIds(List<Long> messageIds, int maxSize) {
        if (messageIds == null || messageIds.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请选择消息");
        }
        LinkedHashSet<Long> unique = messageIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (unique.isEmpty() || unique.size() > maxSize) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "消息数量不合法");
        }
        return new ArrayList<>(unique);
    }

    private boolean isMessageParticipant(Message message, Long userId) {
        return message.getSenderId().equals(userId) || message.getReceiverId().equals(userId);
    }

    private boolean isVisibleTo(Message message, Long userId) {
        if (!isMessageParticipant(message, userId)) return false;
        if (message.getSenderId().equals(userId)) {
            return !Integer.valueOf(1).equals(message.getIsDeletedBySender());
        }
        return !Integer.valueOf(1).equals(message.getIsDeletedByReceiver());
    }

    @Override
    public int getTotalUnreadCount(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return 0;
        }

        String cacheKey = privateMessageConfig.getUnreadCountPrefix() + userId + ":total";

        return CacheHelper.getOrLoad(
                redisUtils,
                cacheKey,
                () -> messageMapper.getUnreadCount(userId),
                privateMessageConfig.getUnreadCountCacheTtlSeconds(),
                TimeUnit.SECONDS,
                Integer.class
        );
    }

    @Override
    public int getUnreadCountFromUser(Long userId, Long otherUserId) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(otherUserId)) {
            return 0;
        }

        String cacheKey = privateMessageConfig.getUnreadCountPrefix() + userId + ":" + otherUserId;

        return CacheHelper.getOrLoad(
                redisUtils,
                cacheKey,
                () -> messageMapper.getUnreadCountFromUser(userId, otherUserId),
                privateMessageConfig.getUnreadCountCacheTtlSeconds(),
                TimeUnit.SECONDS,
                Integer.class
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean setConversationPinned(Long currentUserId, Long otherUserId, boolean pinned) {
        if (ObjectUtils.isEmpty(currentUserId) || ObjectUtils.isEmpty(otherUserId)) {
            return false;
        }

        Conversation conversation = conversationMapper.getConversationBetweenUsers(currentUserId, otherUserId);
        if (conversation == null) {
            return false;
        }

        boolean isUserA = conversation.getUserAId().equals(currentUserId);
        if (isUserA) {
            conversation.setUserAPinned(pinned ? 1 : 0);
        } else {
            conversation.setUserBPinned(pinned ? 1 : 0);
        }

        int updated = conversationMapper.updateById(conversation);

        if (updated > 0) {

            String cacheKey = privateMessageConfig.getConversationPrefix() + currentUserId + ":" + otherUserId;
            CacheHelper.delete(redisUtils, cacheKey);
        }

        return updated > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean setUserBlocked(Long currentUserId, Long otherUserId, boolean blocked) {
        if (ObjectUtils.isEmpty(currentUserId) || ObjectUtils.isEmpty(otherUserId)) {
            return false;
        }

        Conversation conversation = conversationMapper.getConversationBetweenUsers(currentUserId, otherUserId);
        if (conversation == null) {
            return false;
        }

        boolean isUserA = conversation.getUserAId().equals(currentUserId);
        if (isUserA) {
            conversation.setUserABlocked(blocked ? 1 : 0);
        } else {
            conversation.setUserBBlocked(blocked ? 1 : 0);
        }

        int updated = conversationMapper.updateById(conversation);

        if (updated > 0) {

            String cacheKey = privateMessageConfig.getConversationPrefix() + currentUserId + ":" + otherUserId;
            CacheHelper.delete(redisUtils, cacheKey);
        }

        return updated > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int clearChatHistory(Long currentUserId, Long otherUserId) {
        if (ObjectUtils.isEmpty(currentUserId) || ObjectUtils.isEmpty(otherUserId)) {
            return 0;
        }

        int count = 0;
        count += messageMapper.update(null, new LambdaUpdateWrapper<Message>()
                .eq(Message::getSenderId, currentUserId)
                .eq(Message::getReceiverId, otherUserId)
                .eq(Message::getIsDeletedBySender, 0)
                .set(Message::getIsDeletedBySender, 1));
        count += messageMapper.update(null, new LambdaUpdateWrapper<Message>()
                .eq(Message::getSenderId, otherUserId)
                .eq(Message::getReceiverId, currentUserId)
                .eq(Message::getIsDeletedByReceiver, 0)
                .set(Message::getIsDeletedByReceiver, 1));

        messageMapper.delete(new LambdaQueryWrapper<Message>()
                .eq(Message::getSenderId, currentUserId)
                .eq(Message::getReceiverId, otherUserId)
                .eq(Message::getIsDeletedBySender, 1)
                .eq(Message::getIsDeletedByReceiver, 1));
        messageMapper.delete(new LambdaQueryWrapper<Message>()
                .eq(Message::getSenderId, otherUserId)
                .eq(Message::getReceiverId, currentUserId)
                .eq(Message::getIsDeletedBySender, 1)
                .eq(Message::getIsDeletedByReceiver, 1));

        log.debug("清空聊天记录: currentUserId={}, otherUserId={}, count={}",
                currentUserId, otherUserId, count);

        return count;
    }

    @Override
    public IPage<Message> searchMessages(Long userId, String keyword, PageQuery pageQuery) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(keyword)) {
            return new Page<>();
        }

        Page<Message> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());

        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.and(sent -> sent.eq(Message::getSenderId, userId)
                                .eq(Message::getIsDeletedBySender, 0))
                        .or(received -> received.eq(Message::getReceiverId, userId)
                                .eq(Message::getIsDeletedByReceiver, 0)))
                .like(Message::getContent, keyword)
                .eq(Message::getIsDeleted, 0)
                .orderByDesc(Message::getCreateTime);

        return messageMapper.selectPage(page, wrapper);
    }

    @Override
    public IPage<Map<String, Object>> searchMessageViews(Long userId, String keyword, PageQuery pageQuery) {
        return toMessageViewPage(searchMessages(userId, keyword, pageQuery), userId);
    }



    private IPage<Map<String, Object>> toMessageViewPage(IPage<Message> source, Long viewerId) {
        Page<Map<String, Object>> page = new Page<>(source.getCurrent(), source.getSize());
        page.setTotal(source.getTotal());
        page.setRecords(source.getRecords().stream()
                .map(message -> buildMessageView(message, viewerId))
                .collect(Collectors.toList()));
        return page;
    }

    private Map<String, Object> buildMessageView(Message message, Long viewerId) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", message.getId());
        result.put("senderId", message.getSenderId());
        result.put("receiverId", message.getReceiverId());
        result.put("messageType", message.getMessageType());
        boolean recalled = Integer.valueOf(1).equals(message.getIsRecalled());
        boolean managedImage = "image".equals(message.getMessageType())
                && ObjectUtils.isNotEmpty(message.getResourceId());
        result.put("content", recalled || managedImage ? null : message.getContent());
        boolean resourceAvailable = !recalled && (managedImage
                || canAccessSharedResource(message.getMessageType(), message.getResourceId(), viewerId));
        result.put("resourceId", !managedImage && resourceAvailable ? message.getResourceId() : null);
        if (managedImage) {
            result.put("attachmentAssetId", resourceAvailable ? message.getResourceId() : null);
            result.put("resourceAvailable", resourceAvailable);
        }
        if (isSharedResourceType(message.getMessageType())) {
            result.put("resourceAvailable", resourceAvailable);
        }
        result.put("isRead", message.getIsRead());
        result.put("isRecalled", recalled ? 1 : 0);
        result.put("status", message.getStatus());
        result.put("createTime", message.getCreateTime());
        return result;
    }

    private void requireSharedResourceAccess(String messageType, Long resourceId,
                                             Long senderId, Long receiverId) {
        if (!isSharedResourceType(messageType)) {
            if (resourceId != null) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "该消息类型不支持资源ID");
            }
            return;
        }
        if (resourceId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "分享资源ID不能为空");
        }

        if ("song".equals(messageType)) {
            Song song = songMapper.selectById(resourceId);
            contentAccessService.requireSongAccess(song, senderId);
            contentAccessService.requireSongPreviewAccess(song, receiverId);
            return;
        }
        if ("album".equals(messageType)) {
            Album album = albumMapper.selectById(resourceId);
            if (album != null && Integer.valueOf(0).equals(album.getAllowShare())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "该专辑不允许分享");
            }
            contentAccessService.requireAlbumAccess(album, senderId);
            contentAccessService.requireAlbumMetadataAccess(album, receiverId);
            return;
        }
        if ("mv".equals(messageType)) {
            MV mv = mvMapper.selectById(resourceId);
            contentAccessService.requireMvAccess(mv, senderId);
            contentAccessService.requireMvPreviewAccess(mv, receiverId);
            return;
        }

        Playlist playlist = playlistMapper.selectById(resourceId);
        if (playlist != null && Integer.valueOf(0).equals(playlist.getAllowShare())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "该歌单不允许分享");
        }
        contentAccessService.requirePlaylistAccess(playlist, senderId);
        contentAccessService.requirePlaylistAccess(playlist, receiverId);
    }

    private boolean canAccessSharedResource(String messageType, Long resourceId, Long viewerId) {
        if (!isSharedResourceType(messageType)) {
            return resourceId == null;
        }
        if (resourceId == null || viewerId == null) {
            return false;
        }
        try {
            if ("song".equals(messageType)) {
                contentAccessService.requireSongPreviewAccess(songMapper.selectById(resourceId), viewerId);
            } else if ("album".equals(messageType)) {
                contentAccessService.requireAlbumMetadataAccess(albumMapper.selectById(resourceId), viewerId);
            } else if ("mv".equals(messageType)) {
                contentAccessService.requireMvPreviewAccess(mvMapper.selectById(resourceId), viewerId);
            } else {
                contentAccessService.requirePlaylistAccess(playlistMapper.selectById(resourceId), viewerId);
            }
            return true;
        } catch (BusinessException unavailable) {
            return false;
        }
    }

    private boolean isSharedResourceType(String messageType) {
        return "song".equals(messageType)
                || "album".equals(messageType)
                || "mv".equals(messageType)
                || "playlist".equals(messageType);
    }


    private String normalizeMessageContent(String messageType, String content) {
        if (ObjectUtils.isEmpty(content)) {
            return content;
        }

        if ("image".equals(messageType) || "emoji".equals(messageType)) {
            String mediaUrl = content.trim();
            if (mediaUrl.length() > MAX_MEDIA_REFERENCE_LENGTH || mediaUrl.regionMatches(true, 0, "data:", 0, 5)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "媒体地址过长或不支持内联数据");
            }
            if (!isAllowedMediaUrl(mediaUrl)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "媒体地址不合法");
            }
            return mediaUrl;
        }

        SecurityCheckUtil.CheckResult contentCheck = SecurityCheckUtil.checkCommunityText(content, "私信内容");
        if (!contentCheck.isSafe()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, contentCheck.getMessage());
        }
        return SecurityCheckUtil.escapeHtml(contentCheck.getCleanedValue());
    }

    private boolean isAllowedMediaUrl(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")
                || lower.startsWith("/")) {
            return true;
        }
        return !lower.contains(":") && !lower.startsWith("//");
    }





    private void refreshConversationSummaryAfterRecall(Message recalledMessage) {
        Conversation conversation = conversationMapper.getConversationBetweenUsersForUpdate(
                recalledMessage.getSenderId(), recalledMessage.getReceiverId());
        if (conversation == null) {
            return;
        }

        Message latest = messageMapper.getLatestActiveMessageBetweenUsers(
                recalledMessage.getSenderId(), recalledMessage.getReceiverId());
        if (latest == null) {
            conversation.setLastMessage("[消息已撤回]");
            conversation.setLastMessageType("recalled");
            conversation.setLastMessageTime(recalledMessage.getCreateTime());
        } else {
            conversation.setLastMessage(conversationSummary(latest));
            conversation.setLastMessageType(latest.getMessageType());
            conversation.setLastMessageTime(latest.getCreateTime());
        }
        conversationMapper.updateById(conversation);
    }




    private void updateOrCreateConversation(Message message) {
        Long userAId = message.getSenderId();
        Long userBId = message.getReceiverId();

        if (userAId > userBId) {
            Long temp = userAId;
            userAId = userBId;
            userBId = temp;
        }

        Conversation conversation = conversationMapper.getConversationBetweenUsersForUpdate(userAId, userBId);
        String summary = conversationSummary(message);

        if (conversation == null) {

            conversation = new Conversation();
            conversation.setUserAId(userAId);
            conversation.setUserBId(userBId);
            conversation.setLastMessage(summary);
            conversation.setLastMessageType(message.getMessageType());
            conversation.setLastMessageTime(message.getCreateTime());
            conversation.setUserAUnreadCount(0);
            conversation.setUserBUnreadCount(0);
            conversation.setUserAPinned(0);
            conversation.setUserBPinned(0);
            conversation.setUserABlocked(0);
            conversation.setUserBBlocked(0);
            conversation.setStatus("active");
            conversationMapper.insert(conversation);
        } else {

            conversation.setLastMessage(summary);
            conversation.setLastMessageType(message.getMessageType());
            conversation.setLastMessageTime(message.getCreateTime());
            conversationMapper.updateById(conversation);
        }
    }




    private String conversationSummary(Message message) {
        return "image".equals(message.getMessageType()) ? "[图片]" : message.getContent();
    }




    private void incrementUnreadCount(Long receiverId, Long senderId) {

        Long userAId = receiverId < senderId ? receiverId : senderId;
        Long userBId = receiverId < senderId ? senderId : receiverId;

        Conversation conversation = conversationMapper.getConversationBetweenUsers(userAId, userBId);
        if (conversation != null) {
            boolean isReceiverUserA = conversation.getUserAId().equals(receiverId);
            if (isReceiverUserA) {
                conversation.setUserAUnreadCount(conversation.getUserAUnreadCount() + 1);
            } else {
                conversation.setUserBUnreadCount(conversation.getUserBUnreadCount() + 1);
            }
            conversationMapper.updateById(conversation);
        }


        clearUnreadCountCache(receiverId);
    }




    private void decrementUnreadCount(Long receiverId, Long senderId) {
        conversationMapper.decrementUnreadCount(receiverId, senderId);


        clearUnreadCountCache(receiverId, senderId);
    }




    private void clearUnreadCountCache(Long userId, Long... otherUserIds) {
        CacheHelper.delete(redisUtils, privateMessageConfig.getUnreadCountPrefix() + userId + ":total");

        if (otherUserIds != null && otherUserIds.length > 0) {
            for (Long otherUserId : otherUserIds) {
                CacheHelper.delete(redisUtils, privateMessageConfig.getUnreadCountPrefix() + userId + ":" + otherUserId);
            }
        }
    }




    private boolean isUserBlocked(Long senderId, Long receiverId) {

        Long userAId = senderId < receiverId ? senderId : receiverId;
        Long userBId = senderId < receiverId ? receiverId : senderId;

        Conversation conversation = conversationMapper.getConversationBetweenUsers(userAId, userBId);
        if (conversation == null) {
            return false;
        }


        boolean isSenderUserA = conversation.getUserAId().equals(senderId);
        if (isSenderUserA) {
            return conversation.getUserBBlocked() == 1;
        } else {
            return conversation.getUserABlocked() == 1;
        }
    }
}
