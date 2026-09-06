package com.haoran.music.controller;

import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.aspect.DetectCrawler;
import com.haoran.music.common.result.Result;
import com.haoran.music.service.MVDownloadService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;





@RestController
@RequestMapping("/mv/download")
public class MVDownloadController {

    @Resource
    private MVDownloadService mvDownloadService;









    @ApiLog("下载MV")
    @DetectCrawler(operation = "MV下载", checkReferer = true, riskThreshold = 40)
    @GetMapping("/{mvId}")
    public void downloadMV(
            @PathVariable("mvId") Long mvId,
            @RequestParam(defaultValue = "720") String quality,
            @RequestAttribute(value = "userId", required = false) Long userId,
            HttpServletResponse response) {
        mvDownloadService.downloadMV(mvId, quality, userId, response);
    }









    @ApiLog("获取MV下载链接")
    @DetectCrawler(operation = "MV下载链接", checkReferer = true, riskThreshold = 40)
    @GetMapping("/{mvId}/info")
    public Result<Object> getDownloadInfo(
            @PathVariable("mvId") Long mvId,
            @RequestParam(defaultValue = "720") String quality,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        Object result = mvDownloadService.getDownloadInfo(mvId, quality, userId);
        return Result.success(result);
    }
}
