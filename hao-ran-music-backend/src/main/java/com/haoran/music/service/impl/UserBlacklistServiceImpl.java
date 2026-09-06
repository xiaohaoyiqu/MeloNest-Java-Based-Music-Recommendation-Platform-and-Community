




package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.entity.User;
import com.haoran.music.entity.UserBlacklist;
import com.haoran.music.entity.UserFollow;
import com.haoran.music.entity.UserFriend;
import com.haoran.music.mapper.UserBlacklistMapper;
import com.haoran.music.mapper.UserFollowMapper;
import com.haoran.music.mapper.UserFriendMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.NotificationService;
import com.haoran.music.service.UserBlacklistService;
import com.haoran.music.vo.blacklist.BlacklistUserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;




@Slf4j
@Service
public class UserBlacklistServiceImpl extends ServiceImpl<UserBlacklistMapper, UserBlacklist>
        implements UserBlacklistService {




    private static final int MAX_BLACKLIST = 200;

    private final UserBlacklistMapper userBlacklistMapper;
    private final UserMapper userMapper;
    private final UserFollowMapper userFollowMapper;
    private final UserFriendMapper userFriendMapper;
    private final NotificationService notificationService;

    public UserBlacklistServiceImpl(
            UserBlacklistMapper userBlacklistMapper,
            UserMapper userMapper,
            UserFollowMapper userFollowMapper,
            UserFriendMapper userFriendMapper,
            NotificationService notificationService) {
        this.userBlacklistMapper = userBlacklistMapper;
        this.userMapper = userMapper;
        this.userFollowMapper = userFollowMapper;
        this.userFriendMapper = userFriendMapper;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean addToBlacklist(Long userId, Long blacklistedUserId, String reason) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(blacklistedUserId)) {
            log.warn("添加黑名单失败: 参数为空");
            return false;
        }


        if (userId.equals(blacklistedUserId)) {
            log.warn("不能将自己加入黑名单: userId={}", userId);
            return false;
        }


        LambdaQueryWrapper<UserBlacklist> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(UserBlacklist::getUserId, userId);
        Long currentCount = userBlacklistMapper.selectCount(countWrapper);
        if (currentCount != null && currentCount >= MAX_BLACKLIST) {
            log.warn("黑名单已达到上限: userId={}, count={}, max={}", userId, currentCount, MAX_BLACKLIST);
            return false;
        }


        LambdaQueryWrapper<UserBlacklist> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserBlacklist::getUserId, userId)
                .eq(UserBlacklist::getBlacklistedUserId, blacklistedUserId);
        Long count = userBlacklistMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            cleanupRelationshipsForBlacklist(userId, blacklistedUserId);
            log.warn("用户已在黑名单中: userId={}, blacklistedUserId={}", userId, blacklistedUserId);
            return true;
        }


        User blacklistedUser = userMapper.selectById(blacklistedUserId);
        if (ObjectUtils.isEmpty(blacklistedUser)) {
            log.warn("被拉黑用户不存在: blacklistedUserId={}", blacklistedUserId);
            return false;
        }


        UserBlacklist blacklist = new UserBlacklist();
        blacklist.setUserId(userId);
        blacklist.setBlacklistedUserId(blacklistedUserId);
        blacklist.setReason(reason);
        blacklist.setIsMutual(0);
        blacklist.setCreateTime(LocalDateTime.now());

        int result = userBlacklistMapper.insert(blacklist);


        updateMutualStatus(userId, blacklistedUserId);


        if (result > 0) {
            cleanupRelationshipsForBlacklist(userId, blacklistedUserId);
            try {
                int revokedCount = notificationService.revokeNotificationsBySender(userId, blacklistedUserId);
                log.info("添加黑名单，已撤回通知: userId={}, blacklistedUserId={}, revokedCount={}",
                        userId, blacklistedUserId, revokedCount);
            } catch (Exception e) {
                log.warn("撤回通知失败: userId={}, blacklistedUserId={}", userId, blacklistedUserId);
            }
        }

        log.info("添加黑名单成功: userId={}, blacklistedUserId={}", userId, blacklistedUserId);
        return result > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean removeFromBlacklist(Long userId, Long blacklistedUserId) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(blacklistedUserId)) {
            log.warn("移除黑名单失败: 参数为空");
            return false;
        }

        LambdaQueryWrapper<UserBlacklist> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserBlacklist::getUserId, userId)
                .eq(UserBlacklist::getBlacklistedUserId, blacklistedUserId);

        int result = userBlacklistMapper.delete(wrapper);


        updateMutualStatus(userId, blacklistedUserId);

        log.info("移除黑名单成功: userId={}, blacklistedUserId={}", userId, blacklistedUserId);
        return result >= 0;
    }

    @Override
    public List<Long> getBlacklistedUserIds(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<UserBlacklist> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserBlacklist::getUserId, userId)
                .select(UserBlacklist::getBlacklistedUserId);

        return userBlacklistMapper.selectList(wrapper).stream()
                .map(UserBlacklist::getBlacklistedUserId)
                .collect(Collectors.toList());
    }

    @Override
    public List<Long> getBlacklisterUserIds(Long blacklistedUserId, Collection<Long> userIds) {
        if (ObjectUtils.isEmpty(blacklistedUserId) || ObjectUtils.isEmpty(userIds)) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<UserBlacklist> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserBlacklist::getBlacklistedUserId, blacklistedUserId)
                .in(UserBlacklist::getUserId, userIds)
                .select(UserBlacklist::getUserId);

        return userBlacklistMapper.selectList(wrapper).stream()
                .map(UserBlacklist::getUserId)
                .collect(Collectors.toList());
    }

    @Override
    public Boolean isBlacklisted(Long userId, Long blacklistedUserId) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(blacklistedUserId)) {
            return false;
        }

        LambdaQueryWrapper<UserBlacklist> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserBlacklist::getUserId, userId)
                .eq(UserBlacklist::getBlacklistedUserId, blacklistedUserId);

        Long count = userBlacklistMapper.selectCount(wrapper);
        return count != null && count > 0;
    }






    @Override
    public List<BlacklistUserVO> getBlacklistUserInfo(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return new ArrayList<>();
        }


        LambdaQueryWrapper<UserBlacklist> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserBlacklist::getUserId, userId)
                .orderByDesc(UserBlacklist::getCreateTime);

        List<UserBlacklist> blacklistList = userBlacklistMapper.selectList(wrapper);
        if (blacklistList.isEmpty()) {
            return new ArrayList<>();
        }


        List<Long> blacklistedUserIds = blacklistList.stream()
                .map(UserBlacklist::getBlacklistedUserId)
                .collect(Collectors.toList());


        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.in(User::getId, blacklistedUserIds);
        List<User> users = userMapper.selectList(userWrapper);
        java.util.Map<Long, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getId, u -> u));


        return blacklistList.stream()
                .map(blacklist -> {
                    BlacklistUserVO vo = new BlacklistUserVO();
                    vo.setId(blacklist.getId());
                    vo.setBlacklistedUserId(blacklist.getBlacklistedUserId());
                    vo.setReason(blacklist.getReason());
                    vo.setCreateTime(blacklist.getCreateTime());
                    vo.setIsMutual(blacklist.getIsMutual());


                    User user = userMap.get(blacklist.getBlacklistedUserId());
                    if (user != null) {
                        vo.setNickname(user.getNickname());
                        vo.setAvatar(user.getAvatar());
                        vo.setStatus(user.getStatus());
                        vo.setIsBanned(user.getIsBanned());
                    } else {
                        vo.setNickname("未知用户");
                        vo.setAvatar(null);
                    }

                    return vo;
                })
                .collect(Collectors.toList());
    }




    private void cleanupRelationshipsForBlacklist(Long userId, Long blacklistedUserId) {
        int removedForwardFollow = deleteFollowRelation(userId, blacklistedUserId);
        if (removedForwardFollow > 0) {
            decrementFollowCounters(userId, blacklistedUserId, removedForwardFollow);
        }

        int removedReverseFollow = deleteFollowRelation(blacklistedUserId, userId);
        if (removedReverseFollow > 0) {
            decrementFollowCounters(blacklistedUserId, userId, removedReverseFollow);
        }

        int removedFriendRecords = deleteFriendRelation(userId, blacklistedUserId)
                + deleteFriendRelation(blacklistedUserId, userId);
        log.info("拉黑清理社交关系: userId={}, blacklistedUserId={}, removedFollow={}, removedReverseFollow={}, removedFriendRecords={}",
                userId, blacklistedUserId, removedForwardFollow, removedReverseFollow, removedFriendRecords);
    }

    private int deleteFollowRelation(Long followerId, Long followeeId) {
        return userFollowMapper.delete(
                new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFollowerId, followerId)
                        .eq(UserFollow::getFolloweeId, followeeId)
                        .eq(UserFollow::getDeleted, 0)
        );
    }

    private int deleteFriendRelation(Long userId, Long friendId) {
        return userFriendMapper.delete(
                new LambdaQueryWrapper<UserFriend>()
                        .eq(UserFriend::getUserId, userId)
                        .eq(UserFriend::getFriendId, friendId)
        );
    }

    private void decrementFollowCounters(Long followerId, Long followeeId, int count) {
        for (int i = 0; i < count; i++) {
            userMapper.decrementFollowingCount(followerId);
            userMapper.decrementFansCount(followeeId);
        }
    }





    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMutualStatus(Long userId, Long blacklistedUserId) {

        LambdaQueryWrapper<UserBlacklist> wrapper1 = new LambdaQueryWrapper<>();
        wrapper1.eq(UserBlacklist::getUserId, userId)
                .eq(UserBlacklist::getBlacklistedUserId, blacklistedUserId);
        UserBlacklist record1 = userBlacklistMapper.selectOne(wrapper1);


        LambdaQueryWrapper<UserBlacklist> wrapper2 = new LambdaQueryWrapper<>();
        wrapper2.eq(UserBlacklist::getUserId, blacklistedUserId)
                .eq(UserBlacklist::getBlacklistedUserId, userId);
        UserBlacklist record2 = userBlacklistMapper.selectOne(wrapper2);


        boolean isMutual = (record1 != null) && (record2 != null);


        if (record1 != null && !java.util.Objects.equals(record1.getIsMutual(), isMutual ? 1 : 0)) {
            record1.setIsMutual(isMutual ? 1 : 0);
            userBlacklistMapper.updateById(record1);
        }

        if (record2 != null && !java.util.Objects.equals(record2.getIsMutual(), isMutual ? 1 : 0)) {
            record2.setIsMutual(isMutual ? 1 : 0);
            userBlacklistMapper.updateById(record2);
        }

        log.info("更新双向拉黑状态: userId={}, blacklistedUserId={}, isMutual={}",
                userId, blacklistedUserId, isMutual);
    }
}
