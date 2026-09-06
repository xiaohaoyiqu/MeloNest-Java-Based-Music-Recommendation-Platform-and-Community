   
                      
   
package com.haoran.music.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.aspect.RateLimit;
import com.haoran.music.common.result.Result;
import com.haoran.music.entity.CreatorWorkPurchase;
import com.haoran.music.entity.PaymentOrder;
import com.haoran.music.entity.ResourcePurchase;
import com.haoran.music.mapper.CreatorWorkPurchaseMapper;
import com.haoran.music.mapper.PaymentOrderMapper;
import com.haoran.music.mapper.ResourcePurchaseMapper;
import com.haoran.music.service.UserBlacklistService;
import com.haoran.music.service.UserFollowService;
import com.haoran.music.service.UserFriendService;
import com.haoran.music.service.UserService;
import com.haoran.music.service.UserVipService;
import com.haoran.music.vo.user.PublicUserVO;
import com.haoran.music.vo.user.UserVO;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

   
                                      
   
@RestController
@RequestMapping("/user")
public class UserFollowController {

    @Resource
    private UserService userService;

    @Resource
    private UserVipService userVipService;

    @Resource
    private UserFollowService userFollowService;

    @Resource
    private UserFriendService userFriendService;

    @Resource
    private UserBlacklistService userBlacklistService;

    @Resource
    private PaymentOrderMapper paymentOrderMapper;

    @Resource
    private ResourcePurchaseMapper resourcePurchaseMapper;

    @Resource
    private CreatorWorkPurchaseMapper creatorWorkPurchaseMapper;

    @ApiLog("关注用户")
    @RateLimit(maxRequests = 30, timeWindowSeconds = 3600, operation = "followUser",
               message = "关注操作过于频繁，请稍后再试")
    @PostMapping({"/follow/{followeeId}", "/follow/follow/{followeeId}"})
    public Result<Boolean> followUser(@RequestAttribute(value = "userId", required = false) Long userId,
                                      @PathVariable("followeeId") Long followeeId) {
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        if (userId.equals(followeeId)) {
            return Result.error(400, "不能关注自己");
        }
        boolean success = userFollowService.follow(userId, followeeId);
        return success ? Result.success(true) : Result.error(400, "关注失败");
    }

    @ApiLog("取消关注用户")
    @DeleteMapping({"/follow/{followeeId}", "/follow/follow/{followeeId}"})
    public Result<Boolean> unfollowUser(@RequestAttribute(value = "userId", required = false) Long userId,
                                        @PathVariable("followeeId") Long followeeId) {
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        boolean success = userFollowService.unfollow(userId, followeeId);
        return success ? Result.success(true) : Result.error(400, "取消关注失败");
    }

    @ApiLog("移除粉丝")
    @DeleteMapping("/follower/remove")
    public Result<Boolean> removeFollower(@RequestAttribute(value = "userId", required = false) Long userId,
                                          @RequestParam Long followerId) {
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        if (userId.equals(followerId)) {
            return Result.error(400, "不能移除自己");
        }
        boolean success = userFollowService.removeFollower(followerId, userId);
        return success ? Result.success(true) : Result.error(400, "移除粉丝失败");
    }

    @ApiLog("检查是否已关注")
    @GetMapping({"/is-following/{followeeId}", "/follow/check/{followeeId}"})
    public Result<Boolean> isFollowing(@RequestAttribute(value = "userId", required = false) Long userId,
                                       @PathVariable("followeeId") Long followeeId) {
        if (userId == null) {
            return Result.success(false);
        }
        return Result.success(userFollowService.isFollowing(userId, followeeId));
    }

