   
                      
                        
   

package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.SecurityCheckUtil;
import com.haoran.music.entity.*;
import com.haoran.music.mapper.*;
import com.haoran.music.service.FeedbackService;
import com.haoran.music.service.PermissionService;
import com.haoran.music.service.RefundService;
import com.haoran.music.service.NotificationService;
import com.haoran.music.service.PrivateAttachmentService;
import com.haoran.music.enums.PrivateAttachmentPurpose;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.Set;

   
           
   
@Slf4j
@Service
public class FeedbackServiceImpl implements FeedbackService {

    private static final int MAX_BATCH_HANDLE_SIZE = 50;
    private static final int MAX_ATTACHMENT_URLS_LENGTH = 4096;
    private static final Set<String> FEEDBACK_TYPES = new HashSet<>(Arrays.asList(
            "issue", "suggestion", "content", "accessibility", "complaint", "refund"));

    private final UserFeedbackMapper userFeedbackMapper;
    private final UserMapper userMapper;
    private final RefundService refundService;
    private final NotificationService notificationService;
    private final PermissionService permissionService;
    private final PaymentOrderMapper paymentOrderMapper;
    private final PrivateAttachmentService privateAttachmentService;

    public FeedbackServiceImpl(UserFeedbackMapper userFeedbackMapper,
                              UserMapper userMapper,
                               RefundService refundService,
                               NotificationService notificationService,
                               PermissionService permissionService,
                               PaymentOrderMapper paymentOrderMapper,
                               PrivateAttachmentService privateAttachmentService) {
        this.userFeedbackMapper = userFeedbackMapper;
        this.userMapper = userMapper;
        this.refundService = refundService;
        this.notificationService = notificationService;
        this.permissionService = permissionService;
        this.paymentOrderMapper = paymentOrderMapper;
        this.privateAttachmentService = privateAttachmentService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitFeedback(Long userId, String feedbackType,
                                             Long orderId, String orderType,
                                             String title, String content,
                                             String attachmentUrls) {
        return submitFeedbackInternal(userId, feedbackType, orderId, orderType, title,
                content, attachmentUrls, null);
    }

       
                    
      
                         
                               
                          
                            
                      
                        
                                         
                   
       
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitFeedbackWithAssets(Long userId, String feedbackType,
                                                        Long orderId, String orderType,
                                                        String title, String content,
                                                        List<Long> attachmentAssetIds) {
        return submitFeedbackInternal(userId, feedbackType, orderId, orderType, title,
                content, null, attachmentAssetIds);
    }

       
                             
      
                         
                               
                          
                            
                      
                        
                                  
                                       
                   
       
    private Map<String, Object> submitFeedbackInternal(Long userId, String feedbackType,
                                                       Long orderId, String orderType,
                                                       String title, String content,
                                                       String attachmentUrls,
                                                       List<Long> attachmentAssetIds) {
               
        User user = userMapper.selectById(userId);
        if (ObjectUtils.isEmpty(user)) {
            throw new BusinessException("用户不存在");
        }

        String normalizedFeedbackType = normalizeFeedbackType(feedbackType);
        if (!FEEDBACK_TYPES.contains(normalizedFeedbackType)) {
            throw new BusinessException("不支持的反馈类型");
        }
        if (attachmentUrls != null && attachmentUrls.length() > MAX_ATTACHMENT_URLS_LENGTH) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "附件地址总长度不能超过4096个字符");
        }
        if (attachmentUrls != null) {
            String normalizedAttachments = attachmentUrls.toLowerCase(java.util.Locale.ROOT);
            if (normalizedAttachments.contains("data:") || normalizedAttachments.contains("file:")
                    || normalizedAttachments.contains("..\\") || normalizedAttachments.contains("../")
                    || attachmentUrls.indexOf('\r') >= 0 || attachmentUrls.indexOf('\n') >= 0) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "附件地址不合法");
            }
        }

        String authoritativeOrderType = null;
        if (orderId != null) {
            PaymentOrder order = paymentOrderMapper.selectById(orderId);
            if (order == null || !userId.equals(order.getUserId())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权关联此订单");
            }
            authoritativeOrderType = order.getBusinessType();
        } else if ("refund".equals(normalizedFeedbackType)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "退款反馈必须关联本人订单");
        }

                 
        UserFeedback feedback = new UserFeedback();
        feedback.setUserId(userId);
        feedback.setFeedbackType(normalizedFeedbackType);
        feedback.setOrderId(orderId);
        feedback.setOrderType(authoritativeOrderType);
                                
        if (ObjectUtils.isNotEmpty(title)) {
            SecurityCheckUtil.CheckResult titleCheck = SecurityCheckUtil.checkTitle(title);
            if (!titleCheck.isSafe()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, titleCheck.getMessage());
            }
            feedback.setTitle(SecurityCheckUtil.escapeHtml(titleCheck.getCleanedValue()));
        } else {
            feedback.setTitle(title);
        }
                                  
        if (ObjectUtils.isNotEmpty(content)) {
            SecurityCheckUtil.CheckResult contentCheck = SecurityCheckUtil.checkDescription(content);
            if (!contentCheck.isSafe()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, contentCheck.getMessage());
            }
            feedback.setContent(SecurityCheckUtil.escapeHtml(contentCheck.getCleanedValue()));
        } else {
            feedback.setContent(content);
        }
        feedback.setAttachmentUrls(attachmentUrls);
        feedback.setStatus("pending");

        userFeedbackMapper.insert(feedback);

        if (ObjectUtils.isNotEmpty(attachmentAssetIds)) {
            privateAttachmentService.bindAssets(userId,
                    PrivateAttachmentPurpose.FEEDBACK_ATTACHMENT.name(), attachmentAssetIds,
                    PrivateAttachmentPurpose.FEEDBACK_ATTACHMENT.getTargetType(), feedback.getId());
        }

        log.info("event=feedback_submitted userId={} feedbackId={} feedbackType={} attachmentCount={}",
                userId, feedback.getId(), normalizedFeedbackType,
                attachmentAssetIds == null ? 0 : attachmentAssetIds.size());

        Map<String, Object> result = new HashMap<>();
        result.put("feedbackId", feedback.getId());
        result.put("status", "pending");
        result.put("message", "来信已经送达，处理进度会保留在我的反馈中");

        return result;
    }

    @Override
    public Map<String, Object> getMyFeedbacks(Long userId, String feedbackType,
                                             String status, Integer page, Integer size) {
        Page<UserFeedback> pageParam = new Page<>(page, size);

        LambdaQueryWrapper<UserFeedback> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFeedback::getUserId, userId);

        String normalizedFeedbackType = normalizeOptionalFeedbackType(feedbackType);
        if (normalizedFeedbackType != null) {
            wrapper.eq(UserFeedback::getFeedbackType, normalizedFeedbackType);
        }
        if (ObjectUtils.isNotEmpty(status)) {
            wrapper.eq(UserFeedback::getStatus, status);
        }

        wrapper.orderByDesc(UserFeedback::getCreateTime);

        Page<UserFeedback> resultPage = userFeedbackMapper.selectPage(pageParam, wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("list", resultPage.getRecords());
        result.put("total", resultPage.getTotal());
        result.put("page", page);
        result.put("size", size);

        return result;
    }

    @Override
    public Map<String, Object> getPendingFeedbacks(String feedbackType, Integer page, Integer size) {
        Page<UserFeedback> pageParam = new Page<>(page, size);

        LambdaQueryWrapper<UserFeedback> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFeedback::getStatus, "pending");

        String normalizedFeedbackType = normalizeOptionalFeedbackType(feedbackType);
        if (normalizedFeedbackType != null) {
            wrapper.eq(UserFeedback::getFeedbackType, normalizedFeedbackType);
        }

        wrapper.orderByAsc(UserFeedback::getCreateTime);

        Page<UserFeedback> resultPage = userFeedbackMapper.selectPage(pageParam, wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("list", resultPage.getRecords());
        result.put("total", resultPage.getTotal());
        result.put("page", page);
        result.put("size", size);

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> handleFeedback(Long feedbackId, Long handlerId,
                                            String status, String handleResult) {
        UserFeedback feedback = userFeedbackMapper.selectById(feedbackId);
        if (feedback == null) {
            throw new BusinessException("反馈不存在");
        }
        validateHandleStatus(status);

        if (status.equals(feedback.getStatus())) {
            return handledFeedbackResult(feedbackId, status);
        }
        if (!canTransitionFeedback(feedback.getStatus(), status)) {
            throw new BusinessException("当前反馈状态不可执行该操作");
        }

        if (userFeedbackMapper.transitionStatus(feedbackId, feedback.getStatus(), status,
                handlerId, LocalDateTime.now(), handleResult) != 1) {
            throw new BusinessException("反馈已被其他操作处理");
        }

        log.info("处理反馈: feedbackId={}, status={}, handlerId={}",
                feedbackId, status, handlerId);

                     
        if (notificationService != null) {
            notificationService.sendFeedbackResultNotification(
                feedback.getUserId(),
                feedbackId,
                status,
                handleResult
            );
        }
        return handledFeedbackResult(feedbackId, status);
    }

    @Override
    public Map<String, Object> getFeedbackDetail(Long feedbackId) {
        UserFeedback feedback = userFeedbackMapper.selectById(feedbackId);
        if (feedback == null) {
            throw new BusinessException("反馈不存在");
        }

        return buildAdminFeedbackDetail(feedback);
    }

    @Override
    public Map<String, Object> getFeedbackDetail(Long feedbackId, Long viewerId) {
        UserFeedback feedback = userFeedbackMapper.selectById(feedbackId);
        if (feedback == null) {
            throw new BusinessException("反馈不存在");
        }

        if (viewerId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        if (viewerId.equals(feedback.getUserId())) {
            return buildUserFeedbackDetail(feedback);
        }
        if (permissionService != null && permissionService.isAdmin(viewerId)) {
            return buildAdminFeedbackDetail(feedback);
        }
        throw new BusinessException(ResultCode.FORBIDDEN, "无权查看此反馈");
    }

    private Map<String, Object> buildAdminFeedbackDetail(UserFeedback feedback) {
        Map<String, Object> result = new HashMap<>();
        result.put("feedbackId", feedback.getId());
        result.put("userId", feedback.getUserId());
        result.put("feedbackType", feedback.getFeedbackType());
        result.put("orderId", feedback.getOrderId());
        result.put("orderType", feedback.getOrderType());
        result.put("title", feedback.getTitle());
        result.put("content", feedback.getContent());
        result.put("attachmentUrls", feedback.getAttachmentUrls());
        result.put("attachmentAssetIds", privateAttachmentService.listTargetAssetIds(
                PrivateAttachmentPurpose.FEEDBACK_ATTACHMENT.getTargetType(), feedback.getId()));
        result.put("status", feedback.getStatus());
        result.put("handlerId", feedback.getHandlerId());
        result.put("handleTime", feedback.getHandleTime());
        result.put("handleResult", feedback.getHandleResult());
        result.put("createTime", feedback.getCreateTime());

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean closeFeedback(Long feedbackId, Long handlerId, String closeReason) {
        UserFeedback feedback = userFeedbackMapper.selectById(feedbackId);
        if (feedback == null) {
            throw new BusinessException("反馈不存在");
        }

        if ("closed".equals(feedback.getStatus())) {
            return true;
        }
        if (!canTransitionFeedback(feedback.getStatus(), "closed")) {
            throw new BusinessException("当前反馈状态不可关闭");
        }
        if (userFeedbackMapper.transitionStatus(feedbackId, feedback.getStatus(), "closed",
                handlerId, LocalDateTime.now(), "关闭原因: " + closeReason) != 1) {
            throw new BusinessException("反馈已被其他操作处理");
        }

        privateAttachmentService.releaseTargetReferences(
                PrivateAttachmentPurpose.FEEDBACK_ATTACHMENT.getTargetType(), feedbackId);
        log.info("关闭反馈: feedbackId={}, handlerId={}", feedbackId, handlerId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRefundFromFeedback(Long feedbackId, Long operatorId) {
        UserFeedback feedback = userFeedbackMapper.selectById(feedbackId);
        if (feedback == null) {
            throw new BusinessException("反馈不存在");
        }

        if (!"refund".equals(feedback.getFeedbackType())) {
            throw new BusinessException("该反馈不是退款申请");
        }

        if (feedback.getOrderId() == null) {
            throw new BusinessException("未关联订单");
        }

        if (!"pending".equals(feedback.getStatus())) {
            throw new BusinessException("当前反馈状态不可创建退款");
        }
        if (userFeedbackMapper.transitionStatus(feedbackId, "pending", "processing",
                operatorId, LocalDateTime.now(), "已创建退款申请") != 1) {
            throw new BusinessException("反馈已被其他操作处理");
        }

                                 
        Map<String, Object> refundResult = refundService.applyRefund(
                feedback.getUserId(),
                feedback.getOrderId(),
                feedback.getOrderType(),
                feedback.getTitle(),
                feedback.getContent()
        );
        Object refundId = refundResult == null ? null : refundResult.get("refundId");
        if (refundId == null) {
            throw new IllegalStateException("退款申请未返回退款ID");
        }

        log.info("从反馈创建退款: feedbackId={}, operatorId={}", feedbackId, operatorId);

        if (refundId instanceof Number) {
            return ((Number) refundId).longValue();
        }
        return Long.valueOf(String.valueOf(refundId));
    }

    @Override
    public Map<String, Object> getFeedbackStatistics(Long userId) {
        Map<String, Object> stats = userFeedbackMapper.selectUserFeedbackStats(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("totalCount", numberValue(stats, "totalCount"));
        result.put("pendingCount", numberValue(stats, "pendingCount"));
        result.put("resolvedCount", numberValue(stats, "resolvedCount"));

        return result;
    }

    private Map<String, Object> buildUserFeedbackDetail(UserFeedback feedback) {
        Map<String, Object> result = new HashMap<>();
        result.put("feedbackId", feedback.getId());
        result.put("userId", feedback.getUserId());
        result.put("feedbackType", feedback.getFeedbackType());
        result.put("orderId", feedback.getOrderId());
        result.put("orderType", feedback.getOrderType());
        result.put("title", feedback.getTitle());
        result.put("content", feedback.getContent());
        result.put("attachmentUrls", feedback.getAttachmentUrls());
        result.put("attachmentAssetIds", privateAttachmentService.listTargetAssetIds(
                PrivateAttachmentPurpose.FEEDBACK_ATTACHMENT.getTargetType(), feedback.getId()));
        result.put("status", feedback.getStatus());
        result.put("handleTime", feedback.getHandleTime());
        result.put("handleResult", feedback.getHandleResult());
        result.put("createTime", feedback.getCreateTime());
        return result;
    }

    @Override
    public Map<String, Object> getAdminFeedbackStatistics(Long handlerId,
                                                         String startDate, String endDate) {
        LocalDateTime startTime = startDate == null ? null : LocalDateTime.parse(startDate + "T00:00:00");
        LocalDateTime endTime = endDate == null ? null : LocalDateTime.parse(endDate + "T23:59:59");
        Map<String, Object> stats = userFeedbackMapper.selectAdminFeedbackStats(handlerId, startTime, endTime);
        long totalCount = numberValue(stats, "totalCount");
        long processedCount = numberValue(stats, "processedCount");

        Map<String, Object> result = new HashMap<>();
        result.put("totalCount", totalCount);
        result.put("processedCount", processedCount);
        result.put("pendingCount", totalCount - processedCount);

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer batchHandleFeedback(Long[] feedbackIds, Long handlerId,
                                      String status, String handleResult) {
        if (feedbackIds == null || feedbackIds.length == 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "反馈ID不能为空");
        }
        if (feedbackIds.length > MAX_BATCH_HANDLE_SIZE) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "单次最多处理50条反馈");
        }
        validateHandleStatus(status);

        int count = 0;

        for (Long feedbackId : feedbackIds) {
            try {
                handleFeedback(feedbackId, handlerId, status, handleResult);
                count++;
            } catch (Exception e) {
                log.error("event=feedback_batch_process_item_failed feedbackId={} errorType={}",
                        feedbackId, e.getClass().getSimpleName());
            }
        }

        log.info("批量处理反馈完成: 总数={}, 成功={}", feedbackIds.length, count);
        return count;
    }

    @Override
    public Map<String, Object> getHotFeedbackIssues(Integer limit) {
        int safeLimit = limit == null || limit <= 0 ? 10 : Math.min(limit, 50);

        Map<String, Object> result = new HashMap<>();
        result.put("limit", safeLimit);
        result.put("issues", userFeedbackMapper.selectHotFeedbackIssues(safeLimit));

        return result;
    }

    private long numberValue(Map<String, Object> row, String key) {
        if (row == null || row.get(key) == null) {
            return 0L;
        }
        Object value = row.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

                                                     

    private String normalizeFeedbackType(String feedbackType) {
        return feedbackType == null ? null : feedbackType.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeOptionalFeedbackType(String feedbackType) {
        if (feedbackType == null || feedbackType.trim().isEmpty()) {
            return null;
        }
        String normalized = normalizeFeedbackType(feedbackType);
        if (!FEEDBACK_TYPES.contains(normalized)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的反馈类型");
        }
        return normalized;
    }

    private void validateHandleStatus(String status) {
        if (!"processing".equals(status) && !"resolved".equals(status) && !"closed".equals(status)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的反馈处理状态");
        }
    }

    private boolean canTransitionFeedback(String currentStatus, String targetStatus) {
        if ("pending".equals(currentStatus)) {
            return "processing".equals(targetStatus)
                    || "resolved".equals(targetStatus)
                    || "closed".equals(targetStatus);
        }
        return "processing".equals(currentStatus)
                && ("resolved".equals(targetStatus) || "closed".equals(targetStatus));
    }

    private Map<String, Object> handledFeedbackResult(Long feedbackId, String status) {
        Map<String, Object> result = new HashMap<>();
        result.put("feedbackId", feedbackId);
        result.put("status", status);
        result.put("message", "反馈已处理");
        return result;
    }
}
