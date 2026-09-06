


package com.haoran.music.controller;

import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.aspect.RateLimit;
import com.haoran.music.common.aspect.RateLimitScope;
import com.haoran.music.common.result.Result;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.AdminOperationsStatusService;
import com.haoran.music.service.ApiPerformanceMetricsService;
import com.haoran.music.service.MediaReplicaReconciliationService;
import com.haoran.music.vo.admin.MediaReplicaReconciliationVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Map;




@RestController
@RequestMapping("/admin/operations")
@RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
public class AdminOperationsController {

    @Resource
    private AdminOperationsStatusService operationsStatusService;

    @Resource
    private ApiPerformanceMetricsService apiPerformanceMetricsService;

    @Resource
    private MediaReplicaReconciliationService mediaReplicaReconciliationService;

    @GetMapping("/status")
    public Result<Map<String, Object>> getStatus() {
        return Result.success(operationsStatusService.getUnifiedStatus());
    }

    @GetMapping("/performance/slow-apis")
    public Result<Map<String, Object>> getSlowApis(@RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(apiPerformanceMetricsService.getStatus(limit == null ? 20 : limit));
    }

    @GetMapping("/resources/playable-quality")
    public Result<Map<String, Object>> getPlayableResourceQuality(
            @RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(operationsStatusService.getPlayableResourceQuality(limit == null ? 20 : limit));
    }

    @GetMapping("/media-derivative-tasks/status")
    public Result<Map<String, Object>> getMediaDerivativeTaskStatus(
            @RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(operationsStatusService.getMediaDerivativeTaskStatus(limit == null ? 20 : limit));
    }

    @PostMapping("/media-assets/reconcile/dry-run")
    @RateLimit(maxRequests = 1, timeWindowSeconds = 60, operation = "mediaReplicaDryRun",
            scope = RateLimitScope.GLOBAL, message = "媒体副本对账过于频繁，请稍后再试")
    public Result<MediaReplicaReconciliationVO> reconcileMediaAssets(
            @RequestParam(defaultValue = "200") Integer limit,
            @RequestParam(defaultValue = "false") Boolean verifyHash,
            @RequestParam(defaultValue = "1") Integer maxHashFiles) {
        return Result.success(mediaReplicaReconciliationService.reconcile(
                limit == null ? 200 : limit,
                Boolean.TRUE.equals(verifyHash),
                maxHashFiles == null ? 1 : maxHashFiles));
    }

    @PostMapping("/external-links/check")
    public Result<Map<String, Object>> checkExternalLinks(@RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(operationsStatusService.checkExternalLinks(limit == null ? 20 : limit));
    }

    @GetMapping("/migrations/status")
    public Result<Map<String, Object>> getMigrationStatus(@RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(operationsStatusService.getMigrationStatus(limit == null ? 20 : limit));
    }

    @PostMapping("/migrations/register")
    public Result<Map<String, Object>> registerMigration(@RequestParam String scriptName,
                                                         @RequestParam(required = false) String checksum,
                                                         @RequestParam(required = false) String note,
                                                         @RequestAttribute(value = "userId", required = false) Long operatorId) {
        try {
            return Result.success(operationsStatusService.registerMigration(scriptName, checksum, note, operatorId));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error(500, "登记迁移失败，请稍后重试");
        }
    }
}
