




package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.User;
import com.haoran.music.entity.UserFollow;
import com.haoran.music.mapper.UserFollowMapper;
import com.haoran.music.mapper.SubjectFollowMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.UserFollowService;
import com.haoran.music.service.UserBlacklistService;
import com.haoran.music.service.NotificationService;
import com.haoran.music.service.UserVipService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;




@Slf4j
@Service
public class UserFollowServiceImpl implements UserFollowService {

    private final UserFollowMapper userFollowMapper;
    private final UserMapper userMapper;
    private final UserBlacklistService userBlacklistService;

    private final NotificationService notificationService;
    private final UserVipService userVipService;
    private final SubjectFollowMapper subjectFollowMapper;

    public UserFollowServiceImpl(UserFollowMapper userFollowMapper, UserMapper userMapper,
                                  UserBlacklistService userBlacklistService,
                                  NotificationService notificationService,
                                  UserVipService userVipService,
                                  SubjectFollowMapper subjectFollowMapper) {
        this.userFollowMapper = userFollowMapper;
        this.userMapper = userMapper;
        this.userBlacklistService = userBlacklistService;
        this.notificationService = notificationService;
        this.userVipService = userVipService;
        this.subjectFollowMapper = subjectFollowMapper;
    }

    @Override
    public List<Long> getFollowingIds(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return Arrays.asList();
        }

