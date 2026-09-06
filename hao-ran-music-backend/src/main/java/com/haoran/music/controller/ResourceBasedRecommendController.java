package com.haoran.music.controller;

import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.context.UserContext;
import com.haoran.music.common.result.Result;
import com.haoran.music.service.ResourceBasedRecommendService;
import com.haoran.music.vo.recommend.RecommendVO;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;












@RestController
@RequestMapping("/recommend/resource")
public class ResourceBasedRecommendController {

    @Resource
    private ResourceBasedRecommendService resourceBasedRecommendService;








    @ApiLog("根据申请歌曲推荐")
    @GetMapping("/requested")
    public Result<RecommendVO> getRecommendByRequestedSongs(
            @RequestParam(defaultValue = "20") Integer limit) {
        Long userId = UserContext.getCurrentUserId();
        RecommendVO result = resourceBasedRecommendService.getRecommendByRequestedSongs(userId, limit);
        return Result.success(result);
    }








    @ApiLog("热门申请歌曲推荐")
    @GetMapping("/hot-requested")
    public Result<RecommendVO> getHotRequestedSongsRecommend(
            @RequestParam(defaultValue = "20") Integer limit) {
        Long userId = UserContext.getCurrentUserId();
        RecommendVO result = resourceBasedRecommendService.getHotRequestedSongsRecommend(userId, limit);
        return Result.success(result);
    }








    @ApiLog("申请偏好推荐")
    @GetMapping("/preference")
    public Result<RecommendVO> getRecommendByRequestPreference(
            @RequestParam(defaultValue = "20") Integer limit) {
        Long userId = UserContext.getCurrentUserId();
        RecommendVO result = resourceBasedRecommendService.getRecommendByRequestPreference(userId, limit);
        return Result.success(result);
    }







    @ApiLog("未满足需求分析")
    @GetMapping("/unmet-analysis")
    public Result<Map<String, Object>> getUnmetRequestsAnalysis() {
        Long userId = UserContext.getCurrentUserId();
        Map<String, Object> result = resourceBasedRecommendService.getUnmetRequestsAnalysis(userId);
        return Result.success(result);
    }








    @ApiLog("热门申请统计")
    @GetMapping("/hot-statistics")
    public Result<Map<String, Object>> getHotRequestStatistics(
            @RequestParam(defaultValue = "20") Integer limit) {
        Map<String, Object> result = resourceBasedRecommendService.getHotRequestStatistics(limit);
        return Result.success(result);
    }
}
