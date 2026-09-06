



package com.haoran.music.controller;

import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.aspect.RateLimit;
import com.haoran.music.common.aspect.RateLimitScope;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.Result;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.IpRateLimiter;
import com.haoran.music.common.util.ClientIpResolver;
import com.haoran.music.common.util.JwtUtils;
import com.haoran.music.common.util.AuthCookieUtil;
import com.haoran.music.common.config.SecurityConfig;
import com.haoran.music.dto.user.ResetPasswordDTO;
import com.haoran.music.dto.user.SendEmailCodeDTO;
import com.haoran.music.dto.user.SendVerifyCodeDTO;
import com.haoran.music.dto.user.UserLoginDTO;
import com.haoran.music.dto.user.UserRegisterDTO;
import com.haoran.music.dto.user.VerifyEmailCodeDTO;
import com.haoran.music.dto.appeal.AccountRestrictionAppealCodeDTO;
import com.haoran.music.dto.appeal.AccountRestrictionAppealSubmitDTO;
import com.haoran.music.service.AccountRestrictionAppealService;
import com.haoran.music.service.AuthRiskService;
import com.haoran.music.service.EmailVerificationService;
import com.haoran.music.service.OnlineStatusService;
import com.haoran.music.service.PhoneVerificationService;
import com.haoran.music.service.UserService;
import com.haoran.music.service.UserStatisticsService;
import com.haoran.music.service.VerifyCodeService;
import com.haoran.music.vo.user.UserLoginVO;
import com.haoran.music.vo.user.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Resource
    private IpRateLimiter ipRateLimiter;

    @Resource
    private ClientIpResolver clientIpResolver;

    @Resource
    private UserService userService;

    @Resource
    private VerifyCodeService verifyCodeService;

    @Resource
    private PhoneVerificationService phoneVerificationService;

    @Resource
    private EmailVerificationService emailVerificationService;

    @Resource
    private AuthRiskService authRiskService;

    @Resource
    private OnlineStatusService onlineStatusService;

    @Resource
    private UserStatisticsService userStatisticsService;

    @Resource
    private AccountRestrictionAppealService accountRestrictionAppealService;

    @Resource
    private JwtUtils jwtUtils;

    @Resource
    private SecurityConfig securityConfig;




    @ApiLog("用户登录")
    @RateLimit(maxRequests = 5, timeWindowSeconds = 60, operation = "login", scope = RateLimitScope.IP,
            message = "登录尝试过于频繁，请稍后再试", captchaBypass = false)
    @PostMapping("/login")
    public Result<UserLoginVO> login(@Valid @RequestBody UserLoginDTO dto, HttpServletRequest request,
                                    HttpServletResponse response) {
        String clientIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        if (authRiskService.isLoginTemporarilyBlocked(dto.getUsername())) {
            return Result.error(429, "登录尝试过于频繁，请稍后再试");
        }

        boolean baseNeedCaptcha = verifyCodeService.needVerifyCode(clientIp, "login");
        boolean riskNeedCaptcha = authRiskService.needCaptcha(dto.getUsername(), clientIp, userAgent);
        boolean needCaptcha = baseNeedCaptcha || (riskNeedCaptcha && authRiskService.isCaptchaEnforced());

        if (dto.getCaptchaCode() != null && !dto.getCaptchaCode().isEmpty()) {
            boolean valid = verifyCodeService.verifyCode("arithmetic", clientIp, "login", dto.getCaptchaCode());
            if (!valid) {
                authRiskService.recordLoginFailure(dto.getUsername(), clientIp);
                return Result.error(400, "验证码错误，请重新输入");
            }
        } else if (needCaptcha) {
            return Result.error(403, "请先完成验证码验证");
        }

        try {
            UserLoginVO result = userService.login(dto);
            setSessionCookie(response, result.getToken());
            result.setToken(null);
            Long loginUserId = getLoginUserId(result);
            authRiskService.recordLoginSuccess(loginUserId, dto.getUsername(), clientIp, userAgent);
            onlineStatusService.updateUserOnlineActivity(loginUserId);
            userStatisticsService.recordLogin(loginUserId, clientIp);
            return Result.success(result);
        } catch (BusinessException e) {
            if (isLoginFailure(e)) {
                authRiskService.recordLoginFailure(dto.getUsername(), clientIp);
            }
            throw e;
        }
    }




    @ApiLog("用户注册")
    @RateLimit(maxRequests = 3, timeWindowSeconds = 3600, operation = "register", scope = RateLimitScope.IP,
            message = "注册次数过多，请1小时后再试", captchaBypass = false)
    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody UserRegisterDTO dto, HttpServletRequest request) {
        String clientIp = getClientIp(request);
        if (dto.getCaptchaCode() == null || dto.getCaptchaCode().isEmpty()) {
            return Result.error(403, "请先完成验证码验证");
        }

        boolean valid = verifyCodeService.verifyCode("arithmetic", clientIp, "register", dto.getCaptchaCode());
        if (!valid) {
            return Result.error(400, "验证码错误，请重新输入");
        }

        UserVO result = userService.register(dto);
        return Result.success(result);
    }




    @ApiLog("发送手机验证码")
    @RateLimit(maxRequests = 5, timeWindowSeconds = 3600, operation = "sendCode", scope = RateLimitScope.IP,
            message = "验证码发送过于频繁，请稍后再试", captchaBypass = false)
    @PostMapping("/send-code")
    public Result<Map<String, Object>> sendVerifyCode(@Valid @RequestBody SendVerifyCodeDTO dto,
                                                       HttpServletRequest request) {
        Map<String, Object> result = phoneVerificationService.sendCode(
                dto.getPhone(),
                dto.getType(),
                getClientIp(request),
                request.getHeader("User-Agent")
        );
        return Result.success(result);
    }




    @ApiLog("发送邮箱验证码")
    @RateLimit(maxRequests = 5, timeWindowSeconds = 3600, operation = "sendEmailCode", scope = RateLimitScope.IP,
            message = "验证码发送过于频繁，请稍后再试", captchaBypass = false)
    @PostMapping("/email/send-code")
    public Result<Map<String, Object>> sendEmailCode(@Valid @RequestBody SendEmailCodeDTO dto,
                                                      HttpServletRequest request) {
        Map<String, Object> result = emailVerificationService.sendCode(
                dto.getEmail(),
                dto.getType(),
                getClientIp(request),
                request.getHeader("User-Agent")
        );
        return Result.success(result);
    }




    @ApiLog("校验邮箱验证码")
    @RateLimit(maxRequests = 10, timeWindowSeconds = 300, operation = "verifyEmailCode",
            scope = RateLimitScope.IP, message = "验证码校验过于频繁，请稍后再试", captchaBypass = false)
    @PostMapping("/email/verify")
    public Result<Void> verifyEmailCode(@Valid @RequestBody VerifyEmailCodeDTO dto) {
        emailVerificationService.verifyCode(dto.getEmail(), dto.getType(), dto.getVerifyCode());
        return Result.success();
    }




    @ApiLog("重置密码")
    @RateLimit(maxRequests = 3, timeWindowSeconds = 3600, operation = "resetPassword", scope = RateLimitScope.IP,
            message = "密码重置次数过多，请稍后再试", captchaBypass = false)
    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordDTO dto) {
        userService.resetPassword(dto);
        return Result.success();
    }





    @ApiLog(value = "请求受限账号申诉验证码", logArgs = false, logReturn = false)
    @RateLimit(maxRequests = 3, timeWindowSeconds = 3600, operation = "accountRestrictionAppealCode",
            scope = RateLimitScope.IP, message = "申诉验证码请求过于频繁，请1小时后再试",
            captchaBypass = false)
    @PostMapping("/account-restriction-appeal/send-code")
    public Result<Void> sendAccountRestrictionAppealCode(
            @Valid @RequestBody AccountRestrictionAppealCodeDTO dto, HttpServletRequest request) {
        accountRestrictionAppealService.requestBoundContactCode(dto, getClientIp(request), request.getHeader("User-Agent"));
        return Result.success();
    }





    @ApiLog(value = "提交受限账号申诉", logArgs = false, logReturn = false)
    @RateLimit(maxRequests = 3, timeWindowSeconds = 3600, operation = "accountRestrictionAppealSubmit",
            scope = RateLimitScope.IP, message = "申诉提交过于频繁，请1小时后再试",
            captchaBypass = false)
    @PostMapping("/account-restriction-appeal/submit")
    public Result<Void> submitAccountRestrictionAppeal(
            @Valid @RequestBody AccountRestrictionAppealSubmitDTO dto) {
        accountRestrictionAppealService.submitBoundContactAppeal(dto);
        return Result.success();
    }



    @ApiLog("刷新Token")
    @PostMapping("/refresh")
    public Result<Map<String, String>> refreshToken(
            @RequestBody(required = false) Map<String, String> requestBody,
            HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = AuthCookieUtil.resolveToken(request);
        if ((refreshToken == null || refreshToken.isEmpty()) && requestBody != null) {
            refreshToken = requestBody.get("refreshToken");
            if (refreshToken == null || refreshToken.isEmpty()) {
                refreshToken = requestBody.get("token");
            }
        }
        String newToken = userService.refreshToken(refreshToken);
        setSessionCookie(response, newToken);
        Map<String, String> result = new HashMap<>();
        return Result.success(result);
    }




    @ApiLog("用户登出")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String token = AuthCookieUtil.resolveToken(request);
        if (token != null && userService.validateToken(token)) {
            Long userId = jwtUtils.getUserIdFromToken(token);
            userService.logout(userId);
            authRiskService.recordLogout(userId, getClientIp(request));
            onlineStatusService.userOffline(userId);
        }
        clearSessionCookie(response);
        return Result.success();
    }




    @ApiLog("验证Token")
    @PostMapping("/validate")
    public Result<Map<String, Object>> validateToken(
            @RequestBody(required = false) Map<String, String> requestBody,
            HttpServletRequest request) {
        String token = AuthCookieUtil.resolveToken(request);
        if ((token == null || token.isEmpty()) && requestBody != null) {
            token = requestBody.get("token");
        }
        boolean valid = userService.validateToken(token);

        Map<String, Object> result = new HashMap<>();
        result.put("valid", valid);
        return Result.success(result);
    }




    @ApiLog("获取验证码")
    @RateLimit(maxRequests = 10, timeWindowSeconds = 60, operation = "captchaGet", scope = RateLimitScope.IP,
            message = "验证码获取过于频繁，请稍后再试", captchaBypass = false)
    @GetMapping("/captcha")
    public Result<Map<String, Object>> getCaptcha(@RequestParam(defaultValue = "arithmetic") String type,
                                                   @RequestParam(defaultValue = "login") String scene,
                                                   HttpServletRequest request) {
        String target = getClientIp(request);
        Map<String, Object> result = verifyCodeService.generateVerifyCode(type, target, scene);
        return Result.success(result);
    }




    @ApiLog("校验验证码")
    @RateLimit(maxRequests = 10, timeWindowSeconds = 60, operation = "captchaVerify", scope = RateLimitScope.IP,
            message = "验证码校验过于频繁，请稍后再试", captchaBypass = false)
    @PostMapping("/captcha/verify")
    public Result<Map<String, Object>> verifyCaptcha(@RequestBody Map<String, String> params,
                                                      HttpServletRequest request) {
        String type = params.getOrDefault("type", "arithmetic");
        String code = params.get("code");
        String scene = params.getOrDefault("scene", "login");
        String target = getClientIp(request);

        boolean valid = verifyCodeService.verifyCode(type, target, scene, code);

        Map<String, Object> result = new HashMap<>();
        result.put("valid", valid);
        result.put("message", valid ? "验证成功" : "验证码错误");

        if (valid) {
            ipRateLimiter.addToCaptchaWhitelist(target);
            ipRateLimiter.addToCaptchaWhitelist(target, scene);
            log.info("Captcha verification passed: ip={}, scene={}", target, scene);
        }

        return Result.success(result);
    }




    @RateLimit(maxRequests = 60, timeWindowSeconds = 60, operation = "captchaCheck",
            scope = RateLimitScope.IP, message = "验证码状态查询过于频繁，请稍后再试", captchaBypass = false)
    @GetMapping("/captcha/check")
    public Result<Map<String, Object>> checkNeedCaptcha(@RequestParam(defaultValue = "login") String scene,
                                                        @RequestParam(required = false) String account,
                                                        HttpServletRequest request) {
        String target = getClientIp(request);
        boolean baseNeed = verifyCodeService.needVerifyCode(target, scene);
        Map<String, Object> riskStatus = authRiskService.getRiskStatus(account, target, request.getHeader("User-Agent"));
        boolean riskNeed = Boolean.TRUE.equals(riskStatus.get("riskNeedCaptcha"));
        boolean need = baseNeed || (riskNeed && authRiskService.isCaptchaEnforced());

        Map<String, Object> result = new HashMap<>();
        result.put("needCaptcha", need);
        result.put("scene", scene);
        return Result.success(result);
    }




    @GetMapping("/risk/status")
    public Result<Map<String, Object>> getRiskStatus(@RequestParam(required = false) String account,
                                                     HttpServletRequest request) {
        Map<String, Object> riskStatus = authRiskService.getRiskStatus(
                account, getClientIp(request), request.getHeader("User-Agent"));
        Map<String, Object> result = new HashMap<>();
        result.put("needCaptcha", Boolean.TRUE.equals(riskStatus.get("riskNeedCaptcha"))
                && authRiskService.isCaptchaEnforced());
        return Result.success(result);
    }

    private void setSessionCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(AuthCookieUtil.COOKIE_NAME, token)
                .httpOnly(true)
                .secure(securityConfig.isAuthCookieSecure())
                .sameSite("Strict")
                .path("/api")
                .maxAge(Math.max(60, securityConfig.getAuthCookieMaxAgeSeconds()))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearSessionCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(AuthCookieUtil.COOKIE_NAME, "")
                .httpOnly(true)
                .secure(securityConfig.isAuthCookieSecure())
                .sameSite("Strict")
                .path("/api")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private Long getLoginUserId(UserLoginVO result) {
        if (result == null || result.getUserInfo() == null) {
            return null;
        }
        return result.getUserInfo().getId();
    }

    private boolean isLoginFailure(BusinessException e) {
        return ResultCode.USER_NOT_EXIST.getCode().equals(e.getCode())
                || ResultCode.LOGIN_ERROR.getCode().equals(e.getCode());
    }




    private String getClientIp(HttpServletRequest request) {
        return clientIpResolver.resolve(request);
    }
}
