


package com.haoran.music.controller;

import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.result.Result;
import com.haoran.music.service.UserEquipmentConfigService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;




@RestController
@RequestMapping("/user")
public class UserEquipmentController {

    @Resource
    private UserEquipmentConfigService equipmentConfigService;

    @ApiLog("获取用户装备配置")
    @GetMapping("/equipment/config")
    public Result<Map<String, Object>> getEquipmentConfig(
            @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(equipmentConfigService.getUserEquipmentConfig(userId));
    }

    @ApiLog("更新用户装备配置")
    @PutMapping("/equipment/config")
    public Result<Void> updateEquipmentConfig(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestBody Map<String, Object> config) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        equipmentConfigService.updateEquipmentConfig(userId, config);
        return Result.success();
    }

    @ApiLog("装备装饰")
    @PostMapping("/equipment/equip")
    public Result<Void> equipDecoration(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestParam String decorationType,
            @RequestParam String decorationId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        equipmentConfigService.equipDecoration(userId, decorationType, decorationId);
        return Result.success();
    }

    @ApiLog("卸载装饰")
    @PostMapping("/equipment/unequip")
    public Result<Void> unequipDecoration(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestParam String decorationType) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        equipmentConfigService.unequipDecoration(userId, decorationType);
        return Result.success();
    }
}
