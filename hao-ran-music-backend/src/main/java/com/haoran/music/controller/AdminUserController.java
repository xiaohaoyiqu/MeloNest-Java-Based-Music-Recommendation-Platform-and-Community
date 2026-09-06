package com.haoran.music.controller;



import com.haoran.music.enums.UserRole;
import com.haoran.music.common.annotation.RequireRole;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.dto.PageResult;
import com.haoran.music.common.result.Result;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.dto.user.UserQueryDTO;
import com.haoran.music.service.UserService;
import com.haoran.music.vo.user.AdminUserDetailVO;
import com.haoran.music.vo.user.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;





@Slf4j
@RestController
@RequestMapping("/admin/user")
@RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
public class AdminUserController {

    private static final int MAX_BATCH_USER_OPERATION_SIZE = 50;

    @Resource
    private UserService userService;






        @PostMapping("/list")
    @ApiLog("Admin get user list")
    public Result<PageResult<UserVO>> getUserList(@RequestBody UserQueryDTO dto) {
        IPage<UserVO> page = userService.pageUsers(dto);
        PageResult<UserVO> result = new PageResult<>();
        result.setRecords(page.getRecords());
        result.setTotal(page.getTotal());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setPages(page.getPages());
        return Result.success(result);
    }






        @PostMapping("/disable/{userId}")
    @ApiLog("Admin disable user")
    public Result<Void> disableUser(@PathVariable Long userId) {
        userService.updateUserStatus(userId, 0);
        return Result.success();
    }






        @PostMapping("/enable/{userId}")
    @ApiLog("Admin enable user")
    public Result<Void> enableUser(@PathVariable Long userId) {
        userService.updateUserStatus(userId, 1);
        return Result.success();
    }






        @PostMapping("/batch/disable")
    @ApiLog("Admin batch disable users")
    public Result<Map<String, Object>> batchDisableUsers(@RequestBody List<Long> userIds) {
        requireBatchUserIds(userIds);
        int count = userService.batchUpdateUserStatus(userIds, 0);
        Map<String, Object> result = new HashMap<>();
        result.put("successCount", count);
        result.put("total", userIds.size());
        return Result.success(result);
    }






        @PostMapping("/batch/enable")
    @ApiLog("Admin batch enable users")
    public Result<Map<String, Object>> batchEnableUsers(@RequestBody List<Long> userIds) {
        requireBatchUserIds(userIds);
        int count = userService.batchUpdateUserStatus(userIds, 1);
        Map<String, Object> result = new HashMap<>();
        result.put("successCount", count);
        result.put("total", userIds.size());
        return Result.success(result);
    }







        @PostMapping("/role/{userId}")
    @ApiLog("Admin update user role")
    public Result<Void> updateUserRole(
            @PathVariable Long userId,
            @RequestParam String role
    ) {
        userService.updateUserRole(userId, role);
        return Result.success();
    }







        @PostMapping("/batch/role")
    @ApiLog("Admin batch update user role")
    public Result<Map<String, Object>> batchUpdateUserRole(
            @RequestBody List<Long> userIds,
            @RequestParam String role
    ) {
        requireBatchUserIds(userIds);
        int count = userService.batchUpdateUserRole(userIds, role);
        Map<String, Object> result = new HashMap<>();
        result.put("successCount", count);
        result.put("total", userIds.size());
        return Result.success(result);
    }






        @DeleteMapping("/{userId}")
    @ApiLog("Admin delete user")
    public Result<Void> deleteUser(@PathVariable Long userId) {
        userService.adminDeleteUser(userId);
        return Result.success();
    }






        @DeleteMapping("/batch")
    @ApiLog("Admin batch delete users")
    public Result<Map<String, Object>> batchDeleteUsers(@RequestBody List<Long> userIds) {
        requireBatchUserIds(userIds);
        int count = userService.batchDeleteUsers(userIds);
        Map<String, Object> result = new HashMap<>();
        result.put("successCount", count);
        result.put("total", userIds.size());
        return Result.success(result);
    }







        @PostMapping("/reset-password/{userId}")
    @ApiLog("Admin reset user password")
    public Result<Void> resetUserPassword(
            @PathVariable Long userId,
            @RequestParam String newPassword
    ) {
        userService.adminResetPassword(userId, newPassword);
        return Result.success();
    }







        @PutMapping("/edit/{userId}")
    @ApiLog("Admin edit user info")
    public Result<Void> editUserInfo(
            @PathVariable Long userId,
            @RequestBody Map<String, Object> params
    ) {
        userService.adminEditUserInfo(userId, params);
        return Result.success();
    }






        @GetMapping("/detail/{userId}")
    @ApiLog("Admin get user detail")
    public Result<AdminUserDetailVO> getUserDetail(@PathVariable Long userId) {
        return Result.success(AdminUserDetailVO.from(userService.getUserEntityById(userId)));
    }

    private void requireBatchUserIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户ID列表不能为空");
        }
        if (userIds.size() > MAX_BATCH_USER_OPERATION_SIZE) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "单次最多处理50个用户");
        }
        if (userIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户ID不能为空");
        }
    }

}
