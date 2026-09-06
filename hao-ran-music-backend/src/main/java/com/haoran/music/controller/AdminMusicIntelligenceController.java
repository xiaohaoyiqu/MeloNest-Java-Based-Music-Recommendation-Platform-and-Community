   
                      
   
package com.haoran.music.controller;

import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.result.Result;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.MusicIntelligenceCacheService;
import com.haoran.music.service.MusicReportRefreshTaskService;
import com.haoran.music.service.PlayEventDeadLetterService;
import com.haoran.music.service.UserMusicReportSnapshotService;
import com.haoran.music.service.UserMusicSummaryService;
import com.haoran.music.service.UserMusicSummaryBackfillTaskService;
import com.haoran.music.service.UserMusicTopItemSummaryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/music-intelligence")
@RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
public class AdminMusicIntelligenceController {

    @Resource
    private UserMusicSummaryService userMusicSummaryService;

    @Resource
    private UserMusicTopItemSummaryService topItemSummaryService;

    @Resource
    private UserMusicReportSnapshotService reportSnapshotService;

    @Resource
    private MusicReportRefreshTaskService reportRefreshTaskService;

    @Resource
    private MusicIntelligenceCacheService musicIntelligenceCacheService;

    @Resource
    private UserMusicSummaryBackfillTaskService summaryBackfillTaskService;

    @Resource
    private PlayEventDeadLetterService playEventDeadLetterService;

