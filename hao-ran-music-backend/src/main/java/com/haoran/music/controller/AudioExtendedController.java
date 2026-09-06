package com.haoran.music.controller;

import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.aspect.RateLimit;
import com.haoran.music.common.aspect.RateLimitScope;
import com.haoran.music.common.context.UserContext;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.Result;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.dto.audio.DJMixGenerateDTO;
import com.haoran.music.dto.audio.SmartPlaylistSaveDTO;
import com.haoran.music.service.DJMixService;
import com.haoran.music.service.MusicHealthReportService;
import com.haoran.music.service.MusicMapService;
import com.haoran.music.service.SmartPlaylistService;
import com.haoran.music.service.TimeMachineRecommendService;
import com.haoran.music.service.UserMoodDiaryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

   
                                                                                
  
                      
   
@RestController
@RequestMapping("/audio-extended")
public class AudioExtendedController {

    private static final int MIN_REPORT_YEAR = 2000;
    private static final int MAX_THAT_DAY_LIMIT = 100;

    private final UserMoodDiaryService userMoodDiaryService;
    private final MusicMapService musicMapService;
    private final MusicHealthReportService musicHealthReportService;
    private final TimeMachineRecommendService timeMachineRecommendService;
    private final SmartPlaylistService smartPlaylistService;
    private final DJMixService djMixService;

