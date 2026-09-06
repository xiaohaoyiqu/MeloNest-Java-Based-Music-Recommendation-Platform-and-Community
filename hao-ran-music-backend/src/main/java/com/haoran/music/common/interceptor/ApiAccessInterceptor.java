package com.haoran.music.common.interceptor;

import com.haoran.music.common.util.SpiderDetector;
import com.haoran.music.common.util.ClientIpResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;





@Slf4j
@Component
@Order(2)
public class ApiAccessInterceptor implements HandlerInterceptor {


    @Resource
    private SpiderDetector spiderDetector;

    @Resource
    private ClientIpResolver clientIpResolver;





    private static final String[] SENSITIVE_PATHS = {
        "/api/auth/login",
        "/api/auth/register",
        "/api/auth/send-code",
        "/api/user/verify",
        "/api/song/detail",
        "/api/comment",
        "/api/rating"
    };

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestUri = request.getRequestURI();
        String clientIp = clientIpResolver.resolve(request);
        String userAgent = request.getHeader("User-Agent");

        log.debug("event=api_access_check_started");


        if (spiderDetector.isSpider(userAgent)) {
            if (isSensitivePath(requestUri)) {
                log.warn("event=api_access_spider_blocked reason=sensitive_path");
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":403,\"message\":\"Access denied\"}");
                return false;
            }
        }

        return true;
    }






    private boolean isSensitivePath(String requestUri) {
        for (String path : SENSITIVE_PATHS) {
            if (requestUri.contains(path)) {
                return true;
            }
        }
        return false;
    }






}
