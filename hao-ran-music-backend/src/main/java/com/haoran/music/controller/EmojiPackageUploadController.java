   
                      
   

package com.haoran.music.controller;

import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.aspect.RateLimit;
import com.haoran.music.common.aspect.RateLimitScope;
import com.haoran.music.common.result.Result;
import com.haoran.music.service.EmojiPackageUploadService;
import com.haoran.music.vo.EmojiUploadBatchResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/emoji/package")
public class EmojiPackageUploadController {

    private final EmojiPackageUploadService uploadService;

    public EmojiPackageUploadController(EmojiPackageUploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping("/{packageId}/emojis/upload")
    @ApiLog("确认上传表情")
    @RateLimit(maxRequests = 6, timeWindowSeconds = 60, scope = RateLimitScope.USER,
            operation = "emojiPackageUpload", captchaBypass = false, failClosed = true,
            message = "上传太频繁，请稍后再试")
    public Result<EmojiUploadBatchResult> upload(
            @PathVariable Long packageId,
            @RequestParam("files") List<MultipartFile> files,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(uploadService.upload(packageId, files, idempotencyKey, userId));
    }
}