    public AudioExtendedController(UserMoodDiaryService userMoodDiaryService,
                                   MusicMapService musicMapService,
                                   MusicHealthReportService musicHealthReportService,
                                   TimeMachineRecommendService timeMachineRecommendService,
                                   SmartPlaylistService smartPlaylistService,
                                   DJMixService djMixService) {
        this.userMoodDiaryService = userMoodDiaryService;
        this.musicMapService = musicMapService;
        this.musicHealthReportService = musicHealthReportService;
        this.timeMachineRecommendService = timeMachineRecommendService;
        this.smartPlaylistService = smartPlaylistService;
        this.djMixService = djMixService;
    }

       
                                                                               
       
    @ApiLog
    @GetMapping("/mood-curve")
    public Result<List<Map<String, Object>>> getMoodCurve(@RequestParam(defaultValue = "30") Integer days) {
        Long userId = getRequiredUserId();
        return Result.success(userMoodDiaryService.getMoodCurve(userId, days));
    }

       
                                                  
       
    @ApiLog
    @GetMapping("/mood-diary")
    public Result<?> getMoodDiary(@RequestParam String startDate,
                                  @RequestParam String endDate) {
        Long userId = getRequiredUserId();
        return Result.success(userMoodDiaryService.getMoodDiaryTimeline(
                userId,
                parseDate(startDate),
                parseDate(endDate)
        ));
    }

       
                                                
       
    @ApiLog
    @GetMapping("/mood-analysis")
    public Result<Map<String, Object>> getMoodAnalysis() {
        Long userId = getRequiredUserId();
        return Result.success(userMoodDiaryService.analyzeMoodTrend(userId));
    }

       
                                                          
       
    @ApiLog
    @GetMapping("/map")
    public Result<List<Map<String, Object>>> getMusicMap(@RequestParam(defaultValue = "all") String region,
                                                         @RequestParam(defaultValue = "500") Integer limit) {
        return Result.success(musicMapService.getMusicMapData(region, limit));
    }

       
                                                                     
       
    @ApiLog
    @GetMapping("/map/nearby")
    public Result<List<Map<String, Object>>> getNearbySongs(@RequestParam Double valence,
                                                            @RequestParam Double energy,
                                                            @RequestParam(defaultValue = "0.1") Double radius,
                                                            @RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(musicMapService.getNearbySongs(valence, energy, radius, limit));
    }

       
                                                    
       
    @ApiLog
    @GetMapping("/map/exploration/{userId}")
    public Result<Map<String, Object>> getUserExplorationMap(@PathVariable Long userId) {
        Long currentUserId = getRequiredUserId();
        if (!currentUserId.equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "Cannot access another user's exploration map");
        }
        return Result.success(musicMapService.getUserExplorationMap(userId));
    }

       
                                                                
       
    @ApiLog
    @GetMapping("/health-report")
    public Result<Map<String, Object>> getHealthReport() {
        Long userId = getRequiredUserId();
        return Result.success(musicHealthReportService.generateReport(userId));
    }

       
                                                              
       
    @ApiLog
    @GetMapping("/listening-summary")
    public Result<Map<String, Object>> getListeningSummary() {
        Long userId = getRequiredUserId();
        return Result.success(musicHealthReportService.getListeningSummary(userId));
    }

       
                                                       
       
    @ApiLog
    @GetMapping("/music-fingerprint")
    public Result<Map<String, Object>> getMusicFingerprint() {
        Long userId = getRequiredUserId();
        return Result.success(musicHealthReportService.getMusicFingerprint(userId));
    }

       
                                         
       
    @ApiLog
    @GetMapping("/yearly-report")
    public Result<Map<String, Object>> getYearlyReport(@RequestParam(required = false) Integer year) {
        Long userId = getRequiredUserId();
        return Result.success(musicHealthReportService.getYearlyReport(userId, resolveYear(year)));
    }

       
                                                   
       
    @ApiLog
    @GetMapping("/dj/mixable/{songId}")
    public Result<List<Map<String, Object>>> getMixableSongs(@PathVariable Long songId,
                                                             @RequestParam(defaultValue = "10") Integer bpmTolerance,
                                                             @RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(djMixService.getMixableSongs(songId, bpmTolerance, limit));
    }

                                                                       
    @ApiLog
    @PostMapping("/dj/generate-mix")
    @RateLimit(maxRequests = 12, timeWindowSeconds = 60, operation = "djMixGenerate",
            scope = RateLimitScope.IP, captchaBypass = false, failClosed = true,
            message = "DJ 混音请求过于频繁，请稍后再试")
    public Result<Map<String, Object>> generateMixPlaylist(@Valid @RequestBody DJMixGenerateDTO data) {
        Long songId = data.resolveBaseSongId();
        if (ObjectUtils.isEmpty(songId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "起始歌曲ID不能为空");
        }
        Integer durationMinutes = data.getDurationMinutes() == null ? 60 : data.getDurationMinutes();
        return Result.success(djMixService.generateMixPlaylist(
                songId, durationMinutes, getOptionalUserId(), data.getSelectedSongIds()));
    }

       
                                                      
       
    @ApiLog
    @GetMapping("/dj/check-mixable")
    public Result<Map<String, Object>> checkMixable(@RequestParam Long songId1,
                                                    @RequestParam Long songId2) {
        return Result.success(djMixService.checkMixable(songId1, songId2));
    }

       
                                                               
       
    @ApiLog
    @GetMapping("/dj/mix-info/{songId}")
    public Result<Map<String, Object>> getSongMixInfo(@PathVariable Long songId) {
        return Result.success(djMixService.getSongMixInfo(songId));
    }

       
                                                               
       
    @ApiLog
    @GetMapping("/timemachine/that-day")
    public Result<Map<String, Object>> getThatDayRecommendation(@RequestParam(required = false) Integer month,
                                                                  @RequestParam(required = false) Integer day,
                                                                  @RequestParam(defaultValue = "20") Integer limit) {
        LocalDate targetDate = resolveThatDayDate(month, day);
        Integer effectiveLimit = validateThatDayLimit(limit);
        Long userId = getOptionalUserId();
        if (ObjectUtils.isEmpty(userId)) {
            return Result.success(buildThatDayResult(Collections.emptyList()));
        }

        List<Map<String, Object>> recommendations =
                timeMachineRecommendService.getThatDayRecommendation(userId, targetDate, effectiveLimit);
        return Result.success(buildThatDayResult(recommendations));
    }

       
                                                
       
    @ApiLog
    @GetMapping("/timemachine/timeline")
    public Result<List<Map<String, Object>>> getMusicTimeline(@RequestParam(defaultValue = "12") Integer months) {
        Long userId = getOptionalUserId();
        if (ObjectUtils.isEmpty(userId)) {
            return Result.success(Collections.emptyList());
        }
        return Result.success(timeMachineRecommendService.getMusicTimeline(userId, months));
    }

       
                                                           
       
    @ApiLog
    @GetMapping("/timemachine/yearly-memory")
    public Result<Map<String, Object>> getYearlyMemory(@RequestParam(required = false) Integer year) {
        Long userId = getRequiredUserId();
        return Result.success(timeMachineRecommendService.getYearlyMemory(userId, resolveYear(year)));
    }

       
                                                                                     
       
    @ApiLog
    @PostMapping("/smart-playlist/generate")
    public Result<Map<String, Object>> generateSmartPlaylist(@RequestBody Map<String, Object> data) {
        String prompt = getString(data, "prompt", "description");
        if (ObjectUtils.isEmpty(prompt)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "prompt is required");
        }
        if (prompt.length() > 200) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "prompt is too long");
        }
        Integer durationMinutes = getDurationMinutes(data, 60);
        return Result.success(smartPlaylistService.generatePlaylistByPrompt(getOptionalUserId(), prompt, durationMinutes));
    }

       
                                                                    
       
    @ApiLog
    @PostMapping("/smart-playlist/by-activity")
    public Result<Map<String, Object>> generatePlaylistByActivity(@RequestBody Map<String, Object> data) {
        String activity = normalizeActivity(getString(data, "activity", "scenario"));
        if (ObjectUtils.isEmpty(activity)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "activity is required");
        }
        Integer durationMinutes = getDurationMinutes(data, 60);
        return Result.success(smartPlaylistService.generatePlaylistByActivity(getOptionalUserId(), activity, durationMinutes));
    }

       
                                                       
       
    @ApiLog
    @PostMapping("/smart-playlist/save")
    public Result<Map<String, Object>> saveGeneratedPlaylist(@Valid @RequestBody SmartPlaylistSaveDTO data) {
        Long userId = getRequiredUserId();
        return Result.success(smartPlaylistService.saveGeneratedPlaylist(
                userId, data.getName(), data.getDescription(), data.getSongIds()));
    }

       
                                                        
       
    @ApiLog
    @GetMapping("/smart-playlist/name-suggestions")
    public Result<List<String>> getPlaylistNameSuggestions(@RequestParam(defaultValue = "default") String activity) {
        return Result.success(smartPlaylistService.getPlaylistNameSuggestions(normalizeActivity(activity)));
    }

    private Long getRequiredUserId() {
        Long userId = UserContext.getCurrentUserId();
        if (ObjectUtils.isEmpty(userId)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Please login first");
        }
        return userId;
    }

    private Long getOptionalUserId() {
        return UserContext.getCurrentUserId();
    }

    private Map<String, Object> buildThatDayResult(List<Map<String, Object>> recommendations) {
        Map<String, Object> result = new HashMap<>();
        result.put("history", recommendations);
        result.put("recommendations", recommendations);
        return result;
    }

    private LocalDate resolveThatDayDate(Integer month, Integer day) {
        if (month == null && day == null) {
            return LocalDate.now();
        }
        if (month == null || day == null || month < 1 || month > 12) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "month 和 day 必须同时提供且 month 为1到12");
        }
        try {
            return LocalDate.of(LocalDate.now().getYear(), month, day);
        } catch (DateTimeException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "month 和 day 不是有效日期");
        }
    }

    private Integer validateThatDayLimit(Integer limit) {
        if (limit == null || limit < 1 || limit > MAX_THAT_DAY_LIMIT) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "limit 必须为1到100");
        }
        return limit;
    }

    private Integer resolveYear(Integer year) {
        if (ObjectUtils.isEmpty(year)) {
            return LocalDate.now().getYear();
        }
        if (year < MIN_REPORT_YEAR || year > LocalDate.now().getYear()) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "year 必须在" + MIN_REPORT_YEAR + "到当前年份之间");
        }
        return year;
    }

    private String normalizeActivity(String activity) {
        if (ObjectUtils.isEmpty(activity)) {
            return activity;
        }
        if ("sports".equals(activity) || "workout".equals(activity) || "fitness".equals(activity)) {
            return "running";
        }
        if ("study".equals(activity) || "focus".equals(activity)) {
            return "studying";
        }
        if ("sleep".equals(activity)) {
            return "sleeping";
        }
        if ("party".equals(activity)) {
            return "party";
        }
        return activity;
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "日期格式必须为 yyyy-MM-dd");
        }
    }

    private Integer getDurationMinutes(Map<String, Object> data, Integer defaultValue) {
        Integer durationMinutes = getInteger(data, "durationMinutes", "duration");
        if (ObjectUtils.isNotEmpty(durationMinutes) && durationMinutes > 0) {
            return Math.min(durationMinutes, 240);
        }

        Integer count = getInteger(data, "count", "targetCount", "songCount");
        if (ObjectUtils.isNotEmpty(count) && count > 0) {
            return Math.min(count, 60) * 4;
        }
        return defaultValue;
    }

    private String getString(Map<String, Object> data, String... keys) {
        Object value = getValue(data, keys);
        if (ObjectUtils.isEmpty(value)) {
            return "";
        }
        return String.valueOf(value);
    }

    private Long getLong(Map<String, Object> data, String... keys) {
        return toLong(getValue(data, keys));
    }

    private Integer getInteger(Map<String, Object> data, String... keys) {
        Object value = getValue(data, keys);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (ObjectUtils.isEmpty(value)) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private Long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (ObjectUtils.isEmpty(value)) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Object getValue(Map<String, Object> data, String... keys) {
        if (ObjectUtils.isEmpty(data)) {
            return null;
        }
        for (String key : keys) {
            Object value = data.get(key);
            if (ObjectUtils.isNotEmpty(value)) {
                return value;
            }
        }
        return null;
    }
}
