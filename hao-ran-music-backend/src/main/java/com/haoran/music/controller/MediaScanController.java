package com.haoran.music.controller;

import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.result.Result;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.MediaScanService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;





@RestController
@RequestMapping("/admin/media")
@RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
public class MediaScanController {

    private static final int MAX_SCAN_LIMIT = 500;
    private static final int MAX_SPECIFIC_IDS = 100;

    @Resource
    private MediaScanService mediaScanService;




    @ApiLog("扫描MV时长")
    @PostMapping("/scan/mv")
    public Result<Integer> scanMVDuration(@RequestParam(defaultValue = "50") int limit) {
        int count = mediaScanService.scanAndUpdateMVDuration(normalizeLimit(limit));
        return Result.success(count);
    }




    @ApiLog("扫描歌曲时长")
    @PostMapping("/scan/song")
    public Result<Integer> scanSongDuration(@RequestParam(defaultValue = "100") int limit) {
        int count = mediaScanService.scanAndUpdateSongDuration(normalizeLimit(limit));
        return Result.success(count);
    }




    @ApiLog("扫描所有媒体")
    @PostMapping("/scan/all")
    public Result<String> scanAll() {
        int mvCount = mediaScanService.scanAndUpdateMVDuration(100);
        int songCount = mediaScanService.scanAndUpdateSongDuration(200);
        return Result.success("MV更新" + mvCount + "个，歌曲更新" + songCount + "首");
    }







    @ApiLog("扫描指定MV时长")
    @PostMapping("/scan/mv/specific")
    public Result<Integer> scanSpecificMVs(@RequestBody List<Long> mvIds) {
        int count = mediaScanService.scanSpecificMVs(normalizeIds(mvIds));
        return Result.success(count);
    }







    @ApiLog("扫描指定歌曲时长")
    @PostMapping("/scan/song/specific")
    public Result<Integer> scanSpecificSongs(@RequestBody List<Long> songIds) {
        int count = mediaScanService.scanSpecificSongs(normalizeIds(songIds));
        return Result.success(count);
    }

    private int normalizeLimit(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("扫描数量必须大于0");
        }
        return Math.min(limit, MAX_SCAN_LIMIT);
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("媒体ID列表不能为空");
        }
        if (ids.size() > MAX_SPECIFIC_IDS) {
            throw new IllegalArgumentException("单次最多扫描100个媒体文件");
        }
        if (ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("媒体ID必须为正整数");
        }
        return ids.stream().distinct().collect(Collectors.toList());
    }
}
