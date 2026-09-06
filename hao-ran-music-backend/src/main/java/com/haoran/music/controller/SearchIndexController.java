package com.haoran.music.controller;

import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.result.Result;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.SearchIndexSyncOutboxService;
import com.haoran.music.service.search.SearchIndexService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;






@RestController
@RequestMapping("/admin/search")
@RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
public class SearchIndexController {

    @Resource
    private SearchIndexService searchIndexService;

    @Resource
    private SearchIndexSyncOutboxService searchIndexSyncOutboxService;

    @ApiLog("重建搜索索引")
    @PostMapping("/index/rebuild")
    public Result<Map<String, Object>> rebuildIndex() {
        return Result.success(searchIndexService.rebuildAll());
    }

    @GetMapping("/index/status")
    public Result<Boolean> indexStatus() {
        return Result.success(searchIndexService.isAvailable());
    }

    @GetMapping("/sync-outbox/status")
    @ApiLog("管理员获取搜索索引同步状态")
    public Result<Map<String, Object>> syncOutboxStatus() {
        return Result.success(searchIndexSyncOutboxService.getStatusSummary());
    }

    @GetMapping("/sync-outbox/failures")
    @ApiLog("管理员获取搜索索引同步失败事件")
    public Result<List<Map<String, Object>>> syncOutboxFailures(
            @RequestParam(defaultValue = "20") Integer limit) {
        int safeLimit = limit == null ? 20 : Math.max(1, Math.min(limit, 100));
        return Result.success(searchIndexSyncOutboxService.getRecentFailures(safeLimit));
    }

    @PostMapping("/sync-outbox/retry")
    @ApiLog("管理员重试搜索索引同步")
    public Result<Integer> retrySyncOutbox(
            @RequestParam(defaultValue = "20") Integer limit) {
        int safeLimit = limit == null ? 20 : Math.max(1, Math.min(limit, 100));
        return Result.success(searchIndexSyncOutboxService.retryDueEvents(safeLimit));
    }

    @PostMapping("/sync-outbox/{eventId}/retry")
    @ApiLog("管理员重试单个搜索索引同步事件")
    public Result<Boolean> retryFailedSyncEvent(@PathVariable String eventId) {
        return Result.success(searchIndexSyncOutboxService.retryFailedEvent(eventId));
    }
}
