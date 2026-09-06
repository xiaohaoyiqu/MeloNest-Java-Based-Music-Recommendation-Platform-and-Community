




package com.haoran.music.controller;

import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.result.Result;
import com.haoran.music.dto.friend.FriendGroupAssignmentRequest;
import com.haoran.music.dto.friend.FriendGroupCreateRequest;
import com.haoran.music.dto.friend.FriendRemarkRequest;
import com.haoran.music.service.UserFriendService;
import com.haoran.music.vo.friend.FriendGroupVO;
import com.haoran.music.vo.friend.FriendRequestVO;
import com.haoran.music.vo.friend.FriendVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

import javax.annotation.Resource;
import java.util.List;




@Slf4j
@RestController
@RequestMapping("/friend")
public class UserFriendController {

    @Resource
    private UserFriendService userFriendService;








    @ApiLog("发送好友请求")
    @PostMapping("/request/{targetUserId}")
    public Result<Boolean> sendFriendRequest(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @PathVariable("targetUserId") Long targetUserId) {
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        boolean success = userFriendService.sendFriendRequest(userId, targetUserId);
        return success ? Result.success(true) : Result.error("发送好友请求失败");
    }









    @ApiLog("处理好友请求")
    @PutMapping("/request/{requestId}")
    public Result<Boolean> handleFriendRequest(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @PathVariable("requestId") Long requestId,
            @RequestParam("approved") Boolean approved) {
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        boolean success = userFriendService.handleFriendRequest(requestId, userId, approved);
        return success ? Result.success(true) : Result.error("处理好友请求失败");
    }








    @ApiLog("获取好友列表")
    @GetMapping("/list")
    public Result<List<FriendVO>> getFriendList(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestParam(value = "groupId", required = false) Long groupId) {
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        List<FriendVO> friends = userFriendService.getFriendList(userId, groupId);
        return Result.success(friends);
    }







    @ApiLog("获取好友请求列表")
    @GetMapping("/requests")
    public Result<List<FriendRequestVO>> getFriendRequests(
            @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        List<FriendRequestVO> requests = userFriendService.getFriendRequests(userId);
        return Result.success(requests);
    }








    @ApiLog("取消好友")
    @DeleteMapping("/{friendId}")
    public Result<Boolean> unfriend(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @PathVariable("friendId") Long friendId) {
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        boolean success = userFriendService.unfriend(userId, friendId);
        return success ? Result.success(true) : Result.error("取消好友失败");
    }









    @ApiLog("设置特别关注")
    @PutMapping("/special/{friendId}")
    public Result<Boolean> setSpecialMark(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @PathVariable("friendId") Long friendId,
            @RequestParam(value = "specialMark", required = false) String specialMark) {
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        boolean success = userFriendService.setSpecialMark(userId, friendId, specialMark);
        return success ? Result.success(true) : Result.error("设置特别关注失败");
    }








    @ApiLog("设置好友分组")
    @PutMapping("/group")
    public Result<Boolean> setFriendGroup(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @Valid @RequestBody FriendGroupAssignmentRequest request) {
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        boolean success = userFriendService.setFriendGroup(userId, request.getFriendId(), request.getGroupId());
        return success ? Result.success(true) : Result.error("设置分组失败");
    }








    @ApiLog("设置好友备注")
    @PutMapping("/remark")
    public Result<Boolean> setFriendRemark(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @Valid @RequestBody FriendRemarkRequest request) {
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        boolean success = userFriendService.setFriendRemark(userId, request.getFriendId(), request.getRemark());
        return success ? Result.success(true) : Result.error("设置备注失败");
    }









    @ApiLog("屏蔽好友")
    @PutMapping("/block/{friendId}")
    public Result<Boolean> blockFriend(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @PathVariable("friendId") Long friendId,
            @RequestParam("blocked") Boolean blocked) {
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        boolean success = userFriendService.blockFriend(userId, friendId, blocked);
        return success ? Result.success(true) : Result.error("操作失败");
    }








    @ApiLog("检查是否为好友")
    @GetMapping("/check/{targetUserId}")
    public Result<Boolean> isFriend(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @PathVariable("targetUserId") Long targetUserId) {
        if (userId == null) {
            return Result.success(false);
        }
        boolean isFriend = userFriendService.isFriend(userId, targetUserId);
        return Result.success(isFriend);
    }








    @ApiLog("检查是否特别关注")
    @GetMapping("/special/check/{friendId}")
    public Result<Boolean> isSpecialFollow(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @PathVariable("friendId") Long friendId) {
        if (userId == null) {
            return Result.success(false);
        }
        boolean isSpecial = userFriendService.isSpecialFollow(userId, friendId);
        return Result.success(isSpecial);
    }







    @ApiLog("获取特别关注列表")
    @GetMapping("/special/list")
    public Result<List<FriendVO>> getSpecialFollows(
            @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        List<FriendVO> friends = userFriendService.getSpecialFollows(userId);
        return Result.success(friends);
    }







    @ApiLog("获取互关好友列表")
    @GetMapping("/mutual/not-special")
    public Result<List<FriendVO>> getMutualFriendsNotSpecial(
            @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        List<FriendVO> friends = userFriendService.getMutualFriendsNotSpecial(userId);
        return Result.success(friends);
    }







    @ApiLog("获取好友分组列表")
    @GetMapping("/groups")
    public Result<List<FriendGroupVO>> getFriendGroups(
            @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        List<FriendGroupVO> groups = userFriendService.getFriendGroups(userId);
        return Result.success(groups);
    }








    @ApiLog("创建好友分组")
    @PostMapping("/group")
    public Result<Long> createFriendGroup(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @Valid @RequestBody FriendGroupCreateRequest request) {
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        Long groupId = userFriendService.createFriendGroup(userId, request.getGroupName());
        return Result.success(groupId);
    }








    @ApiLog("删除好友分组")
    @DeleteMapping("/group/{groupId}")
    public Result<Boolean> deleteFriendGroup(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @PathVariable Long groupId) {
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        boolean result = userFriendService.deleteFriendGroup(userId, groupId);
        return Result.success(result);
    }
}
