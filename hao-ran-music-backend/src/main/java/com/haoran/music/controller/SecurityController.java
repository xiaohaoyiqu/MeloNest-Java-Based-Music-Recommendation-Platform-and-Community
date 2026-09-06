



package com.haoran.music.controller;

import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.aspect.RateLimit;
import com.haoran.music.common.aspect.RateLimitScope;
import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.config.SecurityConfig;
import com.haoran.music.common.result.Result;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.constant.RedisConstants;
import com.haoran.music.entity.VirusScanRecord;
import com.haoran.music.enums.UserRole;
import com.haoran.music.mapper.VirusScanRecordMapper;
import com.haoran.music.common.util.IpRateLimiter;
import com.haoran.music.common.util.ClientIpResolver;
import com.haoran.music.common.util.JwtUtils;
import com.haoran.music.common.util.RedisUtils;
import com.haoran.music.service.VerifyCodeService;
import com.haoran.music.service.VirusScanService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;





@Slf4j
@RestController
@RequestMapping("/security")
public class SecurityController {

    @Resource
    private IpRateLimiter ipRateLimiter;

    @Resource
    private SecurityConfig securityConfig;

    @Resource
    private VerifyCodeService verifyCodeService;

    @Resource
    private JwtUtils jwtUtils;

    @Resource
    private RedisUtils redisUtils;

    @Autowired(required = false)
    private VirusScanService virusScanService;

    @Resource
    private VirusScanRecordMapper virusScanRecordMapper;

    @Resource
    private ClientIpResolver clientIpResolver;




    @ApiLog("查询IP访问限制")
    @GetMapping("/ip-limit")
    public Result getIpLimitInfo(HttpServletRequest request) {
        String ip = getClientIp(request);
        boolean isLoggedIn = isLoggedIn(request);

        long current = ipRateLimiter.getCurrentCount(ip);
        long remaining = ipRateLimiter.getRemainingCount(ip, isLoggedIn);
        int limit = isLoggedIn ? securityConfig.getIpRateLimitPerDayForLoggedIn() : securityConfig.getIpRateLimitPerDay();

        Map<String, Object> data = new HashMap<>();
        data.put("ip", ip);
        data.put("loggedIn", isLoggedIn);
        data.put("current", current);
        data.put("limit", limit);
        data.put("remaining", remaining);
        data.put("resetTime", getTodayEndTime());
        data.put("usagePercent", String.format("%.1f", (current * 100.0 / limit)));
        long windowCount = ipRateLimiter.getCurrentMinuteCount(ip);
        data.put("minuteCount", windowCount);
        data.put("windowCount", windowCount);

        return Result.success(data);
    }







    @ApiLog("获取行为检测验证码")
    @RateLimit(maxRequests = 10, timeWindowSeconds = 60, operation = "securityCaptchaGet",
            scope = RateLimitScope.IP, message = "验证码获取过于频繁，请稍后再试", captchaBypass = false)
    @GetMapping("/captcha")
    public Result getCaptcha(@RequestParam(defaultValue = "arithmetic") String type,
                            @RequestParam(defaultValue = "behavior") String scene,
                            HttpServletRequest request) {

        String ip = getClientIp(request);

        Map<String, Object> result = verifyCodeService.generateVerifyCode(type, ip, scene);
        return Result.success(result);
    }




