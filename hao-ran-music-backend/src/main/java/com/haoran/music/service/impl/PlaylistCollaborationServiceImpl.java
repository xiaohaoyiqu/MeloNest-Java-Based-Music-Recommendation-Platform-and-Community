package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.haoran.music.common.config.PlaylistCollaborationConfig;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.context.UserContext;
import com.haoran.music.common.dto.PageResult;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.Playlist;
import com.haoran.music.entity.PlaylistCollaborator;
import com.haoran.music.entity.PlaylistOperationLog;
import com.haoran.music.entity.Song;
import com.haoran.music.entity.User;
import com.haoran.music.entity.PlaylistSong;
import com.haoran.music.mapper.PlaylistCollaboratorMapper;
import com.haoran.music.mapper.PlaylistMapper;
import com.haoran.music.mapper.PlaylistOperationLogMapper;
import com.haoran.music.mapper.PlaylistSongMapper;
import com.haoran.music.mapper.SongMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.PlaylistCollaborationService;
import com.haoran.music.service.PlaylistCollaborationAuditService;
import com.haoran.music.service.NotificationService;
import com.haoran.music.vo.playlist.CollaboratorVO;
import com.haoran.music.vo.playlist.PlaylistOperationLogVO;
import com.haoran.music.vo.playlist.PlaylistVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

   
                      
                             
   
@Slf4j
@Service
public class PlaylistCollaborationServiceImpl implements PlaylistCollaborationService {

    private static final int DEFAULT_RECOMMEND_LIMIT = 10;
    private static final int MAX_RECOMMEND_LIMIT = 20;
    private static final int MAX_RECOMMEND_PLAYLISTS = 100;
    private static final int RECOMMEND_CANDIDATE_MULTIPLIER = 4;

    @Resource
    private PlaylistCollaborationConfig playlistCollaborationConfig;

    @Resource
    private PlaylistMapper playlistMapper;

    @Resource
    private PlaylistCollaboratorMapper playlistCollaboratorMapper;

    @Resource
    private PlaylistOperationLogMapper playlistOperationLogMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private SongMapper songMapper;

    @Resource
    private PlaylistSongMapper playlistSongMapper;

    @Resource
    private PlaylistCollaborationAuditService playlistCollaborationAuditService;

