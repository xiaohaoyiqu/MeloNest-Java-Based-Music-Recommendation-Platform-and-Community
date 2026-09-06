


package com.haoran.music.controller;

import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.Result;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.ModerationAppealService;
import com.haoran.music.common.util.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/moderation/appeal")
public class ModerationAppealController {

    @Autowired
    private ModerationAppealService appealService;

    @PostMapping("/submit")
    @ApiLog("提交审核申诉")
    public Result<Long> submitAppeal(@RequestBody AppealSubmitDTO dto,
                                     HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (dto == null || dto.getModerationId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "审核记录ID不能为空");
        }

        log.info("Submit moderation appeal: moderationId={}, userId={}", dto.getModerationId(), userId);
        if (ObjectUtils.isNotEmpty(dto.getAttachments())) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "新审核申诉不再接受附件URL，请先上传私有附件并提交attachmentAssetIds");
        }
        Long appealId = appealService.submitAppealWithAssets(
                dto.getModerationId(),
                userId,
                dto.getAppealReason(),
                dto.getAppealContent(),
                dto.getAttachmentAssetIds()
        );
        return Result.success(appealId);
    }

    @GetMapping("/can-appeal/{moderationId}")
    @ApiLog("检查审核申诉资格")
    public Result<Boolean> canAppeal(@PathVariable Long moderationId,
                                     HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        return Result.success(appealService.canAppeal(userId, moderationId));
    }








    @GetMapping("/detail/{appealId}")
    @ApiLog("获取审核申诉详情")
    public Result<Map<String, Object>> getAppealDetail(@PathVariable Long appealId,
                                                       HttpServletRequest request) {
        return Result.success(appealService.getAppealDetail(appealId, getCurrentUserId(request)));
    }

    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/process/{appealId}")
    @ApiLog("处理审核申诉")
    public Result<Void> processAppeal(@PathVariable Long appealId,
                                      @RequestParam Integer decision,
                                      @RequestParam(required = false) String decisionReason,
                                      HttpServletRequest request) {
        Long reviewerId = getCurrentUserId(request);
        log.info("Process moderation appeal: appealId={}, decision={}, reviewerId={}",
                appealId, decision, reviewerId);
        appealService.processAppeal(appealId, reviewerId, decision, decisionReason);
        return Result.success();
    }

    private Long getCurrentUserId(HttpServletRequest request) {
        Object value = request.getAttribute("userId");
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                throw new BusinessException(ResultCode.UNAUTHORIZED);
            }
        }
        throw new BusinessException(ResultCode.UNAUTHORIZED);
    }

    public static class AppealSubmitDTO {
        private Long moderationId;
        private String appealReason;
        private String appealContent;
        private String attachments;
        private List<Long> attachmentAssetIds;

        public Long getModerationId() {
            return moderationId;
        }

        public void setModerationId(Long moderationId) {
            this.moderationId = moderationId;
        }

        public String getAppealReason() {
            return appealReason;
        }

        public void setAppealReason(String appealReason) {
            this.appealReason = appealReason;
        }

        public String getAppealContent() {
            return appealContent;
        }

        public void setAppealContent(String appealContent) {
            this.appealContent = appealContent;
        }

        public String getAttachments() {
            return attachments;
        }

        public void setAttachments(String attachments) {
            this.attachments = attachments;
        }

        public List<Long> getAttachmentAssetIds() {
            return attachmentAssetIds;
        }

        public void setAttachmentAssetIds(List<Long> attachmentAssetIds) {
            this.attachmentAssetIds = attachmentAssetIds;
        }
    }
}
