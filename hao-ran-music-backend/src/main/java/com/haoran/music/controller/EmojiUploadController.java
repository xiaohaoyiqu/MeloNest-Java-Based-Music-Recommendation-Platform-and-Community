   
                      
   

package com.haoran.music.controller;

import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.config.MusicUploadConfig;
import com.haoran.music.common.constant.EmojiPackageCapacity;
import com.haoran.music.common.result.Result;
import com.haoran.music.common.util.EmojiUploadUtil;
import com.haoran.music.enums.UserRole;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

                                                                              
@RestController
@RequestMapping("/emoji/upload")
public class EmojiUploadController {

    private final EmojiUploadUtil emojiUploadUtil;
    private final MusicUploadConfig musicUploadConfig;

    public EmojiUploadController(EmojiUploadUtil emojiUploadUtil,
                                 MusicUploadConfig musicUploadConfig) {
        this.emojiUploadUtil = emojiUploadUtil;
        this.musicUploadConfig = musicUploadConfig;
    }

    @GetMapping("/config")
    public Result<Map<String, Object>> getUploadConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("maxFileSize", musicUploadConfig.getEmojiMaxFileSize());
        config.put("maxFileCount", musicUploadConfig.getEmojiMaxFiles());
        config.put("dailyMaxFiles", musicUploadConfig.getEmojiDailyMaxFilesPerUser());
        config.put("dailyMaxBytes", musicUploadConfig.getEmojiDailyMaxBytesPerUser());
        config.put("maxEditablePackages", musicUploadConfig.getEmojiMaxEditablePackagesPerUser());
        config.put("maxPendingPackages", musicUploadConfig.getEmojiMaxPendingPackagesPerUser());
        config.put("allowedTypes", musicUploadConfig.getAllowedImageTypes());
        config.put("allowedExtensions", musicUploadConfig.getAllowedImageExtensions());
        config.put("maxWidth", musicUploadConfig.getEmojiMaxWidth());
        config.put("maxHeight", musicUploadConfig.getEmojiMaxHeight());
        config.put("packageCapacities", EmojiPackageCapacity.SUPPORTED);
        config.put("defaultPackageCapacity", EmojiPackageCapacity.DEFAULT);
        return Result.success(config);
    }

    @PostMapping("/cleanup")
    @ApiLog("清理临时表情文件")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    public Result<Map<String, Object>> cleanupTempFiles(HttpServletRequest request) {
        int count = emojiUploadUtil.cleanupTempFiles();
        Map<String, Object> result = new HashMap<>();
        result.put("cleanedFiles", count);
        result.put("message", "已清理 " + count + " 个临时文件");
        return Result.success(result);
    }

    @DeleteMapping("/image")
    @ApiLog("删除表情图片")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    public Result<Boolean> deleteEmojiImage(@RequestBody Map<String, String> params,
                                            HttpServletRequest request) {
        String imageUrl = params.get("imageUrl");
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            return Result.error(400, "图片路径不能为空");
        }
        return Result.success(emojiUploadUtil.deleteEmoji(imageUrl));
    }
}
