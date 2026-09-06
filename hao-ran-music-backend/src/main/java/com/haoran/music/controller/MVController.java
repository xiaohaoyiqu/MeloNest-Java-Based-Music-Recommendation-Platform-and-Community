package com.haoran.music.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.aspect.DetectCrawler;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.common.result.Result;
import com.haoran.music.service.MVService;
import com.haoran.music.vo.mv.MVVO;
import javax.annotation.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.util.List;






@RestController
@RequestMapping("/mv")
public class MVController {

    @Resource
    private MVService mvService;







    @ApiLog("获取MV详情")

    @GetMapping("/info/{id}")
    public Result<MVVO> getMVById(@PathVariable("id") Long id,
                                   @RequestAttribute(value = "userId", required = false) Long userId) {
        MVVO result = mvService.getMVById(id, userId);
        return Result.success(result);
    }











    @ApiLog("查询MV列表")
    @DetectCrawler(operation = "查询MV列表", checkReferer = true, checkBehavior = false)

    @GetMapping("/page")
    public Result<IPage<MVVO>> pageMVs(PageQuery pageQuery,
                                       @RequestParam(required = false) String area,
                                       @RequestParam(required = false) String genre,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) String sortBy,
                                       @RequestParam(required = false) String language,
                                       @RequestParam(required = false) Integer publishYear,
                                       @RequestParam(required = false)
                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate publishDateStart,
                                       @RequestParam(required = false)
                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate publishDateEnd,
                                       @RequestParam(required = false) Integer minDuration,
                                       @RequestParam(required = false) Integer maxDuration,
                                       @RequestParam(required = false) String quality,
                                       @RequestParam(required = false) String binding,
                                       @RequestParam(required = false) String albumType,
                                       @RequestAttribute(value = "userId", required = false) Long userId) {
        IPage<MVVO> result = mvService.pageMVs(pageQuery, area, genre, keyword, sortBy, language,
                publishYear, publishDateStart, publishDateEnd, minDuration, maxDuration, quality,
                binding, albumType, userId);
        return Result.success(result);
    }







    @ApiLog("获取歌曲的MV列表")

    @GetMapping("/song/{songId}")
    public Result<List<MVVO>> getMVsBySongId(@PathVariable("songId") Long songId,
                                              @RequestAttribute(value = "userId", required = false) Long userId) {
        List<MVVO> result = mvService.getMVsBySongId(songId, userId);
        return Result.success(result);
    }








    @ApiLog("获取歌手的MV列表")

    @GetMapping("/artist/{artistId}")
    public Result<IPage<MVVO>> getMVsByArtistId(@PathVariable("artistId") Long artistId,
                                                  PageQuery pageQuery,
                                                  @RequestAttribute(value = "userId", required = false) Long userId) {
        IPage<MVVO> result = mvService.getMVsByArtistId(artistId, pageQuery, userId);
        return Result.success(result);
    }







    @ApiLog("获取热门MV列表")

    @GetMapping("/hot")
    public Result<List<MVVO>> getHotMVs( @RequestParam(defaultValue = "20") Integer limit,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        List<MVVO> result = mvService.getHotMVs(limit, userId);
        return Result.success(result);
    }







    @ApiLog("获取最新MV列表")

    @GetMapping("/newest")
    public Result<List<MVVO>> getNewestMVs( @RequestParam(defaultValue = "20") Integer limit,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        List<MVVO> result = mvService.getNewestMVs(limit, userId);
        return Result.success(result);
    }







    @ApiLog("记录MV播放")

    @PostMapping("/play/{mvId}")
    public Result<Void> recordPlay(@PathVariable("mvId") Long mvId,
                                   @RequestAttribute(value = "userId", required = false) Long userId) {
        mvService.recordPlay(mvId, userId);
        return Result.success();
    }







    @ApiLog("收藏MV")

    @PostMapping("/favorite/{mvId}")
    public Result<Void> favoriteMV(@PathVariable("mvId") Long mvId,
                                    @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        mvService.favoriteMV(userId, mvId);
        return Result.success();
    }







    @ApiLog("取消收藏MV")

    @DeleteMapping("/favorite/{mvId}")
    public Result<Void> unfavoriteMV(@PathVariable("mvId") Long mvId,
                                      @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        mvService.unfavoriteMV(userId, mvId);
        return Result.success();
    }







    @ApiLog("获取用户收藏MV")

    @GetMapping("/favorites")
    public Result<List<MVVO>> getFavoriteMVs(
            @RequestAttribute(value = "userId", required = false) Long userId) {
        List<MVVO> result = mvService.getFavoriteMVs(userId);
        return Result.success(result);
    }







    @ApiLog("点赞MV")

    @PostMapping("/like/{mvId}")
    public Result<Void> likeMV(@PathVariable("mvId") Long mvId,
                               @RequestAttribute(value = "userId", required = false) Long userId) {
        mvService.likeMV(userId, mvId);
        return Result.success();
    }







    @ApiLog("取消点赞MV")

    @DeleteMapping("/like/{mvId}")
    public Result<Void> unlikeMV(@PathVariable("mvId") Long mvId,
                                  @RequestAttribute(value = "userId", required = false) Long userId) {
        mvService.unlikeMV(userId, mvId);
        return Result.success();
    }








    @ApiLog(value = "获取MV播放URL", logReturn = false)
    @DetectCrawler(operation = "获取MV播放链接", checkReferer = true, riskThreshold = 50)
    @GetMapping("/url/{mvId}")
    public Result<String> getPlayUrl(@PathVariable("mvId") Long mvId,
                                     @RequestParam(defaultValue = "720p") String quality,
                                     @RequestAttribute(value = "userId", required = false) Long userId,
                                     HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        String url = mvService.getPlayUrl(mvId, quality, userId);
        return Result.successData(url);
    }




    @ApiLog(value = "获取MV试看URL", logReturn = false)
    @DetectCrawler(operation = "获取MV试看链接", checkReferer = true, riskThreshold = 50)
    @GetMapping("/preview/{mvId}")
    public Result<String> getPreviewUrl(@PathVariable("mvId") Long mvId,
                                        @RequestAttribute(value = "userId", required = false) Long userId,
                                        HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        return Result.successData(mvService.getPreviewUrl(mvId, userId));
    }

    @ApiLog(value = "流式播放MV", logArgs = false, logReturn = false)
    @DetectCrawler(operation = "流式播放MV", checkReferer = false, checkBehavior = false)
    @GetMapping("/stream/{mvId}")
    public void streamMV(@PathVariable("mvId") Long mvId,
                         @RequestParam(defaultValue = "720p") String quality,
                         @RequestParam(required = false) String grant,
                         @RequestHeader(value = "Range", required = false) String range,
                         @RequestAttribute(value = "userId", required = false) Long userId,
                         HttpServletResponse response) {
        mvService.streamMV(mvId, quality, grant, range, userId, response);
    }








    @ApiLog("获取相似MV推荐")
    @GetMapping("/{mvId}/similar")
    public Result<List<MVVO>> getSimilarMVs(@PathVariable("mvId") Long mvId,
                                           @RequestParam(defaultValue = "10") Integer limit) {
        List<MVVO> result = mvService.getSimilarMVs(mvId, limit);
        return Result.success(result);
    }








    @ApiLog("获取歌手其他MV推荐")
    @GetMapping("/{mvId}/artist-mvs")
    public Result<List<MVVO>> getArtistOtherMVs(@PathVariable("mvId") Long mvId,
                                                 @RequestParam(defaultValue = "10") Integer limit) {
        List<MVVO> result = mvService.getArtistOtherMVs(mvId, limit);
        return Result.success(result);
    }
}
