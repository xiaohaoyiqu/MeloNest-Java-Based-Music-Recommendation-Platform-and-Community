package com.haoran.music.controller;

import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.dto.PageResult;
import com.haoran.music.common.result.Result;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.MusicTagService;
import com.haoran.music.vo.tag.MusicTagVO;

import javax.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;





@RestController
@RequestMapping("/music-tag")
public class MusicTagController {

    @Resource
    private MusicTagService musicTagService;




    @ApiLog("获取音乐标签列表")
    @GetMapping("/list")
    public Result<Map<String, List<MusicTagVO>>> getTagList() {
        Map<String, List<MusicTagVO>> result = musicTagService.getTagList();
        return Result.success(result);
    }




    @ApiLog("获取热门标签")
    @GetMapping("/hot")
    public Result<List<MusicTagVO>> getHotTags(@RequestParam(defaultValue = "10") Integer limit) {
        List<MusicTagVO> result = musicTagService.getHotTags(limit);
        return Result.success(result);
    }




    @ApiLog("根据标签搜索歌曲")
    @GetMapping("/songs")
    public Result<PageResult> searchSongsByTags(
            @RequestParam List<Long> tagIds,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        PageResult result = musicTagService.searchSongsByTags(tagIds, page, size);
        return Result.success(result);
    }




    @ApiLog("获取歌曲标签")
    @GetMapping("/song/{songId}")
    public Result<List<MusicTagVO>> getSongTags(@PathVariable Long songId) {
        List<MusicTagVO> result = musicTagService.getSongTags(songId);
        return Result.success(result);
    }




    @ApiLog("添加歌曲标签")
    @PostMapping("/song/{songId}")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    public Result<Void> addSongTag(
            @PathVariable Long songId,
            @RequestParam Long tagId) {
        musicTagService.addSongTag(songId, tagId);
        return Result.success();
    }




    @ApiLog("移除歌曲标签")
    @DeleteMapping("/song/{songId}/tag/{tagId}")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    public Result<Void> removeSongTag(
            @PathVariable Long songId,
            @PathVariable Long tagId) {
        musicTagService.removeSongTag(songId, tagId);
        return Result.success();
    }




    @ApiLog("获取用户标签偏好")
    @GetMapping("/user/preference")
    public Result<List<MusicTagVO>> getUserTagPreference() {
        List<MusicTagVO> result = musicTagService.getUserTagPreference();
        return Result.success(result);
    }




    @ApiLog("基于标签推荐歌曲")
    @GetMapping("/recommend")
    public Result<List> recommendByTags(@RequestParam(defaultValue = "10") Integer limit) {
        List result = musicTagService.recommendByTags(limit);
        return Result.success(result);
    }
}
