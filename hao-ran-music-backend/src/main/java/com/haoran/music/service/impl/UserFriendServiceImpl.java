   
                      
                        
   

package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.enums.VipLevel;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.User;
import com.haoran.music.entity.UserFriend;
import com.haoran.music.entity.UserFollow;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.mapper.UserFriendMapper;
import com.haoran.music.mapper.UserFollowMapper;
import com.haoran.music.service.UserFriendService;
import com.haoran.music.service.UserBlacklistService;
import com.haoran.music.service.UserVipService;
import com.haoran.music.vo.friend.FriendGroupVO;
import com.haoran.music.vo.friend.FriendRequestVO;
import com.haoran.music.vo.friend.FriendVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

   
                       
   
@Slf4j
@Service
public class UserFriendServiceImpl implements UserFriendService {

    @Resource
    private UserFriendMapper userFriendMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private UserFollowMapper userFollowMapper;

    @Resource
    private UserBlacklistService userBlacklistService;

    @Resource
    private UserVipService userVipService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean sendFriendRequest(Long userId, Long targetUserId) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(targetUserId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }

        if (userId.equals(targetUserId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不能添加自己为好友");
        }

        User user = userMapper.selectById(userId);
        User targetUser = userMapper.selectById(targetUserId);
        if (!UserAccountStatusUtil.canInteract(user)) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    UserAccountStatusUtil.currentUnavailableMessage(user) + "，无法添加好友");
        }
        if (!UserAccountStatusUtil.canInteract(targetUser)) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    UserAccountStatusUtil.targetUnavailableMessage(targetUser) + "，无法添加好友");
        }

                    
        if (isFriend(userId, targetUserId)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "已经是好友关系");
        }

        if (isBlacklistedEitherWay(userId, targetUserId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "黑名单关系无法添加好友");
        }

        if (!isMutualFollow(userId, targetUserId)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "需要互相关注后才能添加好友");
        }

        if (hasPendingFriendRequest(userId, targetUserId)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "已有待处理的好友请求");
        }
                                             
        LocalDateTime now = LocalDateTime.now();
        UserFriend request = new UserFriend();
        request.setUserId(userId);
        request.setFriendId(targetUserId);
        request.setStatus("pending");
        request.setFriendSince(now);

        userFriendMapper.insert(request);
        log.info("发送好友请求: from={}, to={}", userId, targetUserId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean handleFriendRequest(Long requestId, Long userId, Boolean approved) {
        UserFriend request = userFriendMapper.selectById(requestId);
        if (request == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "好友请求不存在");
        }

                           
        if (!request.getFriendId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权处理此请求");
        }

        if (!"pending".equals(request.getStatus())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "该请求已被处理");
        }

        if (isMirrorPendingRecord(request)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权处理此请求");
        }

        if (approved) {
            User requester = userMapper.selectById(request.getUserId());
            User receiver = userMapper.selectById(request.getFriendId());
            if (!UserAccountStatusUtil.canInteract(receiver)) {
                throw new BusinessException(ResultCode.FORBIDDEN,
                        UserAccountStatusUtil.currentUnavailableMessage(receiver) + "，无法处理好友请求");
            }
            if (!UserAccountStatusUtil.canInteract(requester)) {
                throw new BusinessException(ResultCode.FORBIDDEN,
                        UserAccountStatusUtil.targetUnavailableMessage(requester) + "，无法成为好友");
            }
            if (isBlacklistedEitherWay(request.getUserId(), request.getFriendId())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "黑名单关系无法成为好友");
            }
            if (!isMutualFollow(request.getUserId(), request.getFriendId())) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "需要保持互相关注后才能成为好友");
            }

                                                              
            LocalDateTime now = LocalDateTime.now();
            request.setStatus("accepted");
            if (request.getFriendSince() == null) {
                request.setFriendSince(now);
            }
            userFriendMapper.updateById(request);

            List<UserFriend> reverseRecords = userFriendMapper.selectList(
                new LambdaQueryWrapper<UserFriend>()
                    .eq(UserFriend::getUserId, request.getFriendId())
                    .eq(UserFriend::getFriendId, request.getUserId())
            );

            UserFriend reverseRecord = reverseRecords.isEmpty() ? null : reverseRecords.get(0);
            if (reverseRecord == null) {
                reverseRecord = new UserFriend();
                reverseRecord.setUserId(request.getFriendId());
                reverseRecord.setFriendId(request.getUserId());
                reverseRecord.setStatus("accepted");
                reverseRecord.setFriendSince(now);
                userFriendMapper.insert(reverseRecord);
            } else {
                reverseRecord.setStatus("accepted");
                if (reverseRecord.getFriendSince() == null) {
                    reverseRecord.setFriendSince(now);
                }
                userFriendMapper.updateById(reverseRecord);
            }

            log.info("接受好友请求: user={}, friend={}", userId, request.getUserId());
        } else {
                                               
            userFriendMapper.deleteById(requestId);
            userFriendMapper.delete(
                new LambdaQueryWrapper<UserFriend>()
                    .eq(UserFriend::getUserId, request.getFriendId())
                    .eq(UserFriend::getFriendId, request.getUserId())
                    .eq(UserFriend::getStatus, "pending")
            );
            log.info("拒绝好友请求: user={}, friend={}", userId, request.getUserId());
        }

        return true;
    }

    @Override
    public List<FriendVO> getFriendList(Long userId, Long groupId) {
        if (ObjectUtils.isEmpty(userId)) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<UserFriend> wrapper = new LambdaQueryWrapper<UserFriend>()
            .eq(UserFriend::getUserId, userId)
            .eq(UserFriend::getStatus, "accepted");

        if (groupId != null) {
            wrapper.eq(UserFriend::getFriendGroup, String.valueOf(groupId));
        }

        List<UserFriend> friendRelations = userFriendMapper.selectList(wrapper);

        if (ObjectUtils.isEmpty(friendRelations)) {
            return new ArrayList<>();
        }

        return buildFriendVoList(userId, friendRelations, false, relation -> true);
    }

    @Override
    public List<FriendRequestVO> getFriendRequests(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return new ArrayList<>();
        }

                         
        List<UserFriend> requests = userFriendMapper.selectList(
            new LambdaQueryWrapper<UserFriend>()
                .eq(UserFriend::getFriendId, userId)
                .eq(UserFriend::getStatus, "pending")
                .orderByDesc(UserFriend::getCreateTime)
        );

        if (ObjectUtils.isEmpty(requests)) {
            return new ArrayList<>();
        }

        requests = requests.stream()
                .filter(request -> !isMirrorPendingRecord(request))
                .collect(Collectors.toList());

        if (ObjectUtils.isEmpty(requests)) {
            return new ArrayList<>();
        }

                    
        List<Long> fromUserIds = requests.stream()
                .map(UserFriend::getUserId)
                .distinct()
                .collect(Collectors.toList());

        List<User> fromUsers = userMapper.selectList(
            new LambdaQueryWrapper<User>()
                .in(User::getId, fromUserIds)
                .eq(User::getDeleted, CommonConstants.NOT_DELETED)
        );

        java.util.Map<Long, User> userMap = fromUsers.stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return requests.stream()
                .map(request -> convertToFriendRequestVO(request, userMap.get(request.getUserId())))
                .filter(vo -> vo != null)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unfriend(Long userId, Long friendId) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(friendId)) {
            return false;
        }

                   
        userFriendMapper.delete(
            new LambdaQueryWrapper<UserFriend>()
                .eq(UserFriend::getUserId, userId)
                .eq(UserFriend::getFriendId, friendId)
                .eq(UserFriend::getStatus, "accepted")
        );

        userFriendMapper.delete(
            new LambdaQueryWrapper<UserFriend>()
                .eq(UserFriend::getUserId, friendId)
                .eq(UserFriend::getFriendId, userId)
        );

        log.info("解除好友关系: user={}, friend={}", userId, friendId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean setSpecialMark(Long userId, Long friendId, String specialMark) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(friendId)) {
            return false;
        }

                  
        if (!isFriend(userId, friendId)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "只能对好友设置特别关注");
        }
        if (specialMark != null && !"special".equals(specialMark) && !"close".equals(specialMark)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "特别关注标记不合法");
        }

        UserFriend updateEntity = new UserFriend();
        updateEntity.setSpecialMark(specialMark);
        int updated = userFriendMapper.update(updateEntity,
            new LambdaQueryWrapper<UserFriend>()
                .eq(UserFriend::getUserId, userId)
                .eq(UserFriend::getFriendId, friendId)
                .eq(UserFriend::getStatus, "accepted")
        );
        if (updated != 1) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "好友关系已变更，请刷新后重试");
        }

        log.info("设置特别关注: user={}, friend={}, mark={}", userId, friendId, specialMark);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean setFriendGroup(Long userId, Long friendId, Long groupId) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(friendId)) {
            return false;
        }

                     
        UserFriend friend = userFriendMapper.selectOne(
            new LambdaQueryWrapper<UserFriend>()
                .eq(UserFriend::getUserId, userId)
                .eq(UserFriend::getFriendId, friendId)
                .eq(UserFriend::getStatus, "accepted")
        );

        if (friend == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "好友关系不存在");
        }

        String groupName = null;
        if (groupId != null) {
            UserFriend groupRecord = userFriendMapper.selectOne(
                    new LambdaQueryWrapper<UserFriend>()
                            .eq(UserFriend::getId, groupId)
                            .eq(UserFriend::getUserId, userId)
                            .eq(UserFriend::getStatus, "placeholder"));
            if (groupRecord == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "好友分组不存在或不属于当前用户");
            }
            groupName = groupRecord.getFriendGroup();
        }

                                           
        UserFriend updateEntity = new UserFriend();
        updateEntity.setFriendGroup(groupName);
        int updated = userFriendMapper.update(updateEntity,
            new LambdaQueryWrapper<UserFriend>()
                .eq(UserFriend::getUserId, userId)
                .eq(UserFriend::getFriendId, friendId)
                .eq(UserFriend::getStatus, "accepted")
        );
        if (updated != 1) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "好友关系已变更，请刷新后重试");
        }

        return true;
    }

    @Override
    public Long createFriendGroup(Long userId, String groupName) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(groupName)) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }

        String normalizedGroupName = groupName.trim();
        if (normalizedGroupName.isEmpty() || normalizedGroupName.length() > 30
                || normalizedGroupName.chars().anyMatch(Character::isISOControl)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "分组名称不能超过30字且不能包含控制字符");
        }
        Long groupCount = userFriendMapper.selectCount(new LambdaQueryWrapper<UserFriend>()
                .eq(UserFriend::getUserId, userId)
                .eq(UserFriend::getStatus, "placeholder"));
        if (groupCount != null && groupCount >= 20) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "好友分组最多20个");
        }

                    
        Long existingCount = userFriendMapper.selectCount(
            new LambdaQueryWrapper<UserFriend>()
                .eq(UserFriend::getUserId, userId)
                .eq(UserFriend::getFriendGroup, normalizedGroupName)
                .eq(UserFriend::getStatus, "placeholder")
        );

        if (existingCount != null && existingCount > 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "分组已存在");
        }

                   
        UserFriend friend = new UserFriend();
        friend.setUserId(userId);
        friend.setFriendId(0L);        
        friend.setFriendGroup(normalizedGroupName);
        friend.setStatus("placeholder");
        friend.setFriendSince(LocalDateTime.now());

        userFriendMapper.insert(friend);

        return friend.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean setFriendRemark(Long userId, Long friendId, String remark) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(friendId)) {
            return false;
        }

        if (!isFriend(userId, friendId)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "只能修改当前好友的备注");
        }
        String normalizedRemark = remark == null ? null : remark.trim();
        if (normalizedRemark != null
                && (normalizedRemark.length() > 50
                || normalizedRemark.chars().anyMatch(Character::isISOControl))) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "好友备注不能超过50字且不能包含控制字符");
        }

        UserFriend updateEntity = new UserFriend();
        updateEntity.setRemark(normalizedRemark);
        int updated = userFriendMapper.update(updateEntity,
            new LambdaQueryWrapper<UserFriend>()
                .eq(UserFriend::getUserId, userId)
                .eq(UserFriend::getFriendId, friendId)
                .eq(UserFriend::getStatus, "accepted")
        );
        if (updated != 1) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "好友关系已变更，请刷新后重试");
        }

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean blockFriend(Long userId, Long friendId, Boolean blocked) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(friendId) || blocked == null) {
            return false;
        }
        if (blocked) {
            return Boolean.TRUE.equals(userBlacklistService.addToBlacklist(userId, friendId, "好友页拉黑"));
        }
                                       
        return Boolean.TRUE.equals(userBlacklistService.removeFromBlacklist(userId, friendId));
    }

    @Override
    public boolean isFriend(Long userId, Long targetUserId) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(targetUserId)) {
            return false;
        }

        if (isBlacklistedEitherWay(userId, targetUserId)) {
            return false;
        }

        return hasAcceptedFriend(userId, targetUserId) && hasAcceptedFriend(targetUserId, userId);
    }

    @Override
    public boolean isSpecialFollow(Long userId, Long friendId) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(friendId)) {
            return false;
        }
        if (!isFriend(userId, friendId)) {
            return false;
        }

        Long count = userFriendMapper.selectCount(
            new LambdaQueryWrapper<UserFriend>()
                .eq(UserFriend::getUserId, userId)
                .eq(UserFriend::getFriendId, friendId)
                .eq(UserFriend::getStatus, "accepted")
                .eq(UserFriend::getSpecialMark, "special")
        );

        return count != null && count > 0;
    }

    @Override
    public List<FriendVO> getSpecialFollows(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return new ArrayList<>();
        }

        List<UserFriend> specialFriends = userFriendMapper.selectList(
            new LambdaQueryWrapper<UserFriend>()
                .eq(UserFriend::getUserId, userId)
                .eq(UserFriend::getStatus, "accepted")
                .eq(UserFriend::getSpecialMark, "special")
        );

        return buildFriendVoList(userId, specialFriends, false, relation -> true);
    }

    @Override
    public List<FriendVO> getMutualFriendsNotSpecial(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return new ArrayList<>();
        }

                 
        List<UserFriend> friends = userFriendMapper.selectList(
            new LambdaQueryWrapper<UserFriend>()
                .eq(UserFriend::getUserId, userId)
                .eq(UserFriend::getStatus, "accepted")
        );

        return buildFriendVoList(userId, friends, true,
                relation -> !"special".equals(relation.getSpecialMark()));
    }

    @Override
    public List<FriendGroupVO> getFriendGroups(Long userId) {
                                     
        List<UserFriend> groupRecords = userFriendMapper.selectList(
            new LambdaQueryWrapper<UserFriend>()
                .select(UserFriend::getId, UserFriend::getFriendGroup, UserFriend::getCreateTime)
                .eq(UserFriend::getUserId, userId)
                .eq(UserFriend::getStatus, "placeholder")
                .isNotNull(UserFriend::getFriendGroup)
        );

        List<FriendGroupVO> result = new ArrayList<>();
        for (UserFriend record : groupRecords) {
            FriendGroupVO vo = new FriendGroupVO();
            vo.setId(record.getId());
            vo.setUserId(userId);
            vo.setGroupName(record.getFriendGroup());
            vo.setCreateTime(record.getCreateTime() != null ? record.getCreateTime().toString() : null);

                          
            Long count = userFriendMapper.selectCount(
                new LambdaQueryWrapper<UserFriend>()
                    .eq(UserFriend::getUserId, userId)
                    .eq(UserFriend::getFriendGroup, record.getFriendGroup())
                    .eq(UserFriend::getStatus, "accepted")
            );
            vo.setCount(count != null ? count.intValue() : 0);
            result.add(vo);
        }

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteFriendGroup(Long userId, Long groupId) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(groupId)) {
            return false;
        }

                 
        UserFriend groupRecord = userFriendMapper.selectOne(
            new LambdaQueryWrapper<UserFriend>()
                .eq(UserFriend::getUserId, userId)
                .eq(UserFriend::getId, groupId)
                .eq(UserFriend::getStatus, "placeholder")
        );

        if (groupRecord == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "分组不存在");
        }

        String groupName = groupRecord.getFriendGroup();

                   
        userFriendMapper.deleteById(groupId);

                        
        if (ObjectUtils.isNotEmpty(groupName)) {
            UserFriend updateEntity = new UserFriend();
            updateEntity.setFriendGroup(null);
            userFriendMapper.update(updateEntity,
                new LambdaQueryWrapper<UserFriend>()
                    .eq(UserFriend::getUserId, userId)
                    .eq(UserFriend::getFriendGroup, groupName)
            );
        }

        log.info("删除好友分组: userId={}, groupId={}", userId, groupId);

        return true;
    }


       
                                  
       
    private List<FriendVO> buildFriendVoList(Long userId,
                                             List<UserFriend> relations,
                                             boolean requireMutualFollow,
                                             Predicate<UserFriend> relationFilter) {
        if (ObjectUtils.isEmpty(relations)) {
            return new ArrayList<>();
        }

        List<Long> friendIds = extractFriendIds(relations);
        if (ObjectUtils.isEmpty(friendIds)) {
            return new ArrayList<>();
        }

        FriendRelationSnapshot snapshot = loadFriendRelationSnapshot(userId, friendIds);
        List<UserFriend> visibleRelations = relations.stream()
                .filter(relation -> relation != null && relation.getFriendId() != null)
                .filter(relation -> !snapshot.isBlocked(relation.getFriendId()))
                .filter(relation -> snapshot.isReverseAccepted(relation.getFriendId()))
                .filter(relationFilter)
                .filter(relation -> !requireMutualFollow || snapshot.isMutualFollow(relation.getFriendId()))
                .collect(Collectors.toList());

        if (ObjectUtils.isEmpty(visibleRelations)) {
            return new ArrayList<>();
        }

        Map<Long, User> userMap = loadFriendUsers(extractFriendIds(visibleRelations));
        Map<Long, VipLevel> vipLevels = userVipService.getActiveVipLevels(userMap.keySet());
        return visibleRelations.stream()
                .map(relation -> convertToFriendVO(
                        relation,
                        userMap.get(relation.getFriendId()),
                        snapshot.isMutualFollow(relation.getFriendId()),
                        vipLevels.getOrDefault(relation.getFriendId(), VipLevel.FREE)))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private FriendRelationSnapshot loadFriendRelationSnapshot(Long userId, Collection<Long> friendIds) {
        if (ObjectUtils.isEmpty(friendIds)) {
            return FriendRelationSnapshot.empty();
        }

        Set<Long> blockedFriendIds = toIdSet(userBlacklistService.getBlacklistedUserIds(userId));
        blockedFriendIds.addAll(toIdSet(userBlacklistService.getBlacklisterUserIds(userId, friendIds)));

        Set<Long> reverseAcceptedFriendIds = toIdSet(userFriendMapper.selectAcceptedFriendIds(userId, friendIds));
        Set<Long> forwardFollowIds = toIdSet(userFollowMapper.selectFolloweeIds(userId, friendIds));
        Set<Long> reverseFollowIds = toIdSet(userFollowMapper.selectMutualFollowerIds(userId, friendIds));
        forwardFollowIds.retainAll(reverseFollowIds);

        return new FriendRelationSnapshot(blockedFriendIds, reverseAcceptedFriendIds, forwardFollowIds);
    }

    private Map<Long, User> loadFriendUsers(Collection<Long> friendIds) {
        if (ObjectUtils.isEmpty(friendIds)) {
            return Collections.emptyMap();
        }

        return userMapper.selectList(
                new LambdaQueryWrapper<User>()
                    .in(User::getId, friendIds)
                    .eq(User::getDeleted, CommonConstants.NOT_DELETED)
            ).stream()
                .collect(Collectors.toMap(User::getId, user -> user, (left, right) -> left));
    }

    private List<Long> extractFriendIds(List<UserFriend> relations) {
        return relations.stream()
                .filter(Objects::nonNull)
                .map(UserFriend::getFriendId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    private Set<Long> toIdSet(Collection<Long> ids) {
        if (ObjectUtils.isEmpty(ids)) {
            return new HashSet<>();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

       
               
       
    private FriendVO convertToFriendVO(UserFriend relation, User user, boolean isMutual, VipLevel vipLevel) {
        if (user == null) {
            return null;
        }

        FriendVO vo = new FriendVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
                                    
        vo.setSignature(user.getIntroduction());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setUserType(user.getUserType());
        vo.setIsBanned(user.getIsBanned());
        vo.setIsCreator(user.getIsCreator() != null && user.getIsCreator() == 1);
        vo.setCreatorStatus(user.getCreatorStatus());
        vo.setCreatorType(user.getCreatorType());

        VipLevel effectiveVipLevel = vipLevel == null ? VipLevel.FREE : vipLevel;
        vo.setIsVip(effectiveVipLevel.isPaidVip());
        vo.setVipLevel(effectiveVipLevel.getCode());

                
        vo.setCreditScore(user.getCreditScore());

                     
        if (relation != null) {
            vo.setFriendGroup(relation.getFriendGroup());
            vo.setRemark(relation.getRemark());
            vo.setSpecialMark(relation.getSpecialMark());
            vo.setFriendSince(relation.getFriendSince());
        }

                    
        vo.setRelationType("friend");
        vo.setIsMutual(relation != null && isMutual);

                    
        if (user.getFansCount() != null) {
            vo.setFansCount(user.getFansCount().longValue());
        }
        if (user.getFollowingCount() != null) {
            vo.setFollowingCount(user.getFollowingCount().longValue());
        }

        return vo;
    }

    private static final class FriendRelationSnapshot {
        private final Set<Long> blockedFriendIds;
        private final Set<Long> reverseAcceptedFriendIds;
        private final Set<Long> mutualFollowFriendIds;

        private FriendRelationSnapshot(Set<Long> blockedFriendIds,
                                       Set<Long> reverseAcceptedFriendIds,
                                       Set<Long> mutualFollowFriendIds) {
            this.blockedFriendIds = blockedFriendIds;
            this.reverseAcceptedFriendIds = reverseAcceptedFriendIds;
            this.mutualFollowFriendIds = mutualFollowFriendIds;
        }

        private static FriendRelationSnapshot empty() {
            return new FriendRelationSnapshot(
                    Collections.emptySet(),
                    Collections.emptySet(),
                    Collections.emptySet());
        }

        private boolean isBlocked(Long friendId) {
            return blockedFriendIds.contains(friendId);
        }

        private boolean isReverseAccepted(Long friendId) {
            return reverseAcceptedFriendIds.contains(friendId);
        }

        private boolean isMutualFollow(Long friendId) {
            return mutualFollowFriendIds.contains(friendId);
        }
    }

       
                
                       
                              
                     
       
    private FriendRequestVO convertToFriendRequestVO(UserFriend request, User fromUser) {
        if (request == null) {
            return null;
        }

        FriendRequestVO vo = new FriendRequestVO();
        vo.setId(request.getId());
        vo.setStatus(request.getStatus());
        vo.setCreateTime(request.getCreateTime());
        vo.setUpdateTime(request.getUpdateTime());

        if (fromUser != null) {
            vo.setFromUserId(fromUser.getId());
            vo.setFromUsername(fromUser.getUsername());
            vo.setFromNickname(fromUser.getNickname());
            vo.setFromAvatar(fromUser.getAvatar());
                                        
            vo.setFromSignature(fromUser.getIntroduction());
            vo.setFromStatus(fromUser.getStatus());
            vo.setFromUserType(fromUser.getUserType());
            vo.setFromIsBanned(fromUser.getIsBanned());
        }

        vo.setToUserId(request.getFriendId());

        return vo;
    }

    private boolean hasAcceptedFriend(Long userId, Long friendId) {
        Long count = userFriendMapper.selectCount(
            new LambdaQueryWrapper<UserFriend>()
                .eq(UserFriend::getUserId, userId)
                .eq(UserFriend::getFriendId, friendId)
                .eq(UserFriend::getStatus, "accepted")
        );
        return count != null && count > 0;
    }

    @Override
    public boolean hasPendingFriendRequest(Long userId, Long targetUserId) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(targetUserId)) {
            return false;
        }
        Long count = userFriendMapper.selectCount(
            new LambdaQueryWrapper<UserFriend>()
                .eq(UserFriend::getStatus, "pending")
                .and(w -> w.eq(UserFriend::getUserId, userId)
                        .eq(UserFriend::getFriendId, targetUserId)
                        .or()
                        .eq(UserFriend::getUserId, targetUserId)
                        .eq(UserFriend::getFriendId, userId))
        );
        return count != null && count > 0;
    }

    private boolean isMirrorPendingRecord(UserFriend request) {
        if (request == null || request.getId() == null || !"pending".equals(request.getStatus())) {
            return false;
        }

        List<UserFriend> oppositeRecords = userFriendMapper.selectList(
            new LambdaQueryWrapper<UserFriend>()
                .eq(UserFriend::getUserId, request.getFriendId())
                .eq(UserFriend::getFriendId, request.getUserId())
                .eq(UserFriend::getStatus, "pending")
        );

        if (oppositeRecords.isEmpty()) {
            return false;
        }

        Long oldestOppositeId = oppositeRecords.stream()
                .map(UserFriend::getId)
                .filter(id -> id != null)
                .min(Long::compareTo)
                .orElse(null);

        return oldestOppositeId != null && request.getId() > oldestOppositeId;
    }

    private boolean isBlacklistedEitherWay(Long userId, Long targetUserId) {
        return Boolean.TRUE.equals(userBlacklistService.isBlacklisted(userId, targetUserId))
                || Boolean.TRUE.equals(userBlacklistService.isBlacklisted(targetUserId, userId));
    }

    private boolean isMutualFollow(Long userId, Long targetUserId) {
        return hasFollow(userId, targetUserId) && hasFollow(targetUserId, userId);
    }

    private boolean hasFollow(Long followerId, Long followeeId) {
        Long count = userFollowMapper.selectCount(
            new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, followerId)
                .eq(UserFollow::getFolloweeId, followeeId)
                .eq(UserFollow::getDeleted, CommonConstants.NOT_DELETED)
        );
        return count != null && count > 0;
    }
}
