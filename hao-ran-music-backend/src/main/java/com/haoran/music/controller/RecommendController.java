package com.haoran.music.controller;

import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.aspect.DetectCrawler;
import com.haoran.music.common.result.Result;
import com.haoran.music.service.RecommendService;
import com.haoran.music.vo.recommend.RecommendVO;
import com.haoran.music.vo.recommend.RecommendedSongVO;

import javax.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;






@RestController
@RequestMapping("/recommend")
public class RecommendController {

    @Resource
    private RecommendService recommendService;






    @ApiLog("获取每日推荐")
    @DetectCrawler(operation = "每日推荐", checkReferer = true, checkBehavior = false)
    @GetMapping("/daily")
    public Result<RecommendVO> getDailyRecommend(
            @RequestAttribute(value = "userId", required = false) Long userId) {
        RecommendVO result = recommendService.getDailyRecommend(userId);
        return Result.success(result);
    }







    @ApiLog("获取个性化推荐")
    @GetMapping("/personal")
    public Result<RecommendVO> getPersonalRecommend(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestParam(defaultValue = "20") Integer limit) {
        RecommendVO result = recommendService.getPersonalRecommend(userId, limit);
        return Result.success(result);
    }







    @ApiLog("获取发现音乐")
    @GetMapping("/discover")
    public Result<RecommendVO> getDiscoverRecommend(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestParam(defaultValue = "20") Integer limit) {
        RecommendVO result = recommendService.getDiscoverRecommend(userId, limit);
        return Result.success(result);
    }








    @ApiLog("获取相似歌曲推荐")
    @GetMapping("/song/{songId}/similar")
    public Result<RecommendVO> getSimilarSongs(
            @PathVariable("songId") Long songId,
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestParam(defaultValue = "10") Integer limit) {
        RecommendVO result = recommendService.getSimilarSongs(userId, songId, limit);
        return Result.success(result);
    }








    @ApiLog("获取歌手推荐")
    @GetMapping("/artist/{artistId}")
    public Result<RecommendVO> getArtistRecommend(
            @PathVariable("artistId") Long artistId,
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestParam(defaultValue = "10") Integer limit) {
        RecommendVO result = recommendService.getArtistRecommend(userId, artistId, limit);
        return Result.success(result);
    }






    @ApiLog("刷新推荐画像")
    @PostMapping("/refresh")
    public Result<Void> refreshRecommendProfile(@RequestAttribute("userId") Long userId) {
        recommendService.refreshUserRecommendProfile(userId);
        return Result.success();
    }






    @ApiLog("获取用户偏好标签")
    @GetMapping("/preferences/tags")
    public Result<List<String>> getUserPreferenceTags(@RequestAttribute("userId") Long userId) {
        List<String> result = recommendService.getUserPreferenceTags(userId);
        return Result.success(result);
    }









    @ApiLog("记录用户行为")
    @PostMapping("/action")
    public Result<Void> recordAction(
            @RequestAttribute("userId") Long userId,
            @RequestParam String actionType,
            @RequestParam Long targetId,
            @RequestParam(defaultValue = "1") Integer targetType) {
        recommendService.recordUserAction(userId, actionType, targetId, targetType);
        return Result.success();
    }










    @ApiLog("获取个性化推荐（带理由）")
    @GetMapping("/personal/reason")
    public Result<List<RecommendedSongVO>> getPersonalRecommendWithReason(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestParam(defaultValue = "10") Integer limit) {
        List<RecommendedSongVO> result = recommendService.getPersonalRecommendWithReason(userId, limit);
        return Result.success(result);
    }








    @ApiLog("获取每日发现（带理由）")
    @GetMapping("/discovery/reason")
    public Result<List<RecommendedSongVO>> getDailyDiscoveryWithReason(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestParam(defaultValue = "10") Integer limit) {
        List<RecommendedSongVO> result = recommendService.getDailyDiscoveryWithReason(userId, limit);
        return Result.success(result);
    }









    @ApiLog("获取相似推荐（带理由）")
    @GetMapping("/similar/{songId}/reason")
    public Result<List<RecommendedSongVO>> getSimilarRecommendWithReason(
            @PathVariable("songId") Long songId,
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestParam(defaultValue = "10") Integer limit) {
        List<RecommendedSongVO> result = recommendService.getSimilarRecommendWithReason(userId, songId, limit);
        return Result.success(result);
    }










    @ApiLog("获取个性化歌单推荐")
    @GetMapping("/playlists")
    public Result<List<Long>> getPersonalizedPlaylists(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestParam(defaultValue = "10") Integer limit) {
        List<Long> result = recommendService.getPersonalizedPlaylists(userId, limit);
        return Result.success(result);
    }








    @ApiLog("获取个性化专辑推荐")
    @GetMapping("/albums")
    public Result<List<Long>> getPersonalizedAlbums(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestParam(defaultValue = "10") Integer limit) {
        List<Long> result = recommendService.getPersonalizedAlbums(userId, limit);
        return Result.success(result);
    }








    @ApiLog("获取个性化MV推荐")
    @GetMapping("/mvs")
    public Result<List<Long>> getPersonalizedMVs(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestParam(defaultValue = "10") Integer limit) {
        List<Long> result = recommendService.getPersonalizedMVs(userId, limit);
        return Result.success(result);
    }
}