    @ApiLog("获取用户关系状态")
    @GetMapping("/relation/{targetUserId}")
    public Result<Map<String, Object>> getUserRelation(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @PathVariable("targetUserId") Long targetUserId) {
        Map<String, Object> relation = createEmptyRelation();

        if (targetUserId == null) {
            relation.put("relationType", "normal");
            return Result.success(relation);
        }

        if (userId == null) {
            relation.put("relationType", "normal");
            return Result.success(relation);
        }

        boolean self = userId.equals(targetUserId);
        relation.put("self", self);
        if (self) {
            relation.put("relationType", "self");
            return Result.success(relation);
        }

        boolean following = userFollowService.isFollowing(userId, targetUserId);
        boolean follower = userFollowService.isFollowing(targetUserId, userId);
        boolean mutual = following && follower;
        boolean blockedByMe = Boolean.TRUE.equals(userBlacklistService.isBlacklisted(userId, targetUserId));
        boolean blockedMe = Boolean.TRUE.equals(userBlacklistService.isBlacklisted(targetUserId, userId));
        boolean blacklisted = blockedByMe || blockedMe;
        boolean friend = !blacklisted && userFriendService.isFriend(userId, targetUserId);
        boolean friendRequestPending = !blacklisted && !friend && userFriendService.hasPendingFriendRequest(userId, targetUserId);
        boolean trade = !blacklisted && hasTradeRelation(userId, targetUserId);

        relation.put("following", following);
        relation.put("follower", follower);
        relation.put("mutual", mutual);
        relation.put("friend", friend);
        relation.put("friendRequestPending", friendRequestPending);
        relation.put("blacklisted", blacklisted);
        relation.put("blockedByMe", blockedByMe);
        relation.put("blockedMe", blockedMe);
        relation.put("trade", trade);
        relation.put("relationType", resolveRelationType(following, follower, friend, friendRequestPending, blockedByMe, blockedMe, trade));
        return Result.success(relation);
    }

    private boolean hasTradeRelation(Long userId, Long targetUserId) {
        if (userId == null || targetUserId == null || userId.equals(targetUserId)) {
            return false;
        }

        LambdaQueryWrapper<PaymentOrder> paymentWrapper = new LambdaQueryWrapper<>();
        paymentWrapper.eq(PaymentOrder::getStatus, "paid")
                .in(PaymentOrder::getBusinessType, "purchase", "reward", "subscribe")
                .and(wrapper -> wrapper
                        .eq(PaymentOrder::getUserId, userId)
                        .eq(PaymentOrder::getPayeeId, targetUserId)
                        .or()
                        .eq(PaymentOrder::getUserId, targetUserId)
                        .eq(PaymentOrder::getPayeeId, userId));
        Long paymentCount = paymentOrderMapper.selectCount(paymentWrapper);
        if (paymentCount != null && paymentCount > 0) {
            return true;
        }

        LambdaQueryWrapper<ResourcePurchase> resourceWrapper = new LambdaQueryWrapper<>();
        resourceWrapper.eq(ResourcePurchase::getStatus, "active")
                .and(wrapper -> wrapper
                        .eq(ResourcePurchase::getUserId, userId)
                        .eq(ResourcePurchase::getOwnerId, targetUserId)
                        .or()
                        .eq(ResourcePurchase::getUserId, targetUserId)
                        .eq(ResourcePurchase::getOwnerId, userId));
        Long resourceCount = resourcePurchaseMapper.selectCount(resourceWrapper);
        if (resourceCount != null && resourceCount > 0) {
            return true;
        }

        LambdaQueryWrapper<CreatorWorkPurchase> workWrapper = new LambdaQueryWrapper<>();
        workWrapper.eq(CreatorWorkPurchase::getStatus, "success")
                .and(wrapper -> wrapper
                        .eq(CreatorWorkPurchase::getUserId, userId)
                        .eq(CreatorWorkPurchase::getCreatorId, targetUserId)
                        .or()
                        .eq(CreatorWorkPurchase::getUserId, targetUserId)
                        .eq(CreatorWorkPurchase::getCreatorId, userId));
        Long workCount = creatorWorkPurchaseMapper.selectCount(workWrapper);
        return workCount != null && workCount > 0;
    }

