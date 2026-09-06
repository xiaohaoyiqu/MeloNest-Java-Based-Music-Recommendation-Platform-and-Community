


package com.haoran.music.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.context.UserContext;
import com.haoran.music.common.dto.PageResult;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.Result;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.dto.appeal.AppealQueryDTO;
import com.haoran.music.dto.appeal.AppealReviewDTO;
import com.haoran.music.entity.AppealRecord;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.AppealRecordService;
import com.haoran.music.service.AppealService;
import com.haoran.music.vo.appeal.AppealVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/admin/appeal")
@RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
public class AdminAppealController {

    private static final int MAX_BATCH_REVIEW_SIZE = 50;

    @Resource
    private AppealService appealService;

    @Resource
    private AppealRecordService appealRecordService;

    @PostMapping("/list")
    @ApiLog("管理员获取申诉列表")
    public Result<PageResult<AppealVO>> getAppealList(@RequestBody(required = false) AppealQueryDTO dto) {
        IPage<AppealVO> page = appealService.pageAppeals(dto);
        return Result.success(buildPageResult(page));
    }

    @GetMapping("/detail/{appealId}")
    @ApiLog("管理员获取申诉详情")
    public Result<AppealVO> getAppealDetail(@PathVariable Long appealId) {
        return Result.success(appealService.getAppealDetail(appealId));
    }

    @PostMapping("/review")
    @ApiLog("管理员审核申诉")
    public Result<Void> reviewAppeal(@RequestBody AppealReviewDTO dto) {
        appealService.reviewAppeal(getCurrentUserId(), dto);
        return Result.success();
    }

    @PostMapping("/batch/review")
    @ApiLog("管理员批量审核申诉")
    public Result<BatchReviewResult> batchReviewAppeal(
            @RequestBody List<Long> appealIds,
            @RequestParam String reviewResult
    ) {
        if (appealIds == null || appealIds.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "申诉ID列表不能为空");
        }
        if (appealIds.size() > MAX_BATCH_REVIEW_SIZE) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "单次最多审核50条申诉");
        }

        Long reviewerId = getCurrentUserId();
        BatchReviewResult result = new BatchReviewResult();
        result.setTotal(appealIds.size());

        for (Long appealId : appealIds) {
            if (appealId == null) {
                result.getFailedIds().add(null);
                continue;
            }
            try {
                AppealReviewDTO dto = new AppealReviewDTO();
                dto.setAppealId(appealId);
                dto.setReviewResult(reviewResult);
                dto.setReviewRemark("批量审核");
                appealService.reviewAppeal(reviewerId, dto);
                result.getSuccessIds().add(appealId);
            } catch (Exception e) {
                log.warn("Batch appeal review failed: appealId={}, result={}, error={}",
                        appealId, reviewResult, e.getClass().getSimpleName());
                result.getFailedIds().add(appealId);
            }
        }

        result.setSuccess(result.getSuccessIds().size());
        result.setFailed(result.getFailedIds().size());
        return Result.success(result);
    }

    @GetMapping("/stats")
    @ApiLog("管理员获取申诉统计")
    public Result<AppealStats> getAppealStats() {
        Map<String, Long> statsMap = appealService.getAppealStats();
        AppealStats stats = new AppealStats();
        stats.setTotal(statsMap.getOrDefault("total", 0L));
        stats.setPending(statsMap.getOrDefault("pending", 0L));
        stats.setApproved(statsMap.getOrDefault("approved", 0L));
        stats.setRejected(statsMap.getOrDefault("rejected", 0L));
        stats.setCancelled(statsMap.getOrDefault("cancelled", 0L));
        return Result.success(stats);
    }

    @GetMapping("/records/{appealId}")
    @ApiLog("管理员获取申诉操作记录")
    public Result<PageResult<AppealRecord>> getAppealRecords(
            @PathVariable Long appealId,
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "20") Long size
    ) {
        IPage<AppealRecord> recordPage = appealRecordService.pageRecords(appealId, null, null, null, page, size);
        return Result.success(buildPageResult(recordPage));
    }

    private Long getCurrentUserId() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return userId;
    }

    private <T> PageResult<T> buildPageResult(IPage<T> page) {
        PageResult<T> result = new PageResult<T>();
        result.setRecords(page.getRecords());
        result.setTotal(page.getTotal());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setPages(page.getPages());
        return result;
    }

    public static class AppealStats {
        private Long total;
        private Long pending;
        private Long approved;
        private Long rejected;
        private Long cancelled;

        public Long getTotal() {
            return total;
        }

        public void setTotal(Long total) {
            this.total = total;
        }

        public Long getPending() {
            return pending;
        }

        public void setPending(Long pending) {
            this.pending = pending;
        }

        public Long getApproved() {
            return approved;
        }

        public void setApproved(Long approved) {
            this.approved = approved;
        }

        public Long getRejected() {
            return rejected;
        }

        public void setRejected(Long rejected) {
            this.rejected = rejected;
        }

        public Long getCancelled() {
            return cancelled;
        }

        public void setCancelled(Long cancelled) {
            this.cancelled = cancelled;
        }
    }

    public static class BatchReviewResult {
        private Integer total = 0;
        private Integer success = 0;
        private Integer failed = 0;
        private List<Long> successIds = new ArrayList<Long>();
        private List<Long> failedIds = new ArrayList<Long>();

        public Integer getTotal() {
            return total;
        }

        public void setTotal(Integer total) {
            this.total = total;
        }

        public Integer getSuccess() {
            return success;
        }

        public void setSuccess(Integer success) {
            this.success = success;
        }

        public Integer getFailed() {
            return failed;
        }

        public void setFailed(Integer failed) {
            this.failed = failed;
        }

        public List<Long> getSuccessIds() {
            return successIds;
        }

        public void setSuccessIds(List<Long> successIds) {
            this.successIds = successIds;
        }

        public List<Long> getFailedIds() {
            return failedIds;
        }

        public void setFailedIds(List<Long> failedIds) {
            this.failedIds = failedIds;
        }
    }
}
