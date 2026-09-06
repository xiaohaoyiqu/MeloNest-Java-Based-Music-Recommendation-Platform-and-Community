




package com.haoran.music.controller;

import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.result.Result;
import com.haoran.music.entity.UserBlacklist;
import com.haoran.music.service.UserBlacklistService;
import com.haoran.music.service.UserService;
import com.haoran.music.vo.blacklist.BlacklistUserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;




@Slf4j
@RestController
@RequestMapping("/blacklist")
public class UserBlacklistController {

    @Resource
    private UserBlacklistService userBlacklistService;

    @Resource
    private UserService userService;




    @PostMapping("/add")
    @ApiLog("添加黑名单")
    public Result<Void> addToBlacklist(
            @RequestParam Long blacklistedUserId,
            @RequestParam(required = false) String reason,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }

        Boolean success = userBlacklistService.addToBlacklist(userId, blacklistedUserId, reason);
        if (success) {
            return Result.success("添加成功");
        }
        return Result.error("添加失败");
    }




    @DeleteMapping("/remove")
    @ApiLog("移除黑名单")
    public Result<Void> removeFromBlacklist(
            @RequestParam Long blacklistedUserId,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }

        Boolean success = userBlacklistService.removeFromBlacklist(userId, blacklistedUserId);
        if (success) {
            return Result.success("移除成功");
        }
        return Result.error("移除失败");
    }




    @GetMapping("/list")
    @ApiLog("获取黑名单列表")
    public Result<List<Long>> getBlacklistList(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }

        List<Long> blacklistedIds = userBlacklistService.getBlacklistedUserIds(userId);

        return Result.success(blacklistedIds);
    }




    @GetMapping("/list/detail")
    @ApiLog("获取黑名单详情列表")
    public Result<List<BlacklistUserVO>> getBlacklistDetailList(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }

        List<BlacklistUserVO> blacklistUsers = userBlacklistService.getBlacklistUserInfo(userId);

        return Result.success(blacklistUsers);
    }




    @GetMapping("/check")
    @ApiLog("检查黑名单状态")
    public Result<Map<String, Boolean>> checkBlacklist(
            @RequestParam Long blacklistedUserId,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }

        Boolean isBlacklisted = userBlacklistService.isBlacklisted(userId, blacklistedUserId);
        Map<String, Boolean> result = new HashMap<>();
        result.put("isBlacklisted", isBlacklisted);
        return Result.success(result);
    }
}
