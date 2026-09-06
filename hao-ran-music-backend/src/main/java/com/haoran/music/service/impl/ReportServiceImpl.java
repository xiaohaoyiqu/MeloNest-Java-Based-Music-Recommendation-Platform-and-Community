




package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.haoran.music.common.config.CreditConfig;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.SecurityCheckUtil;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.*;
import com.haoran.music.mapper.ReportMapper;
import com.haoran.music.mapper.ReportRewardMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.PermissionService;
import com.haoran.music.service.ReportService;
import com.haoran.music.service.CreditService;
import com.haoran.music.service.UserPointsService;
import com.haoran.music.service.NotificationService;
import com.haoran.music.service.ReportActionService;
import com.haoran.music.service.PrivateAttachmentService;
import com.haoran.music.enums.PrivateAttachmentPurpose;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.List;




@Slf4j
@Service
public class ReportServiceImpl implements ReportService {

    private final ReportMapper reportMapper;
    private final ReportRewardMapper reportRewardMapper;
    private final UserMapper userMapper;
    private final CreditService creditService;
    private final UserPointsService userPointsService;
    private final CreditConfig creditConfig;
    private final NotificationService notificationService;
    private final PermissionService permissionService;
    private final ReportActionService reportActionService;
    private final PrivateAttachmentService privateAttachmentService;

    public ReportServiceImpl(ReportMapper reportMapper,
                            ReportRewardMapper reportRewardMapper,
                            UserMapper userMapper,
                            CreditService creditService,
                            UserPointsService userPointsService,
                            CreditConfig creditConfig,
                            NotificationService notificationService,
                            PermissionService permissionService,
                            ReportActionService reportActionService,
                            PrivateAttachmentService privateAttachmentService) {
        this.reportMapper = reportMapper;
        this.reportRewardMapper = reportRewardMapper;
        this.userMapper = userMapper;
        this.creditService = creditService;
        this.userPointsService = userPointsService;
        this.creditConfig = creditConfig;
        this.notificationService = notificationService;
        this.permissionService = permissionService;
        this.reportActionService = reportActionService;
        this.privateAttachmentService = privateAttachmentService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitReport(Long reporterId, String targetType, Long targetId,
                                           String reportType, String reason, String description,
                                           String attachmentUrls) {
        return submitReportInternal(reporterId, targetType, targetId, reportType, reason,
                description, attachmentUrls, null);
    }













    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitReportWithAssets(Long reporterId, String targetType, Long targetId,
                                                      String reportType, String reason, String description,
                                                      List<Long> attachmentAssetIds) {
        return submitReportInternal(reporterId, targetType, targetId, reportType, reason,
                description, null, attachmentAssetIds);
    }














    private Map<String, Object> submitReportInternal(Long reporterId, String targetType, Long targetId,
                                                     String reportType, String reason, String description,
                                                     String attachmentUrls, List<Long> attachmentAssetIds) {

        User reporter = userMapper.selectById(reporterId);
        if (ObjectUtils.isEmpty(reporter)) {
            throw new BusinessException("用户不存在");
        }


        if (checkReportFrequency(reporterId)) {
            throw new BusinessException("举报过于频繁，请稍后再试");
        }

        targetType = targetType == null ? null : targetType.trim().toLowerCase();
        reportType = reportType == null ? null : reportType.trim().toLowerCase();

        if (!isValidTargetType(targetType)) {
            throw new BusinessException("不支持的目标类型");
        }
        if (!isValidReportType(reportType)) {
            throw new BusinessException("不支持的举报类型");
        }
        if (targetId == null || targetId <= 0) {
            throw new BusinessException("举报目标ID不合法");
        }
        if (reason == null || reason.trim().length() < 2 || reason.length() > 500) {
            throw new BusinessException("举报原因长度必须为2到500字");
        }
        if (description != null && description.length() > 2000) {
            throw new BusinessException("举报说明不能超过2000字");
        }
        if (attachmentUrls != null && attachmentUrls.length() > 4096) {
            throw new BusinessException("举报附件引用过长");
        }


        Report report = new Report();
        report.setReporterId(reporterId);
        report.setTargetType(targetType);
        report.setTargetId(targetId);
        report.setReportType(reportType);

        if (ObjectUtils.isNotEmpty(reason)) {
            SecurityCheckUtil.CheckResult reasonCheck = SecurityCheckUtil.checkDescription(reason);
            if (!reasonCheck.isSafe()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, reasonCheck.getMessage());
            }
            report.setReason(SecurityCheckUtil.escapeHtml(reasonCheck.getCleanedValue()));
        } else {
            report.setReason(reason);
        }

        if (ObjectUtils.isNotEmpty(description)) {
            SecurityCheckUtil.CheckResult descriptionCheck = SecurityCheckUtil.checkDescription(description);
            if (!descriptionCheck.isSafe()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, descriptionCheck.getMessage());
            }
            report.setDescription(SecurityCheckUtil.escapeHtml(descriptionCheck.getCleanedValue()));
        } else {
            report.setDescription(description);
        }
        report.setAttachmentUrls(attachmentUrls);
        report.setStatus("pending");
        report.setIsRewarded(0);

