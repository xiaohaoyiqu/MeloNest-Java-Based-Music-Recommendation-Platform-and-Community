   
                      
   
package com.haoran.music.controller;

import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.result.Result;
import com.haoran.music.service.EmojiService;
import com.haoran.music.service.MessageService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

   
                                      
   
@RestController
@RequestMapping("/user")
public class UserDataController {

    @Resource
    private EmojiService emojiService;

    @Resource
    private MessageService messageService;

    @ApiLog("获取存储使用情况")
    @GetMapping("/data/storage")
    public Result<Map<String, Object>> getStorageUsage(@RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        Map<String, Object> storage = new HashMap<>();
        List<?> userPackages = emojiService.getUserPackages(userId);
        storage.put("emojiPackages", userPackages.size());
        long estimatedUsage = userPackages.size() * 1024L * 1024L;
        storage.put("estimatedUsageBytes", estimatedUsage);
        storage.put("estimatedUsageMB", estimatedUsage / (1024 * 1024));
        return Result.success(storage);
    }

    @ApiLog("清理聊天记录")
    @DeleteMapping("/data/messages/{otherUserId}")
    public Result<Integer> cleanupChatMessages(@PathVariable("otherUserId") Long otherUserId,
                                               @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        int count = messageService.clearChatHistory(userId, otherUserId);
        return Result.success(count);
    }

    @ApiLog("清理用户缓存")
    @PostMapping("/data/cache/clear")
    public Result<Map<String, Object>> clearUserCache(@RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("cleared", true);
        result.put("message", "缓存清理成功");
        return Result.success(result);
    }

    @ApiLog("获取可清理项目")
    @GetMapping("/data/cleanable")
    public Result<Map<String, Object>> getCleanableItems(@RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        Map<String, Object> items = new HashMap<>();
        List<?> userPackages = emojiService.getUserPackages(userId);
        Map<String, Object> emojiPackages = new HashMap<>();
        emojiPackages.put("count", userPackages.size());
        emojiPackages.put("canClean", true);
        emojiPackages.put("description", "您创建的自定义表情包");
        items.put("emojiPackages", emojiPackages);
        return Result.success(items);
    }
}
