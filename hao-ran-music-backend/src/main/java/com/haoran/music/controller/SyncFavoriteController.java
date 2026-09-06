package com.haoran.music.controller;

import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.result.Result;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.SyncFavoriteService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;





@Slf4j
@RestController
@RequestMapping("/admin/sync")
@RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
public class SyncFavoriteController {

    @Autowired
    private SyncFavoriteService syncFavoriteService;









    @ApiLog("同步收藏数据")
    @PostMapping("/favorite")
    public Result<Long> syncFavoriteData(@RequestParam(required = false) Long userId,
                                       HttpServletRequest request) {
        Long requestUserId = (Long) request.getAttribute("userId");

        log.info("管理员开始同步收藏数据: adminUserId={}, targetUserId={}", requestUserId, userId);
        return syncFavoriteService.syncFavoriteData(userId);
    }









    @ApiLog("修复收藏歌单")
    @PostMapping("/fix-favorite/{userId}")
    public Result<Long> fixFavoritePlaylist(@PathVariable Long userId,
                                          HttpServletRequest request) {
        Long requestUserId = (Long) request.getAttribute("userId");

        log.info("管理员开始修复收藏歌单: adminUserId={}, targetUserId={}", requestUserId, userId);
        return syncFavoriteService.fixFavoritePlaylist(userId);
    }








    @ApiLog("更新歌单歌曲数量")
    @PostMapping("/update-song-count")
    public Result<Integer> updateAllPlaylistSongCount(HttpServletRequest request) {
        Long requestUserId = (Long) request.getAttribute("userId");

        log.info("管理员开始更新所有歌单歌曲数量: adminUserId={}", requestUserId);
        return syncFavoriteService.updateAllPlaylistSongCount();
    }
}