    @GetMapping("/status")
    public Result<Map<String, Object>> getStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("summary", userMusicSummaryService.getSummaryStatus());
        result.put("topItems", topItemSummaryService.getStatus());
        result.put("snapshot", reportSnapshotService.getSnapshotStatus());
        result.put("cache", musicIntelligenceCacheService.getCacheStatus());
        result.put("reportRefreshTasks", reportRefreshTaskService.getTaskStatus());
        result.put("summaryBackfillTasks", summaryBackfillTaskService.getTaskStatus());
        result.put("playEventDeadLetters", playEventDeadLetterService.getStatus());
        return Result.success(result);
    }

    @PostMapping("/summary/daily/backfill")
    public Result<Map<String, Object>> backfillDailySummaries(@RequestParam(required = false) String startDate,
                                                              @RequestParam(required = false) String endDate,
                                                              @RequestAttribute(value = "userId", required = false) Long operatorId) {
        try {
            return Result.success(summaryBackfillTaskService.submitDaily(
                    parseDate(startDate), parseDate(endDate), operatorId));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PostMapping("/summary/monthly/backfill")
    public Result<Map<String, Object>> backfillMonthlySummaries(@RequestParam(required = false) String startMonth,
                                                                @RequestParam(required = false) String endMonth,
                                                                @RequestAttribute(value = "userId", required = false) Long operatorId) {
        try {
            return Result.success(summaryBackfillTaskService.submitMonthly(
                    parseMonth(startMonth), parseMonth(endMonth), operatorId));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PostMapping("/summary/daily/backfill/async")
    public Result<Map<String, Object>> submitDailyBackfill(@RequestParam(required = false) String startDate,
                                                           @RequestParam(required = false) String endDate,
                                                           @RequestAttribute(value = "userId", required = false) Long operatorId) {
        try {
            return Result.success(summaryBackfillTaskService.submitDaily(
                    parseDate(startDate), parseDate(endDate), operatorId));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PostMapping("/summary/monthly/backfill/async")
    public Result<Map<String, Object>> submitMonthlyBackfill(@RequestParam(required = false) String startMonth,
                                                             @RequestParam(required = false) String endMonth,
                                                             @RequestAttribute(value = "userId", required = false) Long operatorId) {
        try {
            return Result.success(summaryBackfillTaskService.submitMonthly(
                    parseMonth(startMonth), parseMonth(endMonth), operatorId));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @GetMapping("/summary/backfill/tasks/{taskId}")
    public Result<Map<String, Object>> getSummaryBackfillTask(@PathVariable String taskId) {
        Map<String, Object> task = summaryBackfillTaskService.getTask(taskId);
        return task == null ? Result.error(404, "汇总回填任务不存在") : Result.success(task);
    }

    @PostMapping("/summary/backfill/tasks/{taskId}/retry")
    public Result<Map<String, Object>> retrySummaryBackfillTask(
            @PathVariable String taskId,
            @RequestAttribute(value = "userId", required = false) Long operatorId) {
        try {
            Map<String, Object> task = summaryBackfillTaskService.retry(taskId, operatorId);
            return task == null ? Result.error(404, "汇总回填任务不存在") : Result.success(task);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (IllegalStateException e) {
            return Result.error(409, e.getMessage());
        }
    }

    @PostMapping("/reports/refresh")
    public Result<Map<String, Object>> refreshReport(@RequestParam Long userId,
                                                     @RequestParam String reportType,
                                                     @RequestParam(required = false) Integer year,
                                                     @RequestParam(defaultValue = "false") Boolean async,
                                                     @RequestAttribute(value = "userId", required = false) Long operatorId) {
        try {
            if (Boolean.TRUE.equals(async)) {
                if (operatorId == null) {
                    return Result.error(401, "请先登录");
                }
                return Result.success(reportRefreshTaskService.submit(userId, reportType, year, operatorId));
            }
            return Result.success(reportRefreshTaskService.refreshNow(userId, reportType, year));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (IllegalStateException e) {
            return Result.error(500, "刷新报告失败，请稍后重试");
        }
    }

    @GetMapping("/reports/tasks/{taskId}")
    public Result<Map<String, Object>> getReportRefreshTask(@PathVariable String taskId) {
        Map<String, Object> task = reportRefreshTaskService.getTask(taskId);
        return task == null ? Result.error(404, "刷新任务不存在") : Result.success(task);
    }

    @PostMapping("/reports/tasks/{taskId}/retry")
    public Result<Map<String, Object>> retryReportRefreshTask(
            @PathVariable String taskId,
            @RequestAttribute(value = "userId", required = false) Long operatorId) {
        try {
            Map<String, Object> task = reportRefreshTaskService.retry(taskId, operatorId);
            return task == null ? Result.error(404, "刷新任务不存在") : Result.success(task);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (IllegalStateException e) {
            return Result.error(409, e.getMessage());
        }
    }

    @PostMapping("/candidate-cache/bump")
    public Result<Map<String, Object>> bumpCandidateCache(@RequestParam(required = false) String reason,
                                                          @RequestAttribute(value = "userId", required = false) Long operatorId) {
        if (operatorId == null) {
            return Result.error(401, "请先登录");
        }
        try {
            long version = musicIntelligenceCacheService.bumpCandidateCacheVersion(reason, operatorId);
            Map<String, Object> result = new HashMap<>();
            result.put("version", version);
            result.put("status", musicIntelligenceCacheService.getCacheStatus());
            return Result.success(result);
        } catch (IllegalStateException e) {
            return Result.error(500, "更新候选缓存版本失败，请稍后重试");
        }
    }

    @PostMapping("/cache/recommend/bump")
    public Result<Map<String, Object>> bumpRecommendCache(@RequestParam(required = false) String reason,
                                                          @RequestAttribute(value = "userId", required = false) Long operatorId) {
        if (operatorId == null) {
            return Result.error(401, "请先登录");
        }
        try {
            long version = musicIntelligenceCacheService.bumpRecommendCacheVersion(reason, operatorId);
            Map<String, Object> result = new HashMap<>();
            result.put("version", version);
            result.put("status", musicIntelligenceCacheService.getCacheStatus());
            return Result.success(result);
        } catch (IllegalStateException e) {
            return Result.error(500, "更新推荐缓存版本失败，请稍后重试");
        }
    }

    @PostMapping("/cache/ranking/bump")
    public Result<Map<String, Object>> bumpRankingCache(@RequestParam(required = false) String reason,
                                                        @RequestAttribute(value = "userId", required = false) Long operatorId) {
        if (operatorId == null) {
            return Result.error(401, "请先登录");
        }
        try {
            long version = musicIntelligenceCacheService.bumpRankingCacheVersion(reason, operatorId);
            Map<String, Object> result = new HashMap<>();
            result.put("version", version);
            result.put("status", musicIntelligenceCacheService.getCacheStatus());
            return Result.success(result);
        } catch (IllegalStateException e) {
            return Result.error(500, "更新排行缓存版本失败，请稍后重试");
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("日期格式应为 yyyy-MM-dd");
        }
    }

    private YearMonth parseMonth(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return YearMonth.parse(value.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("月份格式应为 yyyy-MM");
        }
    }
}
