package com.haoran.music.controller;

import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.aspect.DetectCrawler;
import com.haoran.music.common.result.Result;
import com.haoran.music.service.AudioFeatureRecommendService;
import com.haoran.music.vo.recommend.RecommendVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;







@Slf4j
@RestController
@RequestMapping("/audio-feature")
public class AudioFeatureRecommendController {

    @Autowired
    private AudioFeatureRecommendService audioFeatureRecommendService;


















    @ApiLog("场景推荐")
    @GetMapping("/scenario/{scenarioCode}")
    public Result<RecommendVO> recommendByScenario(
            @PathVariable String scenarioCode,
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestParam(defaultValue = "20") Integer limit) {
        RecommendVO result = audioFeatureRecommendService.recommendByScenario(userId, scenarioCode, limit);
        return Result.success(result);
    }






    @ApiLog("获取场景列表")
    @GetMapping("/scenarios")
    public Result<List<Map<String, Object>>> getScenarios() {
        List<Map<String, Object>> scenarios = audioFeatureRecommendService.getScenarios();
        return Result.success(scenarios);
    }

















    @ApiLog("情绪推荐")
    @GetMapping("/mood")
    @DetectCrawler(operation = "情绪推荐", checkReferer = true, checkBehavior = false)
    public Result<RecommendVO> recommendByMood(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestParam Double valence,
            @RequestParam(required = false) Double energy,
            @RequestParam(defaultValue = "20") Integer limit) {
        RecommendVO result = audioFeatureRecommendService.recommendByMood(userId, valence, energy, limit);
        return Result.success(result);
    }







    @ApiLog("获取用户情绪分析")
    @GetMapping("/mood/analysis")
    public Result<Map<String, Object>> getUserMoodAnalysis(@RequestAttribute("userId") Long userId) {
        Map<String, Object> analysis = audioFeatureRecommendService.getUserMoodAnalysis(userId);
        return Result.success(analysis);
    }











    @ApiLog("音频特征相似推荐")
    @GetMapping("/similar/{songId}")
    public Result<RecommendVO> recommendByAudioFeatures(
            @PathVariable Long songId,
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestParam(defaultValue = "10") Integer limit) {
        RecommendVO result = audioFeatureRecommendService.recommendByAudioFeatures(userId, songId, limit);
        return Result.success(result);
    }








    @ApiLog("计算歌曲相似度")
    @GetMapping("/similarity")
    public Result<Double> calculateSimilarity(
            @RequestParam Long songId1,
            @RequestParam Long songId2) {
        Double similarity = audioFeatureRecommendService.calculateSimilarity(songId1, songId2);
        return Result.success(similarity);
    }











    @ApiLog("混合推荐")
    @GetMapping("/hybrid")
    public Result<RecommendVO> recommendByMoodAndPreference(
            @RequestAttribute("userId") Long userId,
            @RequestParam(required = false) Double valence,
            @RequestParam(required = false) Double energy,
            @RequestParam(defaultValue = "20") Integer limit) {
        RecommendVO result = audioFeatureRecommendService.recommendByMoodAndPreference(userId, valence, energy, limit);
        return Result.success(result);
    }

















    @ApiLog("BPM范围推荐")
    @GetMapping("/bpm")
    public Result<RecommendVO> recommendByBpmRange(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestParam Integer minBpm,
            @RequestParam Integer maxBpm,
            @RequestParam(defaultValue = "20") Integer limit) {
        RecommendVO result = audioFeatureRecommendService.recommendByBpmRange(userId, minBpm, maxBpm, limit);
        return Result.success(result);
    }









    @ApiLog("获取歌曲音频特征")
    @GetMapping("/features/{songId}")
    public Result<Map<String, Object>> getSongAudioFeatures(@PathVariable Long songId) {
        Map<String, Object> features = audioFeatureRecommendService.getSongAudioFeatures(songId);
        return Result.success(features);
    }







    @ApiLog("批量获取音频特征")
    @GetMapping("/features/batch")
    public Result<List<Map<String, Object>>> batchGetAudioFeatures(
            @RequestParam String songIds) {
        List<Long> idList = java.util.Arrays.stream(songIds.split(","))
                .map(Long::parseLong)
                .collect(java.util.stream.Collectors.toList());
        List<Map<String, Object>> features = audioFeatureRecommendService.batchGetAudioFeatures(idList);
        return Result.success(features);
    }
}
