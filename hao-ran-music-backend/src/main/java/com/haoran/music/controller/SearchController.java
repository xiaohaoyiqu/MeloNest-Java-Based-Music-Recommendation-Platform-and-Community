package com.haoran.music.controller;

import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.aspect.DetectCrawler;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.Result;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.service.SearchCorrectionService;
import com.haoran.music.service.SearchService;
import com.haoran.music.vo.search.SearchResultVO;
import javax.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;





@RestController
@RequestMapping("/search")
public class SearchController {

    @Resource
    private SearchService searchService;

    @Resource
    private SearchCorrectionService searchCorrectionService;








    @ApiLog("搜索建议")
    @GetMapping("/suggest")
    public Result<List<String>> getSearchSuggestions(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "10") Integer limit) {
        List<String> suggestions = searchCorrectionService.getSearchSuggestions(keyword, limit);
        return Result.success(suggestions);
    }







    @ApiLog("智能纠错")
    @GetMapping("/correct")
    public Result<String> correctSearch(@RequestParam String input) {
        String corrected = searchCorrectionService.getCorrectedQuery(input);
        return Result.successData(corrected);
    }







    @ApiLog("综合搜索")
    @DetectCrawler(operation = "综合搜索", checkReferer = true, riskThreshold = 50)
    @GetMapping
    public Result<SearchResultVO> search(
            @RequestParam String keyword,
            @RequestAttribute(value = CommonConstants.USER_ID_KEY, required = false) Long currentUserId) {
        SearchResultVO result = searchService.search(keyword, currentUserId);
        return Result.success(result);
    }







    @ApiLog("搜索歌曲")
    @DetectCrawler(operation = "搜索歌曲", checkReferer = true, riskThreshold = 50)
    @GetMapping({"/song", "/songs"})
    public Result<SearchResultVO> searchSongs(
            @RequestParam String keyword,
            @RequestAttribute(value = CommonConstants.USER_ID_KEY, required = false) Long currentUserId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "all") String field) {
        SearchResultVO result = searchService.searchSongs(keyword, currentUserId, page, size, field);
        return Result.success(result);
    }







    @ApiLog("搜索专辑")
    @DetectCrawler(operation = "搜索专辑", checkReferer = true)
    @GetMapping("/albums")
    public Result<SearchResultVO> searchAlbums(
            @RequestParam String keyword,
            @RequestAttribute(value = CommonConstants.USER_ID_KEY, required = false) Long currentUserId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        SearchResultVO result = searchService.searchAlbums(keyword, currentUserId, page, size);
        return Result.success(result);
    }







    @ApiLog("搜索歌手")
    @DetectCrawler(operation = "搜索歌手", checkReferer = true)
    @GetMapping("/artists")
    public Result<SearchResultVO> searchArtists(
            @RequestParam String keyword,
            @RequestAttribute(value = CommonConstants.USER_ID_KEY, required = false) Long currentUserId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        SearchResultVO result = searchService.searchArtists(keyword, currentUserId, page, size);
        return Result.success(result);
    }







    @ApiLog("搜索歌单")
    @GetMapping("/playlists")
    public Result<SearchResultVO> searchPlaylists(
            @RequestParam String keyword,
            @RequestAttribute(value = CommonConstants.USER_ID_KEY, required = false) Long currentUserId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        SearchResultVO result = searchService.searchPlaylists(keyword, currentUserId, page, size);
        return Result.success(result);
    }







    @ApiLog("搜索MV")
    @GetMapping("/mvs")
    public Result<SearchResultVO> searchMvs(
            @RequestParam String keyword,
            @RequestAttribute(value = CommonConstants.USER_ID_KEY, required = false) Long currentUserId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        SearchResultVO result = searchService.searchMvs(keyword, currentUserId, page, size);
        return Result.success(result);
    }







    @ApiLog("搜索用户")
    @GetMapping("/users")
    public Result<SearchResultVO> searchUsers(
            @RequestParam String keyword,
            @RequestAttribute(value = CommonConstants.USER_ID_KEY, required = false) Long currentUserId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        SearchResultVO result = searchService.searchUsers(keyword, currentUserId, page, size);
        return Result.success(result);
    }







    @ApiLog("获取热门关键词")
    @GetMapping("/hot-keywords")
    public Result<List<String>> getHotKeywords(@RequestParam(defaultValue = "10") Integer limit) {
        List<String> result = searchService.getHotKeywords(limit);
        return Result.success(result);
    }







    @ApiLog("获取搜索历史")
    @GetMapping("/history")
    public Result<List<String>> getSearchHistory(
            @RequestParam(defaultValue = "10") Integer limit,
            @RequestAttribute(value = CommonConstants.USER_ID_KEY, required = false) Long currentUserId) {
        List<String> result = searchService.getSearchHistory(requireCurrentUserId(currentUserId), limit);
        return Result.success(result);
    }







    @ApiLog("保存搜索历史")
    @PostMapping("/history")
    public Result<Void> saveSearchHistory(
            @RequestParam String keyword,
            @RequestAttribute(value = CommonConstants.USER_ID_KEY, required = false) Long currentUserId) {
        searchService.saveSearchHistory(requireCurrentUserId(currentUserId), keyword);
        return Result.success();
    }






    @ApiLog("清空搜索历史")
    @DeleteMapping("/history")
    public Result<Void> clearSearchHistory(
            @RequestAttribute(value = CommonConstants.USER_ID_KEY, required = false) Long currentUserId) {
        searchService.clearSearchHistory(requireCurrentUserId(currentUserId));
        return Result.success();
    }







    @ApiLog("删除单条搜索历史")
    @DeleteMapping("/history/item")
    public Result<Void> deleteSearchHistoryItem(
            @RequestParam String keyword,
            @RequestAttribute(value = CommonConstants.USER_ID_KEY, required = false) Long currentUserId) {
        searchService.deleteSearchHistoryItem(requireCurrentUserId(currentUserId), keyword);
        return Result.success();
    }

    private Long requireCurrentUserId(Long currentUserId) {
        if (currentUserId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        return currentUserId;
    }
}
