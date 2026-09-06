package com.haoran.music.controller;

import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.result.Result;
import com.haoran.music.service.SearchEnhanceService;
import com.haoran.music.vo.search.HotSearchVO;
import com.haoran.music.vo.search.SearchSuggestVO;

import javax.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;





@RestController
@RequestMapping("/search/enhance")
public class SearchEnhanceController {

    @Resource
    private SearchEnhanceService searchEnhanceService;








    @ApiLog("搜索联想")
    @GetMapping("/suggest")
    public Result<SearchSuggestVO> getSuggest(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "10") Integer limit) {
        SearchSuggestVO result = searchEnhanceService.getSuggest(keyword, limit);
        return Result.success(result);
    }







    @ApiLog("获取热门搜索")
    @GetMapping("/hot")
    public Result<List<HotSearchVO>> getHotSearch(
            @RequestParam(defaultValue = "10") Integer limit) {
        List<HotSearchVO> result = searchEnhanceService.getHotSearch(limit);
        return Result.success(result);
    }







    @ApiLog("保存搜索历史")
    @PostMapping("/history")
    public Result<Void> saveSearchHistory(@RequestParam String keyword) {
        searchEnhanceService.saveSearchHistory(keyword);
        return Result.success();
    }






    @ApiLog("获取搜索历史")
    @GetMapping("/history")
    public Result<List<String>> getSearchHistory() {
        List<String> result = searchEnhanceService.getSearchHistory();
        return Result.success(result);
    }






    @ApiLog("清空搜索历史")
    @DeleteMapping("/history")
    public Result<Void> clearSearchHistory() {
        searchEnhanceService.clearSearchHistory();
        return Result.success();
    }
}