        reportMapper.insert(report);

        if (ObjectUtils.isNotEmpty(attachmentAssetIds)) {
            privateAttachmentService.bindAssets(reporterId,
                    PrivateAttachmentPurpose.REPORT_EVIDENCE.name(), attachmentAssetIds,
                    PrivateAttachmentPurpose.REPORT_EVIDENCE.getTargetType(), report.getId());
        }

        log.info("用户提交举报: reporterId={}, targetType={}, targetId={}",
                reporterId, targetType, targetId);

        Map<String, Object> result = new HashMap<>();
        result.put("reportId", report.getId());
        result.put("status", "pending");
        result.put("message", "举报已提交，我们会尽快处理");

        return result;
    }

    @Override
    public Map<String, Object> getMyReports(Long reporterId, String status,
                                           Integer page, Integer size) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null ? 20 : Math.max(1, Math.min(size, 100));
        Page<Report> pageParam = new Page<>(safePage, safeSize);

        LambdaQueryWrapper<Report> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Report::getReporterId, reporterId);

        if (ObjectUtils.isNotEmpty(status)) {
            wrapper.eq(Report::getStatus, status);
        }

        wrapper.orderByDesc(Report::getCreateTime);

        Page<Report> resultPage = reportMapper.selectPage(pageParam, wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("list", resultPage.getRecords());
        result.put("total", resultPage.getTotal());
        result.put("page", safePage);
        result.put("size", safeSize);

        return result;
    }

    @Override
    public Map<String, Object> getPendingReports(Integer page, Integer size) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null ? 20 : Math.max(1, Math.min(size, 100));
        Page<Report> pageParam = new Page<>(safePage, safeSize);

        LambdaQueryWrapper<Report> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Report::getStatus, "pending")
                .orderByAsc(Report::getCreateTime);

        Page<Report> resultPage = reportMapper.selectPage(pageParam, wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("list", resultPage.getRecords());
        result.put("total", resultPage.getTotal());
        result.put("page", safePage);
        result.put("size", safeSize);

        return result;
    }








    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean withdrawReport(Long reportId, Long reporterId) {
        if (ObjectUtils.isEmpty(reportId) || ObjectUtils.isEmpty(reporterId)) {
            throw new BusinessException("举报撤回参数不能为空");
        }
        Report report = reportMapper.selectById(reportId);
        if (ObjectUtils.isEmpty(report)) {
            throw new BusinessException("举报记录不存在");
        }
        if (!reporterId.equals(report.getReporterId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权撤回此举报");
        }
        if ("withdrawn".equals(report.getStatus())) {
            return true;
        }
        if (!"pending".equals(report.getStatus())) {
            throw new BusinessException("只有待审核举报可以撤回");
        }

        int updated = reportMapper.update(null, new UpdateWrapper<Report>()
                .eq("id", reportId)
                .eq("reporter_id", reporterId)
                .eq("status", "pending")
                .set("status", "withdrawn"));
        if (updated != 1) {
            Report current = reportMapper.selectById(reportId);
            if (ObjectUtils.isNotEmpty(current)
                    && reporterId.equals(current.getReporterId())
                    && "withdrawn".equals(current.getStatus())) {
                return true;
            }
            throw new BusinessException("举报状态已变化，无法撤回");
        }
        privateAttachmentService.releaseTargetReferences(
                PrivateAttachmentPurpose.REPORT_EVIDENCE.getTargetType(), reportId);
        log.info("用户撤回举报: reportId={}, reporterId={}", reportId, reporterId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reviewReport(Long reportId, Long reviewerId,
                                           Boolean approved, String reviewReason, String action) {
        if (reportId == null || reviewerId == null || approved == null) {
            throw new BusinessException("举报审核参数不能为空");
        }
        if (reviewReason != null && reviewReason.length() > 1000) {
            throw new BusinessException("举报审核原因不能超过1000字");
        }
        if (action != null && action.length() > 50) {
            throw new BusinessException("举报处理动作过长");
        }
        Report report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException("举报记录不存在");
        }

        if (!"pending".equals(report.getStatus())) {
            throw new BusinessException("举报已处理");
        }

        Map<String, Object> result = new HashMap<>();
        String nextStatus = approved ? "approved" : "rejected";
        String storedAction = approved
                ? (ObjectUtils.isEmpty(action) ? "none" : action.trim().toLowerCase())
                : ("malicious".equalsIgnoreCase(action) ? "malicious" : "none");
        if (approved && !isAllowedApprovedAction(storedAction)) {
            throw new BusinessException("不支持的举报处理动作");
        }
        LocalDateTime reviewTime = LocalDateTime.now();
        int updated = reportMapper.update(null, new UpdateWrapper<Report>()
                .eq("id", reportId)
                .eq("status", "pending")
                .set("status", nextStatus)
                .set("reviewer_id", reviewerId)
                .set("review_time", reviewTime)
                .set("review_result", reviewReason)
                .set("action", storedAction));
        if (updated != 1) {
            throw new BusinessException("举报已被其他审核人处理");
        }
        report.setStatus(nextStatus);
        report.setReviewerId(reviewerId);
        report.setReviewTime(reviewTime);
        report.setReviewResult(reviewReason);
        report.setAction(storedAction);

        if (approved) {


            Map<String, Object> actionResult = reportActionService.executeAction(
                    report.getId(), storedAction, reviewerId);
            if (!Boolean.TRUE.equals(actionResult.get("success"))) {
                throw new BusinessException("举报处理动作执行失败");
            }
            result.put("actionResult", actionResult);


            boolean rewardGranted = grantReportReward(report.getId());
            Integer credit = rewardGranted
                    ? creditService.updateReportCredit(
                            report.getReporterId(),
                            report.getId(),
                            10,
                            "有效举报")
                    : creditService.getUserCredit(report.getReporterId());

            result.put("status", "approved");
            result.put("credit", credit);
            result.put("rewardGranted", rewardGranted);
            result.put("rewardPoints", rewardGranted ? creditConfig.getReportRewardPoints() : 0);
            result.put("message", rewardGranted
                    ? "举报成立，已发放奖励"
                    : "举报成立，账号当前不可领取正向奖励");


            if (notificationService != null) {
                notificationService.sendReportResultNotification(
                    report.getReporterId(),
                    report.getId(),
                    "approved",
                    reviewReason,
                    creditConfig.getReportRewardPoints()
                );
            }

            log.info("举报成立: reportId={}, action={}", reportId, action);

        } else {


            if ("malicious".equals(storedAction)) {

                Integer credit = creditService.updateReportCredit(
                        report.getReporterId(),
                        report.getId(),
                        -creditConfig.getMaliciousReportScore(),
                        "恶意举报"
                );

                result.put("credit", credit);
                result.put("message", "举报不成立，判定为恶意举报，扣除信用分");
            } else {
                result.put("message", "举报不成立");
            }

            log.info("举报不成立: reportId={}, reason={}", reportId, reviewReason);


            if (notificationService != null) {
                notificationService.sendReportResultNotification(
                    report.getReporterId(),
                    report.getId(),
                    "rejected",
                    reviewReason,
                    null
                );
            }
        }

        return result;
    }

    @Override
    public Map<String, Object> getReportDetail(Long reportId) {
        Report report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException("举报记录不存在");
        }

        return buildAdminReportDetail(report);
    }

    @Override
    public Map<String, Object> getReportDetail(Long reportId, Long viewerId) {
        Report report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException("举报记录不存在");
        }

        if (isReportReviewer(viewerId)) {
            return buildAdminReportDetail(report);
        }
        if (viewerId != null && viewerId.equals(report.getReporterId())) {
            return buildUserReportDetail(report);
        }
        throw new BusinessException(ResultCode.FORBIDDEN, "无权查看此举报记录");
    }

    private Map<String, Object> buildAdminReportDetail(Report report) {
        Map<String, Object> result = new HashMap<>();
        result.put("reportId", report.getId());
        result.put("reporterId", report.getReporterId());
        result.put("targetType", report.getTargetType());
        result.put("targetId", report.getTargetId());
        result.put("reportType", report.getReportType());
        result.put("reason", report.getReason());
        result.put("description", report.getDescription());
        result.put("attachmentUrls", report.getAttachmentUrls());
        result.put("attachmentAssetIds", privateAttachmentService.listTargetAssetIds(
                PrivateAttachmentPurpose.REPORT_EVIDENCE.getTargetType(), report.getId()));
        result.put("status", report.getStatus());
        result.put("action", report.getAction());
        result.put("reviewResult", report.getReviewResult());
        result.put("createTime", report.getCreateTime());
        result.put("reviewTime", report.getReviewTime());
        result.put("isRewarded", report.getIsRewarded());

        return result;
    }

    private Map<String, Object> buildUserReportDetail(Report report) {
        Map<String, Object> result = new HashMap<>();
        result.put("reportId", report.getId());
        result.put("targetType", report.getTargetType());
        result.put("targetId", report.getTargetId());
        result.put("reportType", report.getReportType());
        result.put("reason", report.getReason());
        result.put("description", report.getDescription());
        result.put("attachmentUrls", report.getAttachmentUrls());
        result.put("attachmentAssetIds", privateAttachmentService.listTargetAssetIds(
                PrivateAttachmentPurpose.REPORT_EVIDENCE.getTargetType(), report.getId()));
        result.put("status", report.getStatus());
        result.put("createTime", report.getCreateTime());
        result.put("reviewTime", report.getReviewTime());
        result.put("isRewarded", report.getIsRewarded());
        return result;
    }

    @Override
    public Map<String, Object> getTargetReports(String targetType, Long targetId,
                                               Integer page, Integer size) {
        return getTargetReportsInternal(targetType, targetId, page, size, false);
    }

    @Override
    public Map<String, Object> getTargetReports(String targetType, Long targetId,
                                               Integer page, Integer size, Long viewerId) {
        if (!isReportReviewer(viewerId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看目标举报记录");
        }
        return getTargetReportsInternal(targetType, targetId, page, size, true);
    }

    private Map<String, Object> getTargetReportsInternal(String targetType, Long targetId,
                                               Integer page, Integer size, boolean safeDto) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null ? 20 : Math.max(1, Math.min(size, 100));
        Page<Report> pageParam = new Page<>(safePage, safeSize);

        LambdaQueryWrapper<Report> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Report::getTargetType, targetType)
                .eq(Report::getTargetId, targetId)
                .orderByDesc(Report::getCreateTime);

        Page<Report> resultPage = reportMapper.selectPage(pageParam, wrapper);

        Map<String, Object> result = new HashMap<>();
        if (safeDto) {
            List<Map<String, Object>> records = new java.util.ArrayList<>();
            for (Report report : resultPage.getRecords()) {
                records.add(buildAdminReportDetail(report));
            }
            result.put("list", records);
        } else {
            result.put("list", resultPage.getRecords());
        }
        result.put("total", resultPage.getTotal());
        result.put("page", safePage);
        result.put("size", safeSize);

        return result;
    }

    private boolean isReportReviewer(Long viewerId) {
        return viewerId != null && permissionService != null && permissionService.isModerator(viewerId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean grantReportReward(Long reportId) {
        Report report = reportMapper.selectById(reportId);
        if (report == null) {
            return false;
        }
        if (!"approved".equals(report.getStatus())) {
            throw new BusinessException("只有已成立举报可以发放奖励");
        }

        if (!UserAccountStatusUtil.canInteract(report.getReporterId(), userMapper::selectById)) {
            log.info("跳过异常账号举报奖励: reportId={}, userId={}", reportId, report.getReporterId());
            return false;
        }

        int claimed = reportMapper.update(null, new UpdateWrapper<Report>()
                .eq("id", reportId)
                .eq("status", "approved")
                .and(wrapper -> wrapper.eq("is_rewarded", 0).or().isNull("is_rewarded"))
                .set("is_rewarded", 1));
        if (claimed != 1) {
            Report current = reportMapper.selectById(reportId);
            return current != null && Integer.valueOf(1).equals(current.getIsRewarded());
        }


        userPointsService.addPoints(
                report.getReporterId(),
                "reward",
                creditConfig.getReportRewardPoints(),
                "有效举报奖励",
                reportId,
                "report"
        );


        ReportReward reward = new ReportReward();
        reward.setUserId(report.getReporterId());
        reward.setReportId(reportId);
        reward.setRewardType("points");
        reward.setRewardAmount(creditConfig.getReportRewardPoints());
        reward.setRewardDescription("有效举报奖励");
        reward.setIsGranted(1);
        reward.setGrantedTime(LocalDateTime.now());

        reportRewardMapper.insert(reward);

        log.info("发放举报奖励: reportId={}, userId={}, points={}",
                reportId, report.getReporterId(), creditConfig.getReportRewardPoints());

        return true;
    }




    @Override
    public Integer getReportCredit(Long userId) {
        return creditService.getUserCredit(userId);
    }

    @Override
    public Integer addReportCredit(Long userId, Long reportId, Integer score, String reason) {
        return creditService.updateReportCredit(userId, reportId, score, reason);
    }

    @Override
    public Integer deductReportCredit(Long userId, Long reportId, Integer score, String reason) {
        return creditService.updateReportCredit(userId, reportId, -score, reason);
    }

    @Override
    public Integer resetReportCredit() {
        return creditService.resetAllCredits();
    }

    @Override
    public Map<String, Object> getReportStatistics(Long userId) {
        List<Report> allReports = reportMapper.selectList(
                new LambdaQueryWrapper<Report>()
                        .eq(Report::getReporterId, userId)
        );

        int totalCount = allReports.size();
        int approvedCount = (int) allReports.stream()
                .filter(r -> "approved".equals(r.getStatus()))
                .count();
        int rejectedCount = (int) allReports.stream()
                .filter(r -> "rejected".equals(r.getStatus()))
                .count();
        int pendingCount = (int) allReports.stream()
                .filter(r -> "pending".equals(r.getStatus()))
                .count();

        Integer credit = getReportCredit(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("totalCount", totalCount);
        result.put("approvedCount", approvedCount);
        result.put("rejectedCount", rejectedCount);
        result.put("pendingCount", pendingCount);
        result.put("reportCredit", credit);

        return result;
    }

    @Override
    public Boolean checkReportFrequency(Long userId) {

        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);

        LambdaQueryWrapper<Report> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Report::getReporterId, userId)
                .ge(Report::getCreateTime, oneHourAgo);

        Long count = reportMapper.selectCount(wrapper);


        return count >= 5;
    }

    @Override
    public Integer getReportRewardPoints() {
        return creditConfig.getReportRewardPoints();
    }






    private Boolean isValidTargetType(String targetType) {
        return "song".equals(targetType)
                || "mv".equals(targetType)
                || "album".equals(targetType)
                || "playlist".equals(targetType)
                || "comment".equals(targetType)
                || "post".equals(targetType)
                || "marketplace_item".equals(targetType)
                || "user".equals(targetType);
    }

    private boolean isValidReportType(String reportType) {
        return "inappropriate".equals(reportType)
                || "copyright".equals(reportType)
                || "spam".equals(reportType)
                || "wrong_info".equals(reportType)
                || "illegal".equals(reportType)
                || "porn".equals(reportType)
                || "abuse".equals(reportType)
                || "fake".equals(reportType)
                || "other".equals(reportType);
    }

    private boolean isAllowedApprovedAction(String action) {
        return "warning".equals(action)
                || "hidden".equals(action)
                || "deleted".equals(action)
                || "banned".equals(action)
                || "none".equals(action);
    }
}
