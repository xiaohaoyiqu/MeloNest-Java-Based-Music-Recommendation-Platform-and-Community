package com.haoran.music.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.aspect.ApiLog;
import java.util.HashMap;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.common.result.Result;
import com.haoran.music.common.vo.ModeratorVO;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.ModeratorManagementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;





@Slf4j
@RestController
@RequestMapping("/moderator/admin")
@RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
public class ModeratorManagementController {

    @Autowired
    private ModeratorManagementService moderatorManagementService;




    @PostMapping("/set/{userId}")
    public Result<Void> setAsModerator(
            @PathVariable Long userId,
            @RequestParam(required = false) String moderatorNote,
            @RequestParam(required = false, defaultValue = "100") Integer dailyQuota) {

        boolean success = moderatorManagementService.setAsModerator(userId, moderatorNote, dailyQuota);
        return success ? Result.success() : Result.error(500, "设置失败");
    }




    @PostMapping("/remove/{userId}")
    public Result<Void> removeModerator(@PathVariable Long userId) {
        boolean success = moderatorManagementService.removeModerator(userId);
        return success ? Result.success() : Result.error(500, "取消失败");
    }




    @PostMapping("/status/{userId}")
    public Result<Void> updateModeratorStatus(
            @PathVariable Long userId,
            @RequestParam String status) {

        boolean success = moderatorManagementService.updateModeratorStatus(userId, status);
        return success ? Result.success() : Result.error(500, "更新失败");
    }




    @PostMapping("/update/{userId}")
    public Result<Void> updateModeratorInfo(
            @PathVariable Long userId,
            @RequestParam(required = false) String moderatorNote,
            @RequestParam(required = false) Integer dailyQuota) {

        boolean success = moderatorManagementService.updateModeratorInfo(userId, moderatorNote, dailyQuota);
        return success ? Result.success() : Result.error(500, "更新失败");
    }




    @GetMapping("/list")
    public Result<List<ModeratorVO>> getAllModerators(
            @RequestParam(required = false) String status) {

        List<ModeratorVO> moderators = moderatorManagementService.getAllModerators(status);
        return Result.success(moderators);
    }




    @GetMapping("/page")
    public Result<IPage<ModeratorVO>> getModeratorPage(
            @RequestParam(required = false) String status,
            PageQuery pageQuery) {

        IPage<ModeratorVO> page = moderatorManagementService.getModeratorPage(status, pageQuery);
        return Result.success(page);
    }




    @GetMapping("/detail/{userId}")
    public Result<ModeratorVO> getModeratorDetail(@PathVariable Long userId) {
        ModeratorVO moderator = moderatorManagementService.getModeratorDetail(userId);
        return moderator != null ? Result.success(moderator) : Result.error(404, "审核员不存在");
    }




    @PostMapping("/reset-quota/{userId}")
    public Result<Void> resetTodayQuota(@PathVariable Long userId) {
        moderatorManagementService.resetTodayQuota(userId);
        return Result.success();
    }




    @PostMapping("/batch-set")
    public Result<Map<String, Object>> batchSetAsModerator(@RequestBody Map<String, List<Long>> request) {
        List<Long> userIds = request.get("userIds");
        if (userIds == null || userIds.isEmpty()) {
            return Result.error(400, "用户ID列表不能为空");
        }

        int count = moderatorManagementService.batchSetAsModerator(userIds);

        Map<String, Object> result = new HashMap<>();
        result.put("total", userIds.size());
        result.put("success", count);
        return Result.success(result);
    }
}
