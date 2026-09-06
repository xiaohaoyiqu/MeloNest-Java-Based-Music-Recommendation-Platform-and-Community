   
                      
                       
   

package com.haoran.music.controller;

import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.result.Result;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.FeedbackService;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.List;

   
          
                                  
   
@Slf4j
@RestController
@RequestMapping("/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

       
           
       
    @ApiLog("提交反馈")
    @PostMapping("/submit")
    public Result submitFeedback(HttpServletRequest request,
                               @RequestParam String feedbackType,
                               @RequestParam(required = false) Long orderId,
                               @RequestParam(required = false) String orderType,
                               @RequestParam String title,
                               @RequestParam String content,
                               @RequestParam(required = false) String attachmentUrls,
                               @RequestParam(required = false) List<Long> attachmentAssetIds) {
        Long userId = (Long) request.getAttribute("userId");
        if (ObjectUtils.isNotEmpty(attachmentUrls)) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "新反馈不再接受附件URL，请先上传私有附件并提交attachmentAssetIds");
        }
        return Result.success(feedbackService.submitFeedbackWithAssets(userId, feedbackType,
                orderId, orderType, title, content, attachmentAssetIds));
    }

       
               
       
    @ApiLog("获取反馈列表")
    @GetMapping("/my")
    public Result getMyFeedbacks(HttpServletRequest request,
                               @RequestParam(required = false) String feedbackType,
                               @RequestParam(required = false) String status,
                               @RequestParam(defaultValue = "1") Integer page,
                               @RequestParam(defaultValue = "20") Integer size) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(feedbackService.getMyFeedbacks(userId, feedbackType,
                status, page, size));
    }

       
             
       
    @ApiLog("获取反馈统计")
    @GetMapping("/statistics")
    public Result getFeedbackStatistics(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(feedbackService.getFeedbackStatistics(userId));
    }

       
             
       
    @ApiLog("获取反馈详情")
    @GetMapping("/{feedbackId}")
    public Result getFeedbackDetail(HttpServletRequest request,
                                    @PathVariable Long feedbackId) {
        Long currentUserId = getAuthenticatedUserId(request);
        if (currentUserId == null) {
            return Result.error(401, "Unauthorized");
        }
        return Result.success(feedbackService.getFeedbackDetail(feedbackId, currentUserId));
    }

       
                     
       
    @ApiLog("获取待处理反馈")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/pending")
    public Result getPendingFeedbacks(@RequestParam(required = false) String feedbackType,
                                      @RequestParam(defaultValue = "1") Integer page,
                                      @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(feedbackService.getPendingFeedbacks(feedbackType, page, size));
    }

       
                
       
    @ApiLog("处理反馈")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/{feedbackId}/handle")
    public Result handleFeedback(HttpServletRequest request,
                               @PathVariable Long feedbackId,
                               @RequestParam String status,
                               @RequestParam(required = false) String handleResult) {
        Long handlerId = getAuthenticatedUserId(request);
        if (handlerId == null) {
            return Result.error(401, "Unauthorized");
        }
        return Result.success(feedbackService.handleFeedback(feedbackId, handlerId,
                status, handleResult));
    }

       
                
       
    @ApiLog("关闭反馈")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/{feedbackId}/close")
    public Result closeFeedback(HttpServletRequest request,
                              @PathVariable Long feedbackId,
                              @RequestParam(required = false) String closeReason) {
        Long handlerId = getAuthenticatedUserId(request);
        if (handlerId == null) {
            return Result.error(401, "Unauthorized");
        }
        feedbackService.getFeedbackDetail(feedbackId, handlerId);
        return Result.success(feedbackService.closeFeedback(feedbackId, handlerId, closeReason));
    }

       
                     
       
    @ApiLog("从反馈创建退款")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/{feedbackId}/create-refund")
    public Result createRefundFromFeedback(HttpServletRequest request,
                                         @PathVariable Long feedbackId) {
        Long operatorId = getAuthenticatedUserId(request);
        if (operatorId == null) {
            return Result.error(401, "Unauthorized");
        }
        return Result.success(feedbackService.createRefundFromFeedback(feedbackId, operatorId));
    }

       
                  
       
    @ApiLog("批量处理反馈")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/batch-handle")
    public Result batchHandleFeedback(HttpServletRequest request,
                                   @RequestParam Long[] feedbackIds,
                                   @RequestParam String status,
                                   @RequestParam(required = false) String handleResult) {
        Long handlerId = getAuthenticatedUserId(request);
        if (handlerId == null) {
            return Result.error(401, "Unauthorized");
        }
        return Result.success(feedbackService.batchHandleFeedback(feedbackIds,
                handlerId, status, handleResult));
    }

    private Long getAuthenticatedUserId(HttpServletRequest request) {
        return parseUserId(request.getAttribute("userId"));
    }

    private Long parseUserId(Object userId) {
        if (userId == null) {
            return null;
        }
        if (userId instanceof Long) {
            return (Long) userId;
        }
        if (userId instanceof Number) {
            return ((Number) userId).longValue();
        }
        try {
            String value = String.valueOf(userId).trim();
            return value.isEmpty() ? null : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
