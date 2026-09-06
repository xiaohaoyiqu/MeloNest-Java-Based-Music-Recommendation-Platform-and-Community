package com.haoran.music.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.common.result.Result;
import com.haoran.music.entity.FavoriteHistory;
import com.haoran.music.entity.ListenHistory;
import com.haoran.music.service.FavoriteHistoryService;
import com.haoran.music.service.KafkaProducerService;
import com.haoran.music.service.ListenHistoryService;
import com.haoran.music.vo.song.ListenHistoryVO;
import com.haoran.music.dto.song.SongVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

   
                      
                                                                              
   
@Slf4j
@RestController
@RequestMapping("/history")
public class HistoryController {

    @Resource
    private ListenHistoryService listenHistoryService;

    @Resource
    private FavoriteHistoryService favoriteHistoryService;

    @Resource
    private KafkaProducerService kafkaProducerService;

                                                     

       
             
       
    @ApiLog("添加播放记录")
    @PostMapping("/listen/add")
    public Result<Void> addListenRecord(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestParam String songId,
            @RequestParam(required = false) Integer progress,
            @RequestParam(required = false, defaultValue = "standard") String quality,
            @RequestParam(required = false, defaultValue = "0") Integer isLocal,
            @RequestParam(required = false) String eventId) {

        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        String normalizedEventId = eventId == null || eventId.trim().isEmpty()
                ? kafkaProducerService.newPlayEventId() : eventId.trim();
        boolean kafkaSuccess = kafkaProducerService.sendPlayEventSync(
                userId, songId, isLocal, progress, quality, normalizedEventId);
        if (!kafkaSuccess) {
            try {
                listenHistoryService.addListenRecord(
                        userId, songId, progress, quality, isLocal, normalizedEventId);
            } catch (Exception dbException) {
                log.error("[ListenHistory] 保存播放历史失败: songId={}, userId={}, error={}",
                    songId, userId, dbException.getClass().getSimpleName());
            }
        }

        return Result.success();
    }

       
                                         
       
    @ApiLog("更新播放进度")
    @PutMapping("/listen/progress")
    public Result<Boolean> updateListenProgress(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestParam String songId,
            @RequestParam Integer progress,
            @RequestParam(required = false, defaultValue = "standard") String quality,
            @RequestParam(required = false, defaultValue = "0") Integer isLocal) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(listenHistoryService.updateListenProgress(
                userId, songId, progress, quality, isLocal));
    }

       
                
       
    @ApiLog("获取最近播放")
    @GetMapping("/listen/recent")
    public Result<List<SongVO>> getRecentSongs(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestParam(defaultValue = "20") Integer limit) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        int safeLimit = limit == null ? 20 : Math.max(1, Math.min(limit, 100));
        List<SongVO> songs = listenHistoryService.getRecentSongs(userId, safeLimit);
        return Result.success(songs);
    }

       
             
       
    @ApiLog("获取播放历史")
    @GetMapping("/listen/list")
    public Result<Map<String, Object>> getListenHistory(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null ? 20 : Math.max(1, Math.min(size, 100));
        List<ListenHistoryVO> history = listenHistoryService.getUserHistoryWithDetails(userId, safePage, safeSize);
        Long totalCount = listenHistoryService.getUserHistoryCount(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("list", history);
        result.put("records", history);
        result.put("total", totalCount);
        result.put("page", safePage);
        result.put("size", safeSize);
        result.put("maxCount", CommonConstants.HISTORY_MAX_COUNT);

        return Result.success(result);
    }

       
                 
       
    @ApiLog("获取播放历史总数")
    @GetMapping("/listen/count")
    public Result<Map<String, Object>> getListenHistoryCount(@RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        Long count = listenHistoryService.getUserHistoryCount(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("count", count);
        result.put("maxCount", CommonConstants.HISTORY_MAX_COUNT);
        return Result.success(result);
    }

       
             
       
    @ApiLog("清空播放历史")
    @DeleteMapping("/listen/clear")
    public Result<Void> clearListenHistory(@RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        listenHistoryService.clearUserHistory(userId);
        return Result.success();
    }

       
                 
       
    @ApiLog("删除播放历史记录")
    @DeleteMapping("/listen/{historyId}")
    public Result<Void> deleteListenHistory(
            @PathVariable Long historyId,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        boolean success = listenHistoryService.deleteHistory(historyId, userId);
        if (!success) {
            return Result.error("记录不存在或无权删除");
        }
        return Result.success();
    }

       
               
       
    @ApiLog("批量导入播放历史")
    @PostMapping("/listen/import")
    public Result<Integer> importListenHistory(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestBody List<Long> songIds) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        if (songIds != null && songIds.size() > 500) {
            return Result.error(400, "单次最多导入500条播放历史");
        }
        int count = listenHistoryService.importHistory(userId, songIds);
        return Result.success(count);
    }

                                                     

       
             
       
    @ApiLog("获取收藏历史")
    @GetMapping({"/favorite/list", "/favorite/history"})
    public Result<IPage<FavoriteHistory>> getFavoriteHistory(PageQuery pageQuery,
                                                              @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        IPage<FavoriteHistory> result = favoriteHistoryService.getFavoriteHistory(userId, pageQuery);
        return Result.success(result);
    }

       
             
       
    @ApiLog("获取收藏统计")
    @GetMapping({"/favorite/statistics", "/favorite/history/statistics"})
    public Result<Map<String, Object>> getFavoriteStatistics(@RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        Map<String, Object> stats = favoriteHistoryService.getFavoriteStatistics(userId);
        return Result.success(stats);
    }

       
             
       
    @ApiLog("清空收藏历史")
    @DeleteMapping({"/favorite/clear", "/favorite/history/clear"})
    public Result<Boolean> clearFavoriteHistory(@RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        Boolean result = favoriteHistoryService.clearFavoriteHistory(userId);
        return Result.success(result);
    }

       
                 
       
    @ApiLog("删除收藏历史记录")
    @DeleteMapping({"/favorite/{id}", "/favorite/history/{id}"})
    public Result<Boolean> deleteFavoriteHistory(@PathVariable("id") Long id,
                                                 @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        Boolean result = favoriteHistoryService.deleteFavoriteHistory(userId, id);
        return Result.success(result);
    }
}
