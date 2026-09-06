package com.haoran.music.controller;

import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.aspect.RateLimit;
import com.haoran.music.common.aspect.RateLimitScope;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.result.Result;
import com.haoran.music.entity.Lyric;
import com.haoran.music.entity.MusicLanguage;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.LyricService;
import lombok.extern.slf4j.Slf4j;
import javax.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

   
                      
                     
   

@Slf4j
@RestController
@RequestMapping("/lyric")
public class LyricController {

    @Resource
    private LyricService lyricService;

       
             
      
                         
                         
                   
       
    @ApiLog("获取歌曲歌词")

    @GetMapping("/{songId}")
    public Result<Lyric> getSongLyric(@PathVariable("songId") Long songId,
                                      @RequestAttribute(value = "userId", required = false) Long userId) {
        Lyric result = lyricService.getSongLyric(songId, userId);
        return Result.success(result);
    }

       
                
      
                         
                   
       
    @ApiLog("获取歌曲多语言歌词")

    @GetMapping("/{songId}/all")
    public Result<List<Lyric>> getSongLyrics(
            @PathVariable("songId") Long songId,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        List<Lyric> result = lyricService.getSongLyrics(songId, userId);
        return Result.success(result);
    }

       
           
      
                            
                            
                          
                            
                            
                   
       
    @ApiLog("保存歌词")

    @PostMapping
    @RateLimit(maxRequests = 20, timeWindowSeconds = 60, operation = "formalLyricSave",
            scope = RateLimitScope.USER, captchaBypass = false, failClosed = true,
            message = "歌词发布操作过于频繁，请稍后再试")
    public Result<Boolean> saveLyric(@RequestParam("songId") Long songId,
                                     @RequestParam("content") String content,
                                     @RequestParam(value = "language", defaultValue = "zh-CN") String language,
                                     @RequestParam(value = "lyricType", defaultValue = "1") Integer lyricType,
                                     @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
                                                         
        Boolean result = lyricService.saveLyric(songId, content, language, lyricType,
                CommonConstants.LYRIC_SOURCE_ADMIN, userId);
        return Result.success(result);
    }

       
           
      
                          
                           
                   
       
    @ApiLog("删除歌词")

    @DeleteMapping("/{lyricId}")
    @RateLimit(maxRequests = 20, timeWindowSeconds = 60, operation = "formalLyricDelete",
            scope = RateLimitScope.USER, captchaBypass = false, failClosed = true,
            message = "歌词删除操作过于频繁，请稍后再试")
    public Result<Boolean> deleteLyric(@PathVariable("lyricId") Long lyricId,
                                       @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        Boolean result = lyricService.deleteLyric(lyricId, userId);
        return Result.success(result);
    }

       
             
      
                                 
                                 
                                           
                                
                     
       
    @ApiLog("请求翻译歌词")

    @PostMapping("/translate")
    @RateLimit(maxRequests = 5, timeWindowSeconds = 60, operation = "lyricTranslationCreate",
            scope = RateLimitScope.USER, captchaBypass = false, failClosed = true,
            message = "AI 歌词翻译请求过于频繁，请稍后再试")
    public Result<Long> requestTranslation(@RequestParam("songId") Long songId,
                                            @RequestParam("targetLanguage") String targetLanguage,
                                            @RequestParam(value = "lyricType", defaultValue = "2") Integer lyricType,
                                            @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        Long taskId = lyricService.requestTranslation(songId, targetLanguage, lyricType, userId);
        return Result.success(taskId);
    }

       
             
      
                           
                   
       
    @ApiLog("获取翻译状态")

    @GetMapping("/translate/status/{taskId}")
    @RateLimit(maxRequests = 30, timeWindowSeconds = 60, operation = "lyricTranslationStatus",
            scope = RateLimitScope.USER, captchaBypass = false,
            message = "翻译状态查询过于频繁，请稍后再试")
    public Result<Map<String, Object>> getTranslationStatus(
            @PathVariable("taskId") Long taskId,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        Map<String, Object> result = lyricService.getTranslationStatus(taskId, userId);
        return Result.success(result);
    }

       
                          
       
    @ApiLog("找回最近歌词翻译任务")
    @GetMapping("/translate/latest/{songId}")
    @RateLimit(maxRequests = 30, timeWindowSeconds = 60, operation = "lyricTranslationLatest",
            scope = RateLimitScope.USER, captchaBypass = false,
            message = "翻译委托单查询过于频繁，请稍后再试")
    public Result<Map<String, Object>> getLatestTranslationStatus(
            @PathVariable("songId") Long songId,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(lyricService.getLatestTranslationStatus(songId, userId));
    }

       
                
      
                   
       
    @ApiLog("获取支持的语言")

    @GetMapping("/languages")
    public Result<List<MusicLanguage>> getSupportedLanguages() {
        List<MusicLanguage> result = lyricService.getSupportedLanguages();
        return Result.success(result);
    }

       
                   
      
                   
       
    @ApiLog("测试DeepSeek连接")

    @GetMapping("/test/deepseek")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    public Result<Map<String, Object>> testDeepSeek() {
        Map<String, Object> result = lyricService.testDeepSeekConnection();
        return Result.success(result);
    }

       
                     
      
                         
                   
       
    @ApiLog("读取本地歌词文件")
    @GetMapping("/local/{songId}")
    public Result<String> getLocalLyric(@PathVariable("songId") Long songId) {
        try {
            String lyric = lyricService.getLocalLyricFromFile(songId);
            if (lyric != null && !lyric.isEmpty()) {
                return Result.successData(lyric);
            } else {
                return Result.error("未找到本地歌词文件");
            }
        } catch (Exception e) {
            log.error("event=local_lyric_read_failed songId={} errorType={}",
                    songId, e.getClass().getSimpleName());
            return Result.error(500, "读取本地歌词失败，请稍后重试");
        }
    }

    @ApiLog("同步node3歌词")
    @PostMapping("/local/{songId}/sync")
    @RequireRole({UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN})
    public Result<Boolean> syncLocalLyric(
            @PathVariable("songId") Long songId,
            @RequestParam(value = "lyricType", defaultValue = "1") Integer lyricType,
            @RequestParam(value = "language", required = false) String language,
            @RequestAttribute(value = "userId", required = false) Long operatorId) {
        if (operatorId == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(lyricService.syncLocalLyric(songId, operatorId, lyricType, language));
    }

    @ApiLog("预览或校准node3歌词")
    @PostMapping("/local/calibrate")
    @RequireRole({UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN})
    public Result<Map<String, Object>> calibrateNode3Lyrics(
            @RequestParam(value = "limit", defaultValue = "100") Integer limit,
            @RequestParam(value = "dryRun", defaultValue = "true") Boolean dryRun,
            @RequestParam(value = "includeTranslations", defaultValue = "false") Boolean includeTranslations,
            @RequestAttribute(value = "userId", required = false) Long operatorId) {
        if (operatorId == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(lyricService.calibrateNode3Lyrics(
                operatorId, limit, dryRun, includeTranslations));
    }
}