    @Resource
    private NotificationService notificationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enableCollaboration(Long playlistId) {
        Playlist playlist = playlistMapper.selectByIdForUpdate(playlistId);
        if (ObjectUtils.isEmpty(playlist)) {
            throw new RuntimeException("歌单不存在");
        }

        Long userId = getCurrentUserId();
        User ownerUser = userMapper.selectById(userId);
        UserAccountStatusUtil.requireCanInteract(ownerUser, "开启歌单协作");

        if (!playlist.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有歌单创建者可以开启协作");
        }

        PlaylistCollaborator existingOwner = playlistCollaboratorMapper.selectAccepted(playlistId, userId);
        if (existingOwner != null && "owner".equals(existingOwner.getRole())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该歌单已开启协作");
        }

        PlaylistCollaborator owner = new PlaylistCollaborator();
        owner.setPlaylistId(playlistId);
        owner.setUserId(userId);
        owner.setRole("owner");
        owner.setCanAdd(1);
        owner.setCanRemove(1);
        owner.setCanEdit(1);
        owner.setJoinedTime(LocalDateTime.now());
        owner.setStatus("accepted");
        owner.setDeleted(CommonConstants.NOT_DELETED);
        PlaylistCollaborator historicalOwner = playlistCollaboratorMapper
                .selectAnyByPlaylistAndUser(playlistId, userId);
        int changed = historicalOwner != null && "owner".equals(historicalOwner.getRole())
                ? playlistCollaboratorMapper.restoreClosedOwner(historicalOwner.getId())
                : playlistCollaboratorMapper.insertIgnore(owner);
        if (changed != 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该歌单已开启协作");
        }

        boolean restored = historicalOwner != null;
        auditAndRecord(playlistId, userId, null, restored ? "restore" : "enable", null,
                restored ? "v1;collaboration=closed" : "v1;collaboration=disabled",
                "v1;collaboration=active", restored ? "恢复了歌单协作" : "开启了歌单协作");
    }

       
                                 
      
                             
  
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disableCollaboration(Long playlistId) {
        Playlist playlist = playlistMapper.selectByIdForUpdate(playlistId);
        if (ObjectUtils.isEmpty(playlist)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌单不存在");
        }
        Long userId = getCurrentUserId();
        UserAccountStatusUtil.requireCanInteract(userMapper.selectById(userId), "关闭歌单协作");
        if (!Objects.equals(playlist.getUserId(), userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有歌单创建者可以关闭协作");
        }

        PlaylistCollaborator activeOwner = playlistCollaboratorMapper.selectAccepted(playlistId, userId);
        if (activeOwner == null || !"owner".equals(activeOwner.getRole())) {
            PlaylistCollaborator historicalOwner = playlistCollaboratorMapper
                    .selectAnyByPlaylistAndUser(playlistId, userId);
            if (historicalOwner != null && "owner".equals(historicalOwner.getRole())
                    && "closed".equals(historicalOwner.getStatus())
                    && CommonConstants.DELETED.equals(historicalOwner.getDeleted())) {
                return;
            }
            throw new BusinessException(ResultCode.BAD_REQUEST, "该歌单尚未开启协作");
        }

        List<Long> affectedCollaborators = playlistCollaboratorMapper
                .selectActiveNonOwnerUserIds(playlistId);
        int closedRows = playlistCollaboratorMapper.closeAllByPlaylistId(playlistId);
        if (closedRows <= 0) {
            throw new BusinessException("关闭歌单协作失败，请稍后重试");
        }
        String eventId = auditAndRecord(playlistId, userId, null, "disable", null,
                "v1;collaboration=active;activeCollaborators=" + affectedCollaborators.size(),
                "v1;collaboration=closed;closedRows=" + closedRows, "关闭了歌单协作");
        for (Long collaboratorId : affectedCollaborators) {
            notificationService.sendSystemNotificationOnce(collaboratorId, "歌单协作已关闭",
                    "歌单《" + playlist.getName() + "》的协作已由所有者关闭", "/playlist/" + playlistId,
                    "playlist-collaboration:" + eventId + ":" + collaboratorId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void inviteCollaborator(Long playlistId, Long userId, String role) {
        Playlist playlist = playlistMapper.selectByIdForUpdate(playlistId);
        if (ObjectUtils.isEmpty(playlist)) {
            throw new RuntimeException("歌单不存在");
        }

        Long currentUserId = getCurrentUserId();
        User currentUser = userMapper.selectById(currentUserId);
        UserAccountStatusUtil.requireCanInteract(currentUser, "邀请协作者");

        if (!playlist.getUserId().equals(currentUserId)) {
            throw new RuntimeException("只有歌单创建者可以邀请协作者");
        }

        User targetUser = userMapper.selectById(userId);
        if (!UserAccountStatusUtil.canInteract(targetUser)) {
            throw new RuntimeException(UserAccountStatusUtil.targetUnavailableMessage(targetUser) + "，无法邀请协作");
        }

        String collaboratorRole = normalizeCollaboratorRole(role);

        Long count = playlistCollaboratorMapper.countActiveByPlaylistId(playlistId);
        if (count != null && count >= playlistCollaborationConfig.getMaxCollaborators()) {
            throw new RuntimeException("协作者数量已达上限");
        }

        PlaylistCollaborator existing = playlistCollaboratorMapper.selectAnyByPlaylistAndUser(playlistId, userId);
        if (existing != null && CommonConstants.NOT_DELETED.equals(existing.getDeleted())) {
            throw new RuntimeException("该用户已是协作者");
        }

        PlaylistCollaborator collaborator = new PlaylistCollaborator();
        collaborator.setPlaylistId(playlistId);
        collaborator.setUserId(userId);
        collaborator.setRole(collaboratorRole);
        collaborator.setCanAdd("editor".equals(collaboratorRole) ? 1 : 0);
        collaborator.setCanRemove(0);
        collaborator.setCanEdit(0);
        collaborator.setJoinedTime(LocalDateTime.now());
        collaborator.setInvitedBy(currentUserId);
        collaborator.setStatus("pending");
        collaborator.setDeleted(CommonConstants.NOT_DELETED);

        int changed;
        if (existing != null) {
            changed = playlistCollaboratorMapper.restoreInvitation(existing.getId(),
                    collaboratorRole,
                    collaborator.getCanAdd(),
                    collaborator.getCanRemove(),
                    collaborator.getCanEdit(),
                    currentUserId);
        } else {
            changed = playlistCollaboratorMapper.insertIgnore(collaborator);
        }
        if (changed <= 0) {
            throw new RuntimeException("邀请协作者失败，请稍后重试");
        }

        String eventType = ObjectUtils.isNotEmpty(existing) ? "restore_invitation" : "invite";
        String eventId = auditAndRecord(playlistId, currentUserId, userId, eventType, null,
                collaboratorSummary(existing), collaboratorSummary(collaborator), "邀请协作者");
        notificationService.sendSystemNotificationOnce(userId, "歌单协作邀请",
                "你收到歌单《" + playlist.getName() + "》的协作邀请", "/playlist/" + playlistId,
                "playlist-collaboration:" + eventId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void acceptInvitation(Long playlistId) {
        Long userId = getCurrentUserId();
        User currentUser = userMapper.selectById(userId);
        UserAccountStatusUtil.requireCanInteract(currentUser, "接受协作邀请");

        Playlist playlist = playlistMapper.selectByIdForUpdate(playlistId);
        if (ObjectUtils.isEmpty(playlist)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌单不存在");
        }
        User owner = userMapper.selectById(playlist.getUserId());
        if (!UserAccountStatusUtil.canInteract(owner)) {
            throw new RuntimeException(UserAccountStatusUtil.targetUnavailableMessage(owner) + "，无法接受协作邀请");
        }

        if (playlistCollaboratorMapper.selectAccepted(playlistId, userId) != null) {
            return;
        }

        int accepted = playlistCollaboratorMapper.acceptPending(playlistId, userId);
        if (accepted <= 0) {
            throw new RuntimeException("邀请不存在");
        }

        String eventId = auditAndRecord(playlistId, userId, playlist.getUserId(), "accept", null,
                "status=pending", "status=accepted", "接受协作邀请");
        notificationService.sendSystemNotificationOnce(playlist.getUserId(), "协作邀请已接受",
                "用户已接受歌单《" + playlist.getName() + "》的协作邀请", "/playlist/" + playlistId,
                "playlist-collaboration:" + eventId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void declineInvitation(Long playlistId) {
        Long userId = getCurrentUserId();
        UserAccountStatusUtil.requireCanInteract(userMapper.selectById(userId), "拒绝协作邀请");
        Playlist playlist = playlistMapper.selectByIdForUpdate(playlistId);
        if (ObjectUtils.isEmpty(playlist)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌单不存在");
        }
        if (playlistCollaboratorMapper.rejectPending(playlistId, userId) <= 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "待处理邀请不存在");
        }
        String eventId = auditAndRecord(playlistId, userId, playlist.getUserId(), "decline", null,
                "status=pending", "status=rejected", "拒绝协作邀请");
        notificationService.sendSystemNotificationOnce(playlist.getUserId(), "协作邀请已拒绝",
                "用户已拒绝歌单《" + playlist.getName() + "》的协作邀请", "/playlist/" + playlistId,
                "playlist-collaboration:" + eventId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeCollaborator(Long playlistId, Long userId) {
        Playlist playlist = playlistMapper.selectByIdForUpdate(playlistId);
        if (ObjectUtils.isEmpty(playlist)) {
            throw new RuntimeException("歌单不存在");
        }

        Long currentUserId = getCurrentUserId();
        UserAccountStatusUtil.requireCanInteract(userMapper.selectById(currentUserId), "移除协作者");
        if (!playlist.getUserId().equals(currentUserId)) {
            throw new RuntimeException("只有歌单创建者可以移除协作者");
        }

        PlaylistCollaborator removed = playlistCollaboratorMapper.selectAnyByPlaylistAndUser(playlistId, userId);
        if (playlistCollaboratorMapper.removeActiveNonOwner(playlistId, userId) <= 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "协作者不存在");
        }

        String eventId = auditAndRecord(playlistId, currentUserId, userId, "remove_collaborator", null,
                collaboratorSummary(removed), "status=removed", "移除协作者");
        notificationService.sendSystemNotificationOnce(userId, "歌单协作已结束",
                "你已被移出歌单《" + playlist.getName() + "》的协作", "/playlist/" + playlistId,
                "playlist-collaboration:" + eventId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void leaveCollaboration(Long playlistId) {
        Long userId = getCurrentUserId();
        UserAccountStatusUtil.requireCanInteract(userMapper.selectById(userId), "退出歌单协作");
        Playlist playlist = playlistMapper.selectByIdForUpdate(playlistId);
        if (playlist == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌单不存在");
        }
        if (Objects.equals(playlist.getUserId(), userId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "歌单所有者不能退出自己的歌单");
        }
        PlaylistCollaborator leaving = playlistCollaboratorMapper.selectAnyByPlaylistAndUser(playlistId, userId);
        if (playlistCollaboratorMapper.removeActiveNonOwner(playlistId, userId) <= 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "当前不是有效协作者");
        }
        String eventId = auditAndRecord(playlistId, userId, playlist.getUserId(), "leave", null,
                collaboratorSummary(leaving), "status=left", "协作者主动退出");
        notificationService.sendSystemNotificationOnce(playlist.getUserId(), "协作者已退出",
                "一名协作者已退出歌单《" + playlist.getName() + "》", "/playlist/" + playlistId,
                "playlist-collaboration:" + eventId);
    }

    @Override
    public List<CollaboratorVO> getCollaborators(Long playlistId, Long viewerId) {
        Playlist playlist = requireCollaborativePlaylistAccess(playlistId, viewerId, false);
        boolean showPermissions = Objects.equals(playlist.getUserId(), viewerId);
        LambdaQueryWrapper<PlaylistCollaborator> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PlaylistCollaborator::getPlaylistId, playlistId)
                .eq(PlaylistCollaborator::getStatus, "accepted")
                .eq(PlaylistCollaborator::getDeleted, CommonConstants.NOT_DELETED);

        List<PlaylistCollaborator> collaborators = playlistCollaboratorMapper.selectList(wrapper);

        if (collaborators.isEmpty()) {
            return new ArrayList<>();
        }

                      
        Set<Long> userIds = collaborators.stream()
                .map(PlaylistCollaborator::getUserId)
                .collect(Collectors.toSet());

        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.in(User::getId, userIds)
                .select(User::getId, User::getNickname, User::getAvatar);

        List<User> users = userMapper.selectList(userWrapper);
        Map<Long, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<CollaboratorVO> result = new ArrayList<>();
        for (PlaylistCollaborator collab : collaborators) {
            result.add(convertToVO(collab, userMap, showPermissions));
        }

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCollaboratorPermission(Long playlistId, Long userId, Boolean canAdd, Boolean canRemove, Boolean canEdit) {
        Playlist playlist = playlistMapper.selectByIdForUpdate(playlistId);
        if (ObjectUtils.isEmpty(playlist)) {
            throw new RuntimeException("歌单不存在");
        }

        Long currentUserId = getCurrentUserId();
        User currentUser = userMapper.selectById(currentUserId);
        UserAccountStatusUtil.requireCanInteract(currentUser, "修改协作者权限");

        if (!playlist.getUserId().equals(currentUserId)) {
            throw new RuntimeException("只有歌单创建者可以修改权限");
        }

        PlaylistCollaborator before = playlistCollaboratorMapper.selectAnyByPlaylistAndUser(playlistId, userId);
        int updated = playlistCollaboratorMapper.updatePermissions(playlistId,
                userId,
                Boolean.TRUE.equals(canAdd) ? 1 : 0,
                Boolean.TRUE.equals(canRemove) ? 1 : 0,
                Boolean.TRUE.equals(canEdit) ? 1 : 0);
        if (updated <= 0) {
            throw new RuntimeException("协作者不存在");
        }
        PlaylistCollaborator after = new PlaylistCollaborator();
        after.setRole(ObjectUtils.isEmpty(before) ? "editor" : before.getRole());
        after.setStatus(ObjectUtils.isEmpty(before) ? "accepted" : before.getStatus());
        after.setCanAdd(Boolean.TRUE.equals(canAdd) ? 1 : 0);
        after.setCanRemove(Boolean.TRUE.equals(canRemove) ? 1 : 0);
        after.setCanEdit(Boolean.TRUE.equals(canEdit) ? 1 : 0);
        String eventId = auditAndRecord(playlistId, currentUserId, userId, "permission_change", null,
                collaboratorSummary(before), collaboratorSummary(after), "修改协作者权限");
        notificationService.sendSystemNotificationOnce(userId, "歌单协作权限已变更",
                "你在歌单《" + playlist.getName() + "》中的协作权限已更新", "/playlist/" + playlistId,
                "playlist-collaboration:" + eventId);
    }

    @Override
    public PageResult<PlaylistOperationLogVO> getOperationLogs(
            Long playlistId, Integer page, Integer size, Long viewerId) {
        requireCollaborativePlaylistAccess(playlistId, viewerId, true);
        int actualPage = page != null && page > 0 ? page : 1;
        int actualSize = size != null && size > 0 ? Math.min(size, 100) : 20;
        LambdaQueryWrapper<PlaylistOperationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PlaylistOperationLog::getPlaylistId, playlistId)
                .orderByDesc(PlaylistOperationLog::getCreateTime);

        Page<PlaylistOperationLog> pageResult = playlistOperationLogMapper.selectPage(
                new Page<>(actualPage, actualSize), wrapper);

        PageResult<PlaylistOperationLogVO> result = new PageResult<>();
        result.setRecords(convertToVOList(pageResult.getRecords()));
        result.setTotal(pageResult.getTotal());
        result.setCurrent(pageResult.getCurrent());
        result.setSize(pageResult.getSize());
        result.setPages(pageResult.getPages());

        return result;
    }

    @Override
    public List<PlaylistVO> getMyCollaborativePlaylists() {
        Long userId = getCurrentUserId();

        LambdaQueryWrapper<PlaylistCollaborator> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PlaylistCollaborator::getUserId, userId)
                .eq(PlaylistCollaborator::getStatus, "accepted")
                .eq(PlaylistCollaborator::getDeleted, CommonConstants.NOT_DELETED);

        List<PlaylistCollaborator> collaborators = playlistCollaboratorMapper.selectList(wrapper);

        if (collaborators.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> playlistIds = collaborators.stream()
                .map(PlaylistCollaborator::getPlaylistId)
                .collect(Collectors.toList());

        LambdaQueryWrapper<Playlist> playlistWrapper = new LambdaQueryWrapper<>();
        playlistWrapper.in(Playlist::getId, playlistIds)
                .eq(Playlist::getDeleted, com.haoran.music.common.constant.CommonConstants.NOT_DELETED);

        List<Playlist> playlists = playlistMapper.selectList(playlistWrapper);
        Map<Long, Playlist> playlistMap = playlists.stream()
                .collect(Collectors.toMap(Playlist::getId, p -> p));

        List<PlaylistVO> result = new ArrayList<>();
        for (PlaylistCollaborator collab : collaborators) {
            Playlist playlist = playlistMap.get(collab.getPlaylistId());
            if (playlist != null) {
                PlaylistVO vo = new PlaylistVO();
                BeanUtils.copyProperties(playlist, vo);
                result.add(vo);
            }
        }

        return result;
    }

    @Override
    public List<Map<String, Object>> getPendingInvitations() {
        Long userId = getCurrentUserId();

        LambdaQueryWrapper<PlaylistCollaborator> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PlaylistCollaborator::getUserId, userId)
                .eq(PlaylistCollaborator::getStatus, "pending")
                .eq(PlaylistCollaborator::getDeleted, CommonConstants.NOT_DELETED);

        List<PlaylistCollaborator> collaborators = playlistCollaboratorMapper.selectList(wrapper);

        if (collaborators.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> playlistIds = collaborators.stream()
                .map(PlaylistCollaborator::getPlaylistId)
                .collect(Collectors.toList());

        LambdaQueryWrapper<Playlist> playlistWrapper = new LambdaQueryWrapper<>();
        playlistWrapper.in(Playlist::getId, playlistIds)
                .eq(Playlist::getDeleted, com.haoran.music.common.constant.CommonConstants.NOT_DELETED);

        List<Playlist> playlists = playlistMapper.selectList(playlistWrapper);
        Map<Long, Playlist> playlistMap = playlists.stream()
                .collect(Collectors.toMap(Playlist::getId, p -> p));

        List<Map<String, Object>> result = new ArrayList<>();
        for (PlaylistCollaborator collab : collaborators) {
            Playlist playlist = playlistMap.get(collab.getPlaylistId());
            if (playlist != null) {
                Map<String, Object> invite = new HashMap<>();
                invite.put("collaborator", collab);
                invite.put("playlist", playlist);
                result.add(invite);
            }
        }

        return result;
    }

       
                     
       
    private void insertOperationLog(Long playlistId, Long userId, String operationType,
                                    Long songId, String description) {
        PlaylistOperationLog log = new PlaylistOperationLog();
        log.setPlaylistId(playlistId);
        log.setUserId(userId);
        log.setOperationType(operationType);
        log.setSongId(songId);
        log.setDescription(description);
        log.setCreateTime(LocalDateTime.now());

        playlistOperationLogMapper.insert(log);
    }

    private String auditAndRecord(Long playlistId, Long actorId, Long targetUserId,
                                  String eventType, Long songId, String beforeSummary,
                                  String afterSummary, String description) {
        String eventId = playlistCollaborationAuditService.record(playlistId, actorId, targetUserId,
                eventType, beforeSummary, afterSummary, description);
        insertOperationLog(playlistId, actorId, eventType, songId, description);
        return eventId;
    }

    private String collaboratorSummary(PlaylistCollaborator collaborator) {
        if (ObjectUtils.isEmpty(collaborator)) {
            return "status=absent";
        }
        return "status=" + collaborator.getStatus()
                + ";role=" + collaborator.getRole()
                + ";canAdd=" + collaborator.getCanAdd()
                + ";canRemove=" + collaborator.getCanRemove()
                + ";canEdit=" + collaborator.getCanEdit();
    }

       
                      
       
    private CollaboratorVO convertToVO(
            PlaylistCollaborator collab, Map<Long, User> userMap, boolean showPermissions) {
        CollaboratorVO vo = new CollaboratorVO();
        vo.setUserId(collab.getUserId());
        vo.setRole(collab.getRole());
        vo.setRoleName(getRoleName(collab.getRole()));
        if (showPermissions) {
            vo.setCanAdd(Integer.valueOf(1).equals(collab.getCanAdd()));
            vo.setCanRemove(Integer.valueOf(1).equals(collab.getCanRemove()));
            vo.setCanEdit(Integer.valueOf(1).equals(collab.getCanEdit()));
        }
        vo.setJoinedTime(collab.getJoinedTime());

        User user = userMap.get(collab.getUserId());
        if (user != null) {
            vo.setNickname(user.getNickname());
            vo.setAvatar(user.getAvatar());
        }

        return vo;
    }

    private Playlist requireCollaborativePlaylistAccess(
            Long playlistId, Long viewerId, boolean membersOnly) {
        Playlist playlist = playlistMapper.selectById(playlistId);
        if (playlist == null
                || CommonConstants.DELETED.equals(playlist.getDeleted())
                || !CommonConstants.STATUS_NORMAL.equals(playlist.getStatus())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌单不存在或已下架");
        }
        if (Objects.equals(playlist.getUserId(), viewerId)) {
            return playlist;
        }
        PlaylistCollaborator collaborator = viewerId == null
                ? null : playlistCollaboratorMapper.selectAccepted(playlistId, viewerId);
        if (collaborator != null) {
            return playlist;
        }
        if (!membersOnly && Integer.valueOf(1).equals(playlist.getIsPublic())) {
            return playlist;
        }
        throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该协作歌单信息");
    }

       
           
       
    private enum CollaboratorRole {
        OWNER("owner", "所有者"),
        EDITOR("editor", "编辑者"),
        VIEWER("viewer", "查看者");

        private final String code;
        private final String name;

        CollaboratorRole(String code, String name) {
            this.code = code;
            this.name = name;
        }

        public static String getName(String code) {
            for (CollaboratorRole role : values()) {
                if (role.code.equals(code)) {
                    return role.name;
                }
            }
            return code;
        }
    }

       
             
       
    private String getRoleName(String role) {
        return CollaboratorRole.getName(role);
    }

       
                       
       
    private List<PlaylistOperationLogVO> convertToVOList(List<PlaylistOperationLog> logs) {
        if (logs.isEmpty()) {
            return new ArrayList<>();
        }

        Set<Long> userIds = logs.stream()
                .map(PlaylistOperationLog::getUserId)
                .collect(Collectors.toSet());

        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.in(User::getId, userIds)
                .select(User::getId, User::getNickname, User::getAvatar);

        List<User> users = userMapper.selectList(userWrapper);
        Map<Long, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<PlaylistOperationLogVO> result = new ArrayList<>();
        for (PlaylistOperationLog log : logs) {
            PlaylistOperationLogVO vo = new PlaylistOperationLogVO();
            BeanUtils.copyProperties(log, vo);
            vo.setOperationTypeName(getOperationTypeName(log.getOperationType()));

            User user = userMap.get(log.getUserId());
            if (user != null) {
                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("id", user.getId());
                userInfo.put("nickname", user.getNickname());
                userInfo.put("avatar", user.getAvatar());
                vo.setUser(userInfo);
            }

            result.add(vo);
        }
        return result;
    }

       
             
       
    private enum OperationType {
        ADD("add", "添加歌曲"),
        REMOVE("remove", "删除歌曲"),
        EDIT("edit", "编辑歌单"),
        INVITE("invite", "邀请协作者"),
        REMOVE_COLLAB("remove_collab", "移除协作者"),
        ACCEPT("accept", "接受邀请"),
        ENABLE("enable", "开启协作");

        private final String code;
        private final String name;

        OperationType(String code, String name) {
            this.code = code;
            this.name = name;
        }

        public static String getName(String code) {
            for (OperationType type : values()) {
                if (type.code.equals(code)) {
                    return type.name;
                }
            }
            return code;
        }
    }

       
               
       
    private String getOperationTypeName(String type) {
        return OperationType.getName(type);
    }

    @Override
    public String getCollaborativeRecommendReason(Long userId, Long songId, Long playlistId) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(songId) || ObjectUtils.isEmpty(playlistId)) {
            return "";
        }
        if (!UserAccountStatusUtil.canAppearInRecommendations(userId, userMapper::selectById)) {
            return "";
        }

        try {
            PlaylistCollaborator collaborator = playlistCollaboratorMapper.selectAccepted(playlistId, userId);

            if (collaborator == null) {
                return "";
            }

            Playlist playlist = requireCollaborativePlaylistAccess(playlistId, userId, true);
            Set<Long> allowedOwnerIds = playlist.getUserId() == null
                    ? Collections.emptySet()
                    : publicContentUserIds(Collections.singleton(playlist.getUserId()));
            if (!canExposePlaylist(playlist, allowedOwnerIds)) {
                return "";
            }
            PlaylistSong relation = playlistSongMapper.selectOne(new LambdaQueryWrapper<PlaylistSong>()
                    .eq(PlaylistSong::getPlaylistId, playlistId)
                    .eq(PlaylistSong::getSongId, songId)
                    .eq(PlaylistSong::getDeleted, CommonConstants.NOT_DELETED)
                    .last("LIMIT 1"));
            if (relation == null) {
                return "";
            }

            Song song = songMapper.selectById(songId);
            Set<Long> allowedUploaderIds = song == null || song.getUploaderId() == null
                    ? Collections.emptySet()
                    : publicContentUserIds(Collections.singleton(song.getUploaderId()));
            if (!canExposeSong(song, allowedUploaderIds)) {
                return "";
            }
            return buildCollaborativeRecommendReason(playlist, song, collaborator);

        } catch (Exception e) {
            log.error("获取协作歌单推荐理由失败: userId={}, songId={}, playlistId={}", userId, songId, playlistId);
            return "";
        }
    }

    @Override
    public Map<String, Object> getCollaborativePlaylistRecommend(Long userId, Integer limit) {
        Map<String, Object> result = new HashMap<>();
        int safeLimit = normalizeRecommendLimit(limit);

        if (ObjectUtils.isEmpty(userId)) {
            result.put("success", false);
            result.put("message", "用户未登录");
            return result;
        }

        if (!UserAccountStatusUtil.canAppearInRecommendations(userId, userMapper::selectById)) {
            result.put("success", true);
            result.put("data", new ArrayList<>());
            result.put("message", "当前账号暂不可参与协作推荐");
            return result;
        }

        try {
            LambdaQueryWrapper<PlaylistCollaborator> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(PlaylistCollaborator::getUserId, userId)
                    .eq(PlaylistCollaborator::getStatus, "accepted")
                    .eq(PlaylistCollaborator::getDeleted, CommonConstants.NOT_DELETED)
                    .orderByDesc(PlaylistCollaborator::getJoinedTime)
                    .last("LIMIT " + MAX_RECOMMEND_PLAYLISTS);

            List<PlaylistCollaborator> collaborators = playlistCollaboratorMapper.selectList(wrapper);

            if (collaborators.isEmpty()) {
                result.put("success", true);
                result.put("data", new ArrayList<>());
                result.put("message", "暂无协作歌单");
                return result;
            }

            List<Long> playlistIds = collaborators.stream()
                    .map(PlaylistCollaborator::getPlaylistId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            if (playlistIds.isEmpty()) {
                result.put("success", true);
                result.put("data", new ArrayList<>());
                result.put("message", "暂无协作歌单");
                return result;
            }

            LambdaQueryWrapper<com.haoran.music.entity.PlaylistSong> songWrapper = new LambdaQueryWrapper<>();
            songWrapper.in(com.haoran.music.entity.PlaylistSong::getPlaylistId, playlistIds)
                    .eq(com.haoran.music.entity.PlaylistSong::getDeleted,
                           CommonConstants.NOT_DELETED)
                    .orderByAsc(com.haoran.music.entity.PlaylistSong::getSortOrder)
                    .last("LIMIT " + (safeLimit * RECOMMEND_CANDIDATE_MULTIPLIER));

            List<com.haoran.music.entity.PlaylistSong> playlistSongs = playlistSongMapper.selectList(songWrapper);

            if (playlistSongs.isEmpty()) {
                result.put("success", true);
                result.put("data", new ArrayList<>());
                result.put("message", "协作歌单暂无歌曲");
                return result;
            }

                               
            Set<Long> songIds = playlistSongs.stream()
                    .map(com.haoran.music.entity.PlaylistSong::getSongId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (songIds.isEmpty()) {
                result.put("success", true);
                result.put("data", new ArrayList<>());
                result.put("message", "协作歌单暂无歌曲");
                return result;
            }

            List<Song> songs = songMapper.selectBatchIds(songIds);
            Set<Long> allowedUploaderIds = publicContentUserIds(songs.stream()
                    .map(Song::getUploaderId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet()));
            Map<Long, Song> songMap = songs.stream()
                    .filter(song -> canExposeSong(song, allowedUploaderIds))
                    .collect(Collectors.toMap(Song::getId, s -> s, (left, right) -> left));

            List<Playlist> playlists = playlistMapper.selectBatchIds(playlistIds);
            Set<Long> allowedOwnerIds = publicContentUserIds(playlists.stream()
                    .map(Playlist::getUserId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet()));
            Map<Long, Playlist> playlistMap = playlists.stream()
                    .filter(playlist -> canExposePlaylist(playlist, allowedOwnerIds))
                    .collect(Collectors.toMap(Playlist::getId, p -> p, (left, right) -> left));
            Map<Long, PlaylistCollaborator> collaboratorMap = collaborators.stream()
                    .filter(collaborator -> collaborator.getPlaylistId() != null)
                    .collect(Collectors.toMap(PlaylistCollaborator::getPlaylistId,
                            collaborator -> collaborator, (left, right) -> left));

            List<Map<String, Object>> recommendSongs = new ArrayList<>();
            Set<Long> processedSongIds = new HashSet<>();

            for (com.haoran.music.entity.PlaylistSong playlistSong : playlistSongs) {
                if (!processedSongIds.add(playlistSong.getSongId())) {
                    continue;
                }

                Playlist playlist = playlistMap.get(playlistSong.getPlaylistId());
                if (playlist == null) {
                    continue;
                }

                Song song = songMap.get(playlistSong.getSongId());
                if (song == null) {
                    continue;
                }

                Map<String, Object> songData = new HashMap<>();
                songData.put("songId", song.getId());
                songData.put("songName", song.getName());
                songData.put("artistNames", song.getArtistNames());
                songData.put("cover", song.getCover());
                songData.put("duration", song.getDuration());

                String reason = buildCollaborativeRecommendReason(
                        playlist, song, collaboratorMap.get(playlistSong.getPlaylistId()));
                songData.put("recommendReason", reason);

                songData.put("playlistId", playlist.getId());
                songData.put("playlistName", playlist.getName());

                recommendSongs.add(songData);

                if (recommendSongs.size() >= safeLimit) {
                    break;
                }
            }

            result.put("success", true);
            result.put("data", recommendSongs);
            result.put("total", recommendSongs.size());
            result.put("collaboratorCount", collaborators.size());

        } catch (Exception e) {
            log.error("event=playlist_collaboration_recommendation_failed userId={} errorType={}",
                    userId, e.getClass().getSimpleName());
            result.put("success", false);
            result.put("message", "获取协作歌单推荐失败");
        }

        return result;
    }

    @Override
    public List<PlaylistVO> getPublicCollaborativePlaylists(Long userId, Integer limit) {
        User profileOwner = userId == null ? null : userMapper.selectById(userId);
        if (!UserAccountStatusUtil.canExposePublicContent(profileOwner)) return Collections.emptyList();
        int safeLimit = Math.min(Math.max(limit == null ? 6 : limit, 1), 12);
        List<PlaylistCollaborator> collaborators = playlistCollaboratorMapper.selectList(
                new LambdaQueryWrapper<PlaylistCollaborator>()
                        .eq(PlaylistCollaborator::getUserId, userId)
                        .eq(PlaylistCollaborator::getStatus, "accepted")
                        .eq(PlaylistCollaborator::getDeleted, CommonConstants.NOT_DELETED)
                        .orderByDesc(PlaylistCollaborator::getJoinedTime)
                        .last("LIMIT " + safeLimit));
        if (collaborators.isEmpty()) return Collections.emptyList();
        List<Long> playlistIds = collaborators.stream().map(PlaylistCollaborator::getPlaylistId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (playlistIds.isEmpty()) return Collections.emptyList();
        Map<Long, Playlist> playlists = playlistMapper.selectList(new LambdaQueryWrapper<Playlist>()
                        .in(Playlist::getId, playlistIds)
                        .eq(Playlist::getStatus, CommonConstants.STATUS_NORMAL)
                        .eq(Playlist::getDeleted, CommonConstants.NOT_DELETED)
                        .eq(Playlist::getIsPublic, CommonConstants.YES))
                .stream().collect(Collectors.toMap(Playlist::getId, value -> value));
        List<PlaylistVO> result = new ArrayList<>();
        for (Long playlistId : playlistIds) {
            Playlist playlist = playlists.get(playlistId);
            if (playlist == null) continue;
            PlaylistVO vo = new PlaylistVO();
            BeanUtils.copyProperties(playlist, vo);
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getAuditEvents(Long playlistId, Integer limit, Long viewerId) {
        requireCollaborativePlaylistAccess(playlistId, viewerId, true);
        int safeLimit = ObjectUtils.isEmpty(limit) ? 20 : Math.max(1, Math.min(limit, 100));
        return playlistCollaborationAuditService.listRecent(playlistId, safeLimit);
    }

    private Set<Long> publicContentUserIds(Set<Long> userIds) {
        return UserAccountStatusUtil.filterPublicContentUserIds(userIds, ids -> userMapper.selectBatchIds(ids));
    }

    private boolean canExposeSong(Song song, Set<Long> allowedUploaderIds) {
        return song != null
                && CommonConstants.NOT_DELETED.equals(song.getDeleted())
                && CommonConstants.STATUS_NORMAL.equals(song.getStatus())
                && (song.getUploaderId() == null || allowedUploaderIds.contains(song.getUploaderId()));
    }

    private boolean canExposePlaylist(Playlist playlist, Set<Long> allowedOwnerIds) {
        return playlist != null
                && CommonConstants.NOT_DELETED.equals(playlist.getDeleted())
                && CommonConstants.STATUS_NORMAL.equals(playlist.getStatus())
                && (playlist.getUserId() == null || allowedOwnerIds.contains(playlist.getUserId()));
    }

       
                                          
      
                           
                     
                                      
                   
  
    private String buildCollaborativeRecommendReason(
            Playlist playlist, Song song, PlaylistCollaborator collaborator) {
        if (playlist == null || song == null || collaborator == null) {
            return "";
        }
        StringBuilder reason = new StringBuilder("来自协作歌单《")
                .append(playlist.getName()).append("》");
        if ("owner".equals(collaborator.getRole())) {
            reason.append("（你创建的）");
        } else {
            reason.append("（你参与的）");
        }
        if (song.getPlayCount() != null && song.getPlayCount() > 1000) {
            reason.append("，近期站内播放较多");
        }
        return reason.toString();
    }

       
                   
      
                         
                           
  
    private int normalizeRecommendLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_RECOMMEND_LIMIT;
        }
        return Math.min(limit, MAX_RECOMMEND_LIMIT);
    }

    private String normalizeCollaboratorRole(String role) {
        if ("viewer".equals(role)) {
            return "viewer";
        }
        return "editor";
    }

    private Long getCurrentUserId() {
        return UserContext.getCurrentUserId();
    }
}
