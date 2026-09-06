package com.haoran.music.common.interceptor;

import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.constant.RedisConstants;
import com.haoran.music.common.result.Result;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.JwtUtils;
import com.haoran.music.common.util.RedisUtils;
import com.haoran.music.common.util.AuthCookieUtil;
import com.haoran.music.service.SimpleUserClassificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;

   
                      
                                          
   
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);

    private final JwtUtils jwtUtils;
    private final RedisUtils redisUtils;
    private final SimpleUserClassificationService userClassificationService;

    private static final List<String> OPTIONAL_AUTH_PATHS = Arrays.asList(
            "/api/auth/",
            "/api/auth/login",
            "/api/auth/register",
            "/api/system/health",
            "/api/song/like/status/",
            "/api/song/play/",
            "/api/mv/play/",
            "/api/playlist/hot",
            "/api/song/hot",
            "/api/song/new",
            "/api/mv/hot",
            "/api/mv/newest",
            "/api/playlist/page",
            "/api/album/page",
            "/api/album/hot",
            "/api/artist/page",
            "/api/artist/list",
            "/api/artist/hot",
            "/api/artist/letter/",
            "/api/artist/search",
            "/api/artist/letters",
            "/api/search",
            "/api/search/",
            "/api/recommend/daily",
            "/api/recommend/discover",
            "/api/recommend/personal",
            "/api/decoration/shop",
            "/api/decoration/types",
            "/api/decoration/detail/",
            "/api/curated-content/carousel",
                                       
            "/api/artist/info",
            "/api/comment/page",
            "/api/comment/hot",
            "/api/album/info",
            "/api/mv/info",
            "/api/song/info",
                                     
            "/api/audio-feature/scenario/",
            "/api/audio-feature/scenarios",
            "/api/audio-feature/mood",
            "/api/audio-feature/similar/",
            "/api/audio-feature/bpm",
            "/api/audio-feature/features/",
            "/api/audio-feature/features/batch",
            "/api/audio-feature/similarity",
                                     
            "/api/playlist-audio/analyze/",
            "/api/playlist-audio/tags/",
            "/api/playlist-audio/distribution/",
            "/api/playlist-audio/consistency/",
            "/api/playlist-audio/recommend",
                                     
            "/api/audio-extended/map",
            "/api/audio-extended/map/nearby",
            "/api/audio-extended/timemachine/that-day",
            "/api/audio-extended/timemachine/timeline",
            "/api/audio-extended/smart-playlist/name-suggestions"
    );

    private static final List<String> OPTIONAL_GET_AUTH_PATHS = Arrays.asList(
            "/api/ranking/",
            "/api/recommend/",
            "/api/hybrid/",
            "/api/playlist/info/",
            "/api/album/",
            "/api/artist/",
            "/api/song/",
            "/api/lyric/",
            "/api/emoji/render",
            "/api/mv/",
            "/api/audio-extended/dj/",
            "/api/music-square/posts",
            "/api/music-square/posts/",
            "/api/music-square/topics/hot",
            "/api/music-square/events/featured",
            "/api/music-square/events/",
            "/api/music-square/vote/hot",
            "/api/music-square/recommend-users",
            "/api/music-square/marketplace",
            "/api/music-square/marketplace/",
            "/api/user/profile/",
            "/api/user/following/",
            "/api/user/followers/",
            "/api/user/follow/stats/",
            "/api/user/is-following/",
            "/api/push-notifications/active",
            "/api/push-notifications/type/",
            "/api/curated-content/carousel",
            "/api/external/",
            "/api/external-content/list/",
            "/api/external-content/recommend",
            "/api/private-attachments/assets/"
    );

    private static final List<String> PROTECTED_GET_AUTH_PATHS = Arrays.asList(
            "/api/music-square/posts/my",
            "/api/music-square/topics/personalized",
            "/api/music-square/vote/stats",
            "/api/music-square/vote/personalized",
            "/api/music-square/recommend-users/personalized",
            "/api/music-square/marketplace/my",
            "/api/music-square/marketplace/favorites",
            "/api/music-square/monitor/operation-stats",
            "/api/user/profile/segment/stats",
            "/api/user/profile/churn/predict/",
            "/api/song/favorite/list",
            "/api/song/like/favorite/list",
            "/api/song/favorite/details",
            "/api/song/like/favorite/details",
            "/api/song/favorite/count",
            "/api/song/like/favorite/count",
            "/api/song/rating/user/list",
            "/api/song/rating/stats",
            "/api/lyric/local/",
            "/api/lyric/translate/status/",
            "/api/lyric/test/deepseek",
            "/api/song/resource-request/check",
            "/api/song/resource-request/today-count",
            "/api/song/resource-request/my",
            "/api/song/resource-request/all",
            "/api/song/resource-request/pending-count",
            "/api/mv/favorites",
            "/api/album/favorites",
            "/api/recommend/preferences/tags",
            "/api/recommend/resource/requested",
            "/api/recommend/resource/preference",
            "/api/recommend/resource/unmet-analysis",
            "/api/hybrid/recommend-with-reason",
            "/api/hybrid/cold-start",
            "/api/hybrid/discovery",
            "/api/hybrid/mood/",
            "/api/hybrid/weights",
            "/api/hybrid/models/status",
            "/api/audio-feature/mood/analysis"
    );

    public AuthInterceptor(JwtUtils jwtUtils, RedisUtils redisUtils,
                           SimpleUserClassificationService userClassificationService) {
        this.jwtUtils = jwtUtils;
        this.redisUtils = redisUtils;
        this.userClassificationService = userClassificationService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }

        String requestUri = request.getRequestURI();
        log.debug("event=auth_request_started");

        boolean isOptionalAuth = isOptionalAuthPath(requestUri, request.getMethod());
        log.debug("event=auth_policy_resolved optionalAuth={}", isOptionalAuth);

        String token = getTokenFromRequest(request);
        log.debug("event=auth_token_presence_checked tokenPresent={} optionalAuth={}", token != null, isOptionalAuth);

        if (StringUtils.isEmpty(token)) {
            if (isOptionalAuth) {
                log.debug("event=auth_optional_request_allowed reason=token_missing");
                return true;
            }
            log.warn("event=auth_request_rejected reason=token_missing");
            writeErrorResponse(response, ResultCode.UNAUTHORIZED);
            return false;
        }

        boolean valid = jwtUtils.validateToken(token);
        log.debug("event=auth_token_validated valid={}", valid);
        if (!valid) {
            log.warn("event=auth_token_invalid_or_expired optionalAuth={}", isOptionalAuth);
            if (isOptionalAuth) {
                log.debug("event=auth_optional_request_allowed reason=token_invalid");
                return true;
            }
            log.warn("event=auth_request_rejected reason=token_invalid");
            writeErrorResponse(response, ResultCode.TOKEN_EXPIRED);
            return false;
        }

        try {
            Long userId = jwtUtils.getUserIdFromToken(token);
            if (!isCurrentToken(userId, token)) {
                log.warn("event=auth_request_rejected reason=token_not_current userId={}", userId);
                if (isOptionalAuth) {
                    return true;
                }
                writeErrorResponse(response, ResultCode.TOKEN_EXPIRED);
                return false;
            }
            if (Boolean.TRUE.equals(userClassificationService.isRestricted(userId))) {
                log.warn("event=auth_request_rejected reason=user_restricted userId={}", userId);
                userClassificationService.forceLogout(userId);
                writeErrorResponse(response, ResultCode.FORBIDDEN);
                return false;
            }
            if (Boolean.TRUE.equals(userClassificationService.isFrozen(userId))
                    && !isSafeReadMethod(request.getMethod())) {
                log.warn("event=auth_request_rejected reason=frozen_user_write userId={} method={}",
                        userId, request.getMethod());
                writeErrorResponse(response, ResultCode.FORBIDDEN);
                return false;
            }
            log.debug("event=auth_request_allowed userId={}", userId);
            request.setAttribute(CommonConstants.USER_ID_KEY, userId);
            return true;
        } catch (Exception e) {
            log.warn("event=auth_token_parse_failed errorType={}", e.getClass().getSimpleName());
            if (isOptionalAuth) {
                log.debug("event=auth_optional_request_allowed reason=token_parse_failed");
                return true;
            }
            writeErrorResponse(response, ResultCode.TOKEN_INVALID);
            return false;
        }
    }

    private boolean isOptionalAuthPath(String requestUri, String method) {
        if (matchesAnyPath(OPTIONAL_AUTH_PATHS, requestUri)) {
            return true;
        }
        if ("GET".equalsIgnoreCase(method) && !matchesAnyPath(PROTECTED_GET_AUTH_PATHS, requestUri)) {
            return matchesAnyPath(OPTIONAL_GET_AUTH_PATHS, requestUri);
        }
        return false;
    }

    private boolean matchesAnyPath(List<String> paths, String requestUri) {
        for (String path : paths) {
            if (requestUri.equals(path) || (path.endsWith("/") && requestUri.startsWith(path))) {
                return true;
            }
        }
        return false;
    }

    private boolean isSafeReadMethod(String method) {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        return AuthCookieUtil.resolveToken(request);
    }

    private boolean isCurrentToken(Long userId, String token) {
        Object currentToken = redisUtils.get(RedisConstants.TOKEN_PREFIX + userId);
        return currentToken != null && token.equals(currentToken.toString());
    }

    private void writeErrorResponse(HttpServletResponse response, ResultCode resultCode) throws Exception {
        if (ResultCode.FORBIDDEN.equals(resultCode)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
        response.setContentType("application/json;charset=UTF-8");
        Result<Void> result = Result.error(resultCode);
        response.getWriter().write(com.alibaba.fastjson2.JSON.toJSONString(result));
    }
}
