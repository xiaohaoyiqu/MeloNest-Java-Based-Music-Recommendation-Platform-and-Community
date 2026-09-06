



package com.haoran.music.common.filter;

import com.haoran.music.common.config.SecurityConfig;
import com.haoran.music.common.constant.RedisConstants;
import com.haoran.music.common.util.IpRateLimiter;
import com.haoran.music.common.util.ClientIpResolver;
import com.haoran.music.common.util.JwtUtils;
import com.haoran.music.common.util.RedisUtils;
import com.haoran.music.common.util.SpiderDetector;
import com.haoran.music.common.util.AuthCookieUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;









@Slf4j
@Component
@Order(1)
public class SecurityFilter implements Filter {

    @Resource
    private IpRateLimiter ipRateLimiter;

    @Resource
    private SpiderDetector spiderDetector;

    @Resource
    private SecurityConfig securityConfig;

    @Resource
    private JwtUtils jwtUtils;

    @Resource
    private RedisUtils redisUtils;

    @Resource
    private ClientIpResolver clientIpResolver;

    private static final String[] EXCLUDE_PATHS = {
        "/api/doc.html", "/api/swagger", "/api/webjars",
        "/api/v3/api-docs", "/api/favicon.ico", "/api/error",
        "/actuator", "/static", "/health",
        "/ws/",                                

        "/api/song/hot", "/api/song/new", "/api/song/page",
        "/api/album/list", "/api/album/hot", "/api/album/page", "/api/album/info", "/api/album/new",
        "/api/artist/page", "/api/artist/list", "/api/artist/hot", "/api/artist/new", "/api/artist/info",
        "/api/artist/letter", "/api/artist/search", "/api/artist/letters",
        "/api/playlist/hot", "/api/playlist/new", "/api/playlist/page", "/api/playlist/info",
        "/api/mv/hot", "/api/mv/newest", "/api/mv/page", "/api/mv/info", "/api/mv/stream", "/api/mv/url",
        "/api/search",
        "/api/recommend/hot", "/api/recommend/new", "/api/recommend/daily", "/api/recommend/discover", "/api/recommend/personal",
        "/api/hybrid/",
        "/api/ranking/",
        "/api/song/stream", "/api/song/url", "/api/song/info",
        "/api/comment/page", "/api/comment/hot",
        "/api/user/info",
        "/api/decoration/shop", "/api/decoration/types", "/api/decoration/detail",
        "/api/push-notifications/active", "/api/push-notifications/type/",
        "/api/curated-content/carousel"
    };

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String uri = req.getRequestURI();
        String ip = clientIpResolver.resolve(req);
        String userAgent = req.getHeader("User-Agent");


        if (isExcludePath(uri, req.getMethod())) {
            chain.doFilter(request, response);
            return;
        }


        boolean isLoggedIn = isLoggedIn(req);
        String userType = isLoggedIn ? "登录用户" : "未登录";


        if (securityConfig.isIpRateLimitEnabled()) {
            if (!ipRateLimiter.checkIpLimit(ip, isLoggedIn)) {
                log.warn("event=security_rate_limit_exceeded userType={}", userType);
                writeJsonResponse(resp, 429, new HashMap<String, Object>() {{
                    put("code", 429);
                    put("msg", isLoggedIn ? "访问过于频繁，请明天再试" : "请先登录或明天再试");
                    put("needLogin", !isLoggedIn);
                }});
                return;
            }
        }


        if (securityConfig.isUserAgentCheckEnabled()) {
            if (spiderDetector.isSpider(userAgent)) {
                log.warn("event=security_crawler_request_blocked");
                writeJsonResponse(resp, 403, new HashMap<String, Object>() {{
                    put("code", 403);
                    put("msg", "访问被拒绝，请使用正常浏览器访问");
                }});
                return;
            }
        }


        if (securityConfig.isBehaviorCheckEnabled() && securityConfig.isCaptchaEnabled()) {

            if (!isLoggedIn && ipRateLimiter.needCaptcha(ip)) {
                log.warn("event=security_captcha_required");
                writeJsonResponse(resp, 429, new HashMap<String, Object>() {{
                    put("code", 429);
                    put("msg", "访问过于频繁，请完成验证码");
                    put("needCaptcha", true);
                }});
                return;
            }
        }


        if (securityConfig.isSensitiveLogEnabled() && isSensitiveOperation(uri)) {
            log.info("event=security_sensitive_operation method={} userType={}",
                req.getMethod(), userType);
        }

        chain.doFilter(request, response);
    }




    private boolean isLoggedIn(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId != null) {
            return true;
        }

        return isCurrentToken(extractToken(request));
    }

    private String extractToken(HttpServletRequest request) {
        String token = AuthCookieUtil.resolveToken(request);
        if (token != null) {
            return token;
        }
        if (securityConfig != null && securityConfig.isQueryTokenEnabled()) {
            String queryToken = request.getParameter("token");
            return queryToken == null || queryToken.isEmpty() ? null : queryToken;
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
            log.debug("event=security_token_cache_validation_failed errorType={}",
                    e.getClass().getSimpleName());
            return false;
        }
    }




    private void writeJsonResponse(HttpServletResponse resp, int status, Map<String, Object> data)
            throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!first) json.append(",");
            json.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof String || value instanceof Character) {
                json.append("\"").append(value).append("\"");
            } else if (value instanceof Boolean) {
                json.append(value);
            } else {
                json.append(value);
            }
            first = false;
        }
        json.append("}");
        resp.getWriter().write(json.toString());
    }

    private boolean isExcludePath(String uri) {
        return isExcludePath(uri, "GET");
    }

    private boolean isExcludePath(String uri, String method) {
        if ("GET".equalsIgnoreCase(method)
                && (uri.startsWith("/api/recommend/")
                    || uri.matches("^/api/lyric/\\d+(?:/all)?$")
                    || "/api/lyric/languages".equals(uri)
                    || "/api/emoji/render".equals(uri)
                    || uri.matches("^/api/mv/[^/]+/(similar|artist-mvs)$")
                    || uri.matches("^/api/mv/artist/[^/]+$")
                    || uri.matches("^/api/album/artist/[^/]+$")
                    || uri.matches("^/api/album/[^/]+/(songs|similar|artist-albums)$")
                    || uri.matches("^/api/artist/[^/]+/(songs|similar)$"))) {
            return true;
        }
        for (String exclude : EXCLUDE_PATHS) {
            if (uri.contains(exclude)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSensitiveOperation(String uri) {
        return uri.contains("/login") || uri.contains("/register")
            || uri.contains("/password") || uri.contains("/admin")
            || uri.contains("/delete") || uri.contains("/upload");
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("event=security_filter_initialized ipLimitEnabled={} userAgentDetectionEnabled={} behaviorDetectionEnabled={} captchaEnabled={}",
            securityConfig.isIpRateLimitEnabled(),
            securityConfig.isUserAgentCheckEnabled(),
            securityConfig.isBehaviorCheckEnabled(),
            securityConfig.isCaptchaEnabled());
        log.info("event=security_filter_limits_loaded anonymousDailyLimit={} authenticatedDailyLimit={} captchaThreshold={} captchaWindowSeconds={}",
            securityConfig.getIpRateLimitPerDay(),
            securityConfig.getIpRateLimitPerDayForLoggedIn(),
            securityConfig.getCaptchaTriggerThreshold(),
            securityConfig.getCaptchaTriggerWindowSeconds());
    }

    @Override
    public void destroy() {
        log.info("event=security_filter_destroyed");
    }
}
