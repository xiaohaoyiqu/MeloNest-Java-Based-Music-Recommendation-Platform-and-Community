   
                      
                       
   

package com.haoran.music.controller;

import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.context.UserContext;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.Result;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.PermissionService;
import com.haoran.music.service.StatisticsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/statistics")
public class StatisticsController {

    @Resource
    private StatisticsService statisticsService;

    @Resource
    private PermissionService permissionService;

    @ApiLog("获取审核统计概览")
    @RequireRole({UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/audit/overview")
    public Result<Map<String, Object>> getAuditOverview(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) Long moderatorId) {
        Long scopedModeratorId = resolveModeratorScope(moderatorId);
        log.info("Get audit overview: startTime={}, endTime={}, moderatorId={}",
                startTime, endTime, scopedModeratorId);
        return Result.success(statisticsService.getAuditOverview(startTime, endTime, scopedModeratorId));
    }

    @ApiLog("获取实时审核状态")
    @RequireRole({UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/audit/realtime")
    public Result<Map<String, Object>> getRealtimeStatus(
            @RequestParam(required = false) Long moderatorId) {
        Long scopedModeratorId = resolveModeratorScope(moderatorId);
        log.info("Get realtime audit status: moderatorId={}", scopedModeratorId);
        return Result.success(statisticsService.getRealtimeAuditStatus(scopedModeratorId));
    }

    @ApiLog("获取审核员排名")
    @RequireRole({UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/audit/moderator-ranking")
    public Result<List<Map<String, Object>>> getModeratorRanking(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) Long moderatorId) {
        Long scopedModeratorId = resolveModeratorScope(moderatorId);
        log.info("Get moderator ranking: startTime={}, endTime={}, moderatorId={}",
                startTime, endTime, scopedModeratorId);
        return Result.success(statisticsService.getModeratorRanking(startTime, endTime, scopedModeratorId));
    }

    @ApiLog("获取审核类型统计")
    @RequireRole({UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/audit/type-stats")
    public Result<Map<String, Object>> getAuditTypeStats(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) Long moderatorId) {
        Long scopedModeratorId = resolveModeratorScope(moderatorId);
        log.info("Get audit type stats: startTime={}, endTime={}, moderatorId={}",
                startTime, endTime, scopedModeratorId);
        return Result.success(statisticsService.getAuditTypeStats(startTime, endTime, scopedModeratorId));
    }

    @ApiLog("获取审核趋势")
    @RequireRole({UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/audit/trend")
    public Result<List<Map<String, Object>>> getAuditTrend(
            @RequestParam(defaultValue = "30") Integer days,
            @RequestParam(required = false) Long moderatorId) {
        Long scopedModeratorId = resolveModeratorScope(moderatorId);
        log.info("Get audit trend: days={}, moderatorId={}", days, scopedModeratorId);
        return Result.success(statisticsService.getAuditTrend(days, scopedModeratorId));
    }

    @ApiLog("获取平台概览")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/platform/overview")
    public Result<StatisticsService.PlatformOverviewVO> getPlatformOverview() {
        return Result.success(statisticsService.getOverview());
    }

    @ApiLog("获取用户增长趋势")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/user/trend")
    public Result<List<StatisticsService.TrendDataVO>> getUserTrend(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate,
            @RequestParam(defaultValue = "day") String interval) {
        Date safeEndDate = endDate == null ? new Date() : endDate;
        Date safeStartDate = startDate == null
                ? new Date(safeEndDate.getTime() - 6L * 24L * 60L * 60L * 1000L)
                : startDate;
        return Result.success(statisticsService.getUserTrend(safeStartDate, safeEndDate, interval));
    }

    @ApiLog("获取内容统计")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/content/overview")
    public Result<StatisticsService.ContentStatisticsVO> getContentOverview() {
        return Result.success(statisticsService.getContentStatistics());
    }

    @ApiLog("获取互动统计")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/interaction/today")
    public Result<StatisticsService.InteractionStatisticsVO> getInteractionStatistics() {
        return Result.success(statisticsService.getInteractionStatistics());
    }

    @ApiLog("获取热门歌曲")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/content/hot-songs")
    public Result<List<StatisticsService.HotSongVO>> getHotSongs(
            @RequestParam(defaultValue = "10") Integer limit) {
        return Result.success(statisticsService.getHotSongs(limit));
    }

    @ApiLog("获取热门创作者")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/content/hot-creators")
    public Result<List<StatisticsService.HotCreatorVO>> getHotCreators(
            @RequestParam(defaultValue = "10") Integer limit) {
        return Result.success(statisticsService.getHotCreators(limit));
    }

    private Long resolveModeratorScope(Long requestedModeratorId) {
        Long currentUserId = UserContext.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        boolean admin = permissionService.isAdmin(currentUserId);
        if (requestedModeratorId != null) {
            if (!admin && !currentUserId.equals(requestedModeratorId)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "只能查看自己的审核统计");
            }
            return requestedModeratorId;
        }
        return admin ? null : currentUserId;
    }
}