    private Map<String, Object> createEmptyRelation() {
        Map<String, Object> relation = new HashMap<>();
        relation.put("self", false);
        relation.put("following", false);
        relation.put("follower", false);
        relation.put("mutual", false);
        relation.put("friend", false);
        relation.put("friendRequestPending", false);
        relation.put("blacklisted", false);
        relation.put("blockedByMe", false);
        relation.put("blockedMe", false);
        relation.put("trade", false);
        relation.put("relationType", "normal");
        return relation;
    }

    private String resolveRelationType(boolean following,
                                       boolean follower,
                                       boolean friend,
                                       boolean friendRequestPending,
                                       boolean blockedByMe,
                                       boolean blockedMe,
                                       boolean trade) {
        if (blockedByMe) {
            return "blocked";
        }
        if (blockedMe) {
            return "blocked_by";
        }
        if (friend) {
            return "friend";
        }
        if (friendRequestPending) {
            return "friend_pending";
        }
        if (following && follower) {
            return "mutual";
        }
        if (following) {
            return "following";
        }
        if (follower) {
            return "follower";
        }
        if (trade) {
            return "trade";
        }
        return "normal";
    }

    @ApiLog("获取用户关注列表")
    @GetMapping({"/following/{userId}", "/follow/following/{userId}"})
    public Result<IPage<PublicUserVO>> getFollowingList(@PathVariable("userId") Long userId,
                                                        @RequestParam(value = "page", defaultValue = "1") Integer page,
                                                        @RequestParam(value = "size", defaultValue = "20") Integer size) {
        return Result.success(toPublicUserPage(userService.getFollowingList(userId, page, size)));
    }

    @ApiLog("获取用户粉丝列表")
    @GetMapping({"/followers/{userId}", "/follow/followers/{userId}"})
    public Result<IPage<PublicUserVO>> getFollowersList(@PathVariable("userId") Long userId,
                                                        @RequestParam(value = "page", defaultValue = "1") Integer page,
                                                        @RequestParam(value = "size", defaultValue = "20") Integer size) {
        return Result.success(toPublicUserPage(userService.getFollowersList(userId, page, size)));
    }

    @ApiLog("获取关注统计")
    @GetMapping("/follow/stats/{userId}")
    public Result<Map<String, Object>> getFollowStats(@PathVariable("userId") Long userId) {
        long followingCount = userFollowService.getFollowingCount(userId);
        long followerCount = userFollowService.getFollowerCount(userId);
        Map<String, Object> stats = new HashMap<>();
        stats.put("followingCount", followingCount);
        stats.put("followerCount", followerCount);
        return Result.success(stats);
    }

    @ApiLog("批量检查关注状态")
    @PostMapping("/follow/check-batch")
    public Result<Map<Long, Boolean>> checkBatchFollowing(@RequestBody List<Long> userIds,
                                                          @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        Map<Long, Boolean> result = new HashMap<>();
        for (Long followeeId : userIds) {
            result.put(followeeId, userFollowService.isFollowing(userId, followeeId));
        }
        return Result.success(result);
    }

    private IPage<PublicUserVO> toPublicUserPage(IPage<UserVO> source) {
        Map<Long, LocalDateTime> vipExpirations = userVipService.getActiveVipExpirations(source.getRecords().stream()
                .map(UserVO::getId)
                .collect(Collectors.toList()));
        return source.convert(user -> toPublicUser(user, vipExpirations));
    }

    private PublicUserVO toPublicUser(UserVO source, Map<Long, LocalDateTime> vipExpirations) {
        PublicUserVO target = new PublicUserVO();
        target.setId(source.getId());
        target.setUsername(source.getUsername());
        target.setNickname(source.getNickname());
        target.setAvatar(source.getAvatar());
        target.setSignature(source.getSignature());
        target.setStatus(source.getStatus());
        target.setFansCount(source.getFansCount());
        target.setFollowingCount(source.getFollowingCount());
        target.setIsCreator(source.getIsCreator());
        target.setCreatorStatus(source.getCreatorStatus());
        target.setIsVip(vipExpirations.containsKey(source.getId()));
        target.setVerifiedInfo(source.getVerifiedInfo());
        target.setWorksCount(source.getWorksCount());
        return target;
    }
}