    @ApiLog("验证码校验")
    @RateLimit(maxRequests = 10, timeWindowSeconds = 60, operation = "securityCaptchaVerify",
            scope = RateLimitScope.IP, message = "验证码校验过于频繁，请稍后再试", captchaBypass = false)
    @PostMapping("/captcha/verify")
    public Result verifyCaptcha(@RequestBody Map<String, Object> params,
                                HttpServletRequest request) {
        String ip = getClientIp(request);
        String type = String.valueOf(params.getOrDefault("type", "arithmetic"));
        String code = String.valueOf(params.get("code"));
        String scene = String.valueOf(params.getOrDefault("scene", "behavior"));

        boolean valid = verifyCodeService.verifyCode(type, ip, scene, code);

        if (valid) {

            ipRateLimiter.addToCaptchaWhitelist(ip);
            ipRateLimiter.addToCaptchaWhitelist(ip, scene);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("valid", valid);
        result.put("message", valid ? "验证通过" : "验证码错误");
        result.put("whitelistMinutes", securityConfig.getCaptchaWhitelistMinutes());

        return Result.success(result);
    }




    @RateLimit(maxRequests = 60, timeWindowSeconds = 60, operation = "securityCaptchaCheck",
            scope = RateLimitScope.IP, message = "验证码状态查询过于频繁，请稍后再试", captchaBypass = false)
    @GetMapping("/captcha/check")
    public Result checkCaptcha(@RequestParam(defaultValue = "behavior") String scene,
                               HttpServletRequest request) {
        String ip = getClientIp(request);


        long minuteCount = ipRateLimiter.getCurrentMinuteCount(ip);
        boolean sceneNeedCaptcha = verifyCodeService.needVerifyCode(ip, scene);

        Map<String, Object> data = new HashMap<>();
        data.put("scene", scene);
        data.put("needCaptcha", sceneNeedCaptcha || minuteCount >= securityConfig.getCaptchaTriggerThreshold());

        return Result.success(data);
    }





    @ApiLog("查询病毒扫描状态")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/antivirus/status")
    public Result getAntivirusStatus() {
        Map<String, Object> data = new HashMap<>();
        if (virusScanService == null) {
            data.put("enabled", false);
            data.put("available", false);
            data.put("message", "ClamAV scanner bean is disabled");
            return Result.success(data);
        }

        data.putAll(virusScanService.getStatus());
        data.put("recentErrors", countRecentScanRecords("error"));
        data.put("recentInfected", countRecentScanRecords("infected"));
        return Result.success(data);
    }




    @ApiLog("查询病毒扫描记录")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/antivirus/records")
    public Result getAntivirusRecords(@RequestParam(defaultValue = "1") Integer page,
                                      @RequestParam(defaultValue = "20") Integer size,
                                      @RequestParam(required = false) String result,
                                      @RequestParam(required = false) String businessType) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null ? 20 : Math.min(Math.max(size, 1), 100);

        LambdaQueryWrapper<VirusScanRecord> wrapper = new LambdaQueryWrapper<>();
        if (result != null && !result.trim().isEmpty()) {
            wrapper.eq(VirusScanRecord::getResult, result.trim());
        }
        if (businessType != null && !businessType.trim().isEmpty()) {
            wrapper.eq(VirusScanRecord::getBusinessType, businessType.trim());
        }
        wrapper.orderByDesc(VirusScanRecord::getCreateTime);
        return Result.success(virusScanRecordMapper.selectPage(new Page<>(safePage, safeSize), wrapper));
    }

    private long countRecentScanRecords(String result) {
        try {
            LambdaQueryWrapper<VirusScanRecord> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(VirusScanRecord::getResult, result)
                    .ge(VirusScanRecord::getCreateTime, LocalDateTime.now().minusDays(1));
            return virusScanRecordMapper.selectCount(wrapper);
        } catch (Exception e) {
            log.warn("event=antivirus_record_count_failed errorType={}", e.getClass().getSimpleName());
            return 0;
        }
    }




    private boolean isLoggedIn(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId != null) {
            return true;
        }
        return isCurrentToken(extractToken(request));
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader(CommonConstants.TOKEN_HEADER);
        if (authHeader != null && authHeader.startsWith(CommonConstants.TOKEN_PREFIX)) {
            return authHeader.substring(CommonConstants.TOKEN_PREFIX.length());
        }
        if (securityConfig != null && securityConfig.isQueryTokenEnabled()) {
            String token = request.getParameter("token");
            return token == null || token.isEmpty() ? null : token;
        }
        return null;
    }

    private boolean isCurrentToken(String token) {
        if (token == null || token.isEmpty() || !jwtUtils.validateToken(token)) {
            return false;
        }
        try {
            Long userId = jwtUtils.getUserIdFromToken(token);
            Object currentToken = redisUtils.get(RedisConstants.TOKEN_PREFIX + userId);
            return currentToken != null && token.equals(currentToken.toString());
        } catch (Exception e) {
            log.debug("SecurityController token cache validation failed: errorType={}",
                    e.getClass().getSimpleName());
            return false;
        }
    }




    private String getClientIp(HttpServletRequest request) {
        return clientIpResolver.resolve(request);
    }




    private String getTodayEndTime() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endOfDay = now.toLocalDate().atTime(23, 59, 59);
        return endOfDay.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
