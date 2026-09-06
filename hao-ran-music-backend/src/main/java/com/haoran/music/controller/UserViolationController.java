


package com.haoran.music.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.result.Result;
import com.haoran.music.entity.UserViolation;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.UserViolationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;




@RestController
public class UserViolationController {

    @Resource
    private UserViolationService userViolationService;

    @ApiLog("获取我的违规记录")
    @GetMapping("/violation/my")
    public Result<IPage<UserViolation>> getMyViolations(
            HttpServletRequest request,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        Page<UserViolation> pageInfo = new Page<>(page, size);
        LambdaQueryWrapper<UserViolation> wrapper = new LambdaQueryWrapper<UserViolation>()
                .eq(UserViolation::getUserId, userId)
                .orderByDesc(UserViolation::getCreateTime);
        return Result.success(userViolationService.page(pageInfo, wrapper));
    }

    @ApiLog("管理员获取违规记录列表")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/admin/violation/list")
    public Result<IPage<UserViolation>> getAllViolations(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Integer violationLevel,
            @RequestParam(required = false) Integer isResolved) {
        Page<UserViolation> pageInfo = new Page<>(page, size);
        LambdaQueryWrapper<UserViolation> wrapper = new LambdaQueryWrapper<UserViolation>()
                .orderByDesc(UserViolation::getCreateTime);
        if (userId != null) {
            wrapper.eq(UserViolation::getUserId, userId);
        }
        if (violationLevel != null) {
            wrapper.eq(UserViolation::getViolationLevel, violationLevel);
        }
        if (isResolved != null) {
            wrapper.eq(UserViolation::getIsResolved, isResolved);
        }
        return Result.success(userViolationService.page(pageInfo, wrapper));
    }
}