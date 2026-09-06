   
                      
   

package com.haoran.music.controller;

import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.result.Result;
import com.haoran.music.common.util.EmojiUploadUtil;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.dto.decoration.DecorationCreatorRequest;
import com.haoran.music.entity.DecorationConfig;
import com.haoran.music.enums.UserRole;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.DecorationCreatorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/decoration/creator")
public class DecorationCreatorController {

    private final DecorationCreatorService decorationCreatorService;
    private final EmojiUploadUtil imageUploadUtil;
    private final UserMapper userMapper;

    public DecorationCreatorController(DecorationCreatorService decorationCreatorService,
                                       EmojiUploadUtil imageUploadUtil,
                                       UserMapper userMapper) {
        this.decorationCreatorService = decorationCreatorService;
        this.imageUploadUtil = imageUploadUtil;
        this.userMapper = userMapper;
    }

    @GetMapping("/my")
    public Result<List<DecorationConfig>> getMyDecorations(
            @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) return Result.error(401, "请先登录");
        return Result.success(decorationCreatorService.getMyDecorations(userId));
    }

    @GetMapping("/{decorationId}/review-detail")
    @RequireRole({UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN})
    public Result<DecorationConfig> getReviewDetail(@PathVariable Long decorationId) {
        return Result.success(decorationCreatorService.getForReview(decorationId));
    }

    @PostMapping
    @ApiLog("创建装饰草稿")
    public Result<Long> createDraft(@Valid @RequestBody DecorationCreatorRequest input,
                                    @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) return Result.error(401, "请先登录");
        return Result.success(decorationCreatorService.createDraft(input, userId));
    }

    @PutMapping("/{decorationId}")
    @ApiLog("修改装饰草稿")
    public Result<Void> updateDraft(@PathVariable Long decorationId,
                                    @Valid @RequestBody DecorationCreatorRequest input,
                                    @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) return Result.error(401, "请先登录");
        decorationCreatorService.updateDraft(decorationId, input, userId);
        return Result.success();
    }

    @PostMapping("/{decorationId}/submit")
    @ApiLog("提交装饰审核")
    public Result<Void> submit(@PathVariable Long decorationId,
                               @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) return Result.error(401, "请先登录");
        decorationCreatorService.submit(decorationId, userId);
        return Result.success();
    }

    @PostMapping("/upload")
    @ApiLog("上传装饰素材")
    public Result<String> upload(@RequestParam("file") MultipartFile file,
                                 @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) return Result.error(401, "请先登录");
        if (file == null || file.isEmpty()) return Result.error(400, "文件不能为空");
        try {
            UserAccountStatusUtil.requireCanInteract(userMapper.selectById(userId), "上传装饰素材");
            return Result.successData(imageUploadUtil.uploadEmoji(file, userId, "decoration"));
        } catch (IllegalArgumentException | SecurityException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("event=decoration_creator_upload_failed errorType={}", e.getClass().getSimpleName());
            return Result.error(500, "上传失败，请稍后重试");
        }
    }
}