        LambdaQueryWrapper<UserFollow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFollow::getFollowerId, userId)
                .eq(UserFollow::getDeleted, 0)
                .select(UserFollow::getFolloweeId);

        return userFollowMapper.selectList(wrapper).stream()
                .map(UserFollow::getFolloweeId)
                .collect(Collectors.toList());
    }

    @Override
    public List<Long> getFollowerIds(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return Arrays.asList();
        }

        LambdaQueryWrapper<UserFollow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFollow::getFolloweeId, userId)
                .eq(UserFollow::getDeleted, 0)
                .select(UserFollow::getFollowerId);

        return userFollowMapper.selectList(wrapper).stream()
                .map(UserFollow::getFollowerId)
                .collect(Collectors.toList());
    }

    @Override
    public boolean isFollowing(Long followerId, Long followeeId) {
        if (ObjectUtils.isEmpty(followerId) || ObjectUtils.isEmpty(followeeId)) {
            return false;
        }

        LambdaQueryWrapper<UserFollow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFollow::getFollowerId, followerId)
                .eq(UserFollow::getFolloweeId, followeeId)
                .eq(UserFollow::getDeleted, 0);

        Long count = userFollowMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean follow(Long followerId, Long followeeId) {
        if (ObjectUtils.isEmpty(followerId) || ObjectUtils.isEmpty(followeeId)) {
            return false;
        }

        if (followerId.equals(followeeId)) {
            log.warn("不能关注自己: userId={}", followerId);
            return false;
        }

        User follower = userMapper.selectById(followerId);
        User followee = userMapper.selectById(followeeId);
        if (!UserAccountStatusUtil.canInteract(follower)) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    UserAccountStatusUtil.currentUnavailableMessage(follower) + "，无法关注");
        }
        if (!UserAccountStatusUtil.canInteract(followee)) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    UserAccountStatusUtil.targetUnavailableMessage(followee) + "，无法关注");
        }

        Boolean senderBlacklisted = userBlacklistService.isBlacklisted(followerId, followeeId);
        Boolean receiverBlacklisted = userBlacklistService.isBlacklisted(followeeId, followerId);
        if (Boolean.TRUE.equals(senderBlacklisted) || Boolean.TRUE.equals(receiverBlacklisted)) {
            log.warn("无法关注黑名单关系用户: followerId={}, followeeId={}", followerId, followeeId);
            throw new BusinessException(ResultCode.FORBIDDEN, "黑名单关系无法关注");
        }

        if (isFollowing(followerId, followeeId)) {
            syncTypedUserFollow(followerId, followeeId);
            log.info("已经关注过: followerId={}, followeeId={}", followerId, followeeId);
            return true;
        }

        UserFollow userFollow = new UserFollow();
        userFollow.setFollowerId(followerId);
        userFollow.setFolloweeId(followeeId);

        int result = userFollowMapper.insert(userFollow);

        if (result > 0) {
            syncTypedUserFollow(followerId, followeeId);
            userMapper.incrementFollowingCount(followerId);
            userMapper.incrementFansCount(followeeId);

            try {
                notificationService.sendFollowNotification(followeeId, followerId, displayName(follower));
            } catch (Exception e) {
                log.warn("发送关注通知失败: followerId={}, followeeId={}", followerId, followeeId);
            }
        }

        log.info("关注操作: followerId={}, followeeId={}, result={}", followerId, followeeId, result);

        return result > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unfollow(Long followerId, Long followeeId) {
        if (ObjectUtils.isEmpty(followerId) || ObjectUtils.isEmpty(followeeId)) {
            return false;
        }

        LambdaQueryWrapper<UserFollow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFollow::getFollowerId, followerId)
                .eq(UserFollow::getFolloweeId, followeeId)
                .eq(UserFollow::getDeleted, 0);

        int result = userFollowMapper.delete(wrapper);

        if (result > 0) {
            cancelTypedUserFollow(followerId, followeeId);
            userMapper.decrementFollowingCount(followerId);
            userMapper.decrementFansCount(followeeId);

            try {
                notificationService.revokeFollowNotification(followeeId, followerId);
                log.info("取消关注，已撤回通知: followerId={}, followeeId={}", followerId, followeeId);
            } catch (Exception e) {
                log.warn("撤回关注通知失败: followerId={}, followeeId={}", followerId, followeeId);
            }
        }

        log.info("取消关注操作: followerId={}, followeeId={}, result={}", followerId, followeeId, result);

        return result > 0;
    }

    @Override
    public long getFollowingCount(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return 0;
        }

        LambdaQueryWrapper<UserFollow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFollow::getFollowerId, userId)
                .eq(UserFollow::getDeleted, 0);

        Long count = userFollowMapper.selectCount(wrapper);
        return count != null ? count : 0L;
    }

    @Override
    public long getFollowerCount(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return 0;
        }

        LambdaQueryWrapper<UserFollow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFollow::getFolloweeId, userId)
                .eq(UserFollow::getDeleted, 0);

        Long count = userFollowMapper.selectCount(wrapper);
        return count != null ? count : 0L;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeFollower(Long followerId, Long userId) {
        if (ObjectUtils.isEmpty(followerId) || ObjectUtils.isEmpty(userId)) {
            log.warn("移除粉丝失败: 参数为空");
            return false;
        }


        LambdaQueryWrapper<UserFollow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFollow::getFollowerId, followerId)
                .eq(UserFollow::getFolloweeId, userId)
                .eq(UserFollow::getDeleted, 0);

        int result = userFollowMapper.delete(wrapper);

        if (result > 0) {
            cancelTypedUserFollow(followerId, userId);
            userMapper.decrementFollowingCount(followerId);
            userMapper.decrementFansCount(userId);
            log.info("移除粉丝成功: followerId={}, userId={}, result={}",
                    followerId, userId, result);
        }

        return result > 0;
    }







    private void syncTypedUserFollow(Long followerId, Long followeeId) {
        subjectFollowMapper.activate(followerId, "user", followeeId, true);
    }







    private void cancelTypedUserFollow(Long followerId, Long followeeId) {
        subjectFollowMapper.cancelBySubject(followerId, "user", followeeId);
    }

    @Override
    public List<Object> getRecommendUsers(Long userId, Integer limit) {
        int safeLimit = normalizeRecommendLimit(limit);


        List<Long> followingIds = ObjectUtils.isEmpty(userId) ? Collections.emptyList() : getFollowingIds(userId);
        Set<Long> excludedIds = relationExcludedUserIds(userId, followingIds);


        LambdaQueryWrapper<User> wrapper = UserAccountStatusUtil.publicStatsUserQuery();

        if (!excludedIds.isEmpty()) {
            wrapper.notIn(User::getId, excludedIds);
        }

        if (!ObjectUtils.isEmpty(userId)) {
            wrapper.ne(User::getId, userId);         
        }

        int candidateLimit = Math.min(80, Math.max(safeLimit * 4, safeLimit + 10));
        wrapper.orderByDesc(User::getFansCount)
                .last("LIMIT " + candidateLimit);

        List<User> users = userMapper.selectList(wrapper);
        Set<Long> reverseBlockedIds = reverseBlockedUserIds(userId, users);
        Set<Long> vipUserIds = userVipService.getActiveVipExpirations(
                users.stream().map(User::getId).collect(Collectors.toSet())).keySet();


        return users.stream()
                .filter(UserAccountStatusUtil::canAppearInRecommendations)
                .filter(user -> !excludedIds.contains(user.getId()))
                .filter(user -> !reverseBlockedIds.contains(user.getId()))
                .limit(safeLimit)
                .map(user -> {
                    Map<String, Object> vo = new HashMap<>();
                    vo.put("id", user.getId());
                    vo.put("nickname", user.getNickname());
                    vo.put("avatar", user.getAvatar());
                    vo.put("introduction", user.getIntroduction());
                    Integer fansCount = user.getFansCount() != null ? user.getFansCount() : 0;
                    vo.put("fansCount", fansCount);
                    vo.put("followerCount", fansCount);
                    vo.put("followingCount", user.getFollowingCount() != null ? user.getFollowingCount() : 0);
                    vo.put("isFollowing", false);
                    vo.put("isCreator", user.getIsCreator() != null && user.getIsCreator() == 1);
                    vo.put("isVip", vipUserIds.contains(user.getId()));
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> getPersonalizedRecommendUsers(Long userId, Integer limit) {
        int safeLimit = normalizeRecommendLimit(limit);


        List<Long> followingIds = ObjectUtils.isEmpty(userId) ? Collections.emptyList() : getFollowingIds(userId);
        Set<Long> excludedIds = relationExcludedUserIds(userId, followingIds);


        LambdaQueryWrapper<User> wrapper = UserAccountStatusUtil.publicStatsUserQuery();

        if (!excludedIds.isEmpty()) {
            wrapper.notIn(User::getId, excludedIds);
        }

        if (!ObjectUtils.isEmpty(userId)) {
            wrapper.ne(User::getId, userId);         
        }


        int candidateLimit = Math.min(80, Math.max(safeLimit * 4, safeLimit + 10));
        wrapper.orderByDesc(User::getIsCreator)
                .orderByDesc(User::getFansCount)
                .last("LIMIT " + candidateLimit);                

        List<User> users = userMapper.selectList(wrapper);
        Set<Long> reverseBlockedIds = reverseBlockedUserIds(userId, users);
        Set<Long> vipUserIds = userVipService.getActiveVipExpirations(
                users.stream().map(User::getId).collect(Collectors.toSet())).keySet();


        List<Map<String, Object>> result = new ArrayList<>();
        for (User user : users) {
            if (result.size() >= safeLimit) break;
            if (!UserAccountStatusUtil.canAppearInRecommendations(user)
                    || excludedIds.contains(user.getId())
                    || reverseBlockedIds.contains(user.getId())) {
                continue;
            }

            Map<String, Object> vo = new HashMap<>();
            vo.put("id", user.getId());
            vo.put("nickname", user.getNickname());
            vo.put("avatar", user.getAvatar());
            vo.put("introduction", user.getIntroduction());
            Integer fansCount = user.getFansCount() != null ? user.getFansCount() : 0;
            vo.put("fansCount", fansCount);
            vo.put("followerCount", fansCount);
            vo.put("followingCount", user.getFollowingCount() != null ? user.getFollowingCount() : 0);
            vo.put("isCreator", user.getIsCreator() != null && user.getIsCreator() == 1);
            boolean isVip = vipUserIds.contains(user.getId());
            vo.put("isVip", isVip);
            vo.put("isFollowing", false);


            String recommendReason = generateRecommendReason(user, followingIds, isVip);
            vo.put("recommendReason", recommendReason);

            result.add(vo);
        }

        log.info("个性化推荐用户: userId={}, count={}", userId, result.size());
        return result;
    }

    private int normalizeRecommendLimit(Integer limit) {
        return ObjectUtils.isEmpty(limit) || limit <= 0 ? 5 : Math.min(limit, 20);
    }

    private Set<Long> relationExcludedUserIds(Long userId, Collection<Long> followingIds) {
        Set<Long> excluded = new HashSet<>();
        if (followingIds != null) {
            excluded.addAll(followingIds);
        }
        if (userId != null) {
            excluded.add(userId);
            List<Long> blocked = userBlacklistService.getBlacklistedUserIds(userId);
            if (blocked != null) {
                excluded.addAll(blocked);
            }
        }
        return excluded;
    }

    private Set<Long> reverseBlockedUserIds(Long userId, Collection<User> users) {
        if (userId == null || users == null || users.isEmpty()) {
            return Collections.emptySet();
        }
        List<Long> candidateIds = users.stream()
                .map(User::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        List<Long> reverseBlocked = userBlacklistService.getBlacklisterUserIds(userId, candidateIds);
        return reverseBlocked == null ? Collections.emptySet() : new HashSet<>(reverseBlocked);
    }








    @SuppressWarnings("unused")
    private String generateRecommendReason(User user, List<Long> followingIds, boolean isVip) {
        List<String> reasons = new ArrayList<>();


        if (user.getIsCreator() != null && user.getIsCreator() == 1) {
            reasons.add("优质创作者");
        }


        if (user.getFansCount() != null && user.getFansCount() >= 1000) {
            reasons.add("热门用户");
        }


        if (isVip) {
            reasons.add("VIP会员");
        }


        if (!ObjectUtils.isEmpty(user.getIntroduction()) && user.getIntroduction().trim().length() > 0) {
            reasons.add("活跃用户");
        }


        if (reasons.isEmpty()) {
            return "推荐关注";
        } else if (reasons.size() == 1) {
            return reasons.get(0);
        } else {
            return reasons.get(0) + " · " + reasons.get(1);
        }
    }
    private String displayName(User user) {
        if (user == null) {
            return "用户";
        }
        if (ObjectUtils.isNotEmpty(user.getNickname())) {
            return user.getNickname();
        }
        if (ObjectUtils.isNotEmpty(user.getUsername())) {
            return user.getUsername();
        }
        return "用户";
    }
}
