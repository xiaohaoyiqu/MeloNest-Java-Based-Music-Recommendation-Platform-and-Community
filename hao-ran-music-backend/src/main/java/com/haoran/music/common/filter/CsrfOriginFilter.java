   
                      
   

package com.haoran.music.common.filter;

import com.haoran.music.common.config.SecurityConfig;
import com.haoran.music.common.util.AuthCookieUtil;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

                                                                                          
@Component
@Order(0)
public class CsrfOriginFilter implements Filter {

    private final SecurityConfig securityConfig;

    public CsrfOriginFilter(SecurityConfig securityConfig) {
        this.securityConfig = securityConfig;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        if (isSafeMethod(request.getMethod()) || !AuthCookieUtil.hasSessionCookie(request)) {
            chain.doFilter(request, response);
            return;
        }

        String origin = request.getHeader("Origin");
        String fetchSite = request.getHeader("Sec-Fetch-Site");
        if ((origin != null && !allowedOrigins().contains(origin.trim()))
                || (origin == null && "cross-site".equalsIgnoreCase(fetchSite))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"请求来源不受信任\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private Set<String> allowedOrigins() {
        if (securityConfig.getAllowedOrigins() == null) {
            return Collections.emptySet();
        }
        Set<String> origins = new HashSet<>();
        for (String origin : securityConfig.getAllowedOrigins()) {
            if (origin != null && !origin.trim().isEmpty()) {
                origins.add(origin.trim());
            }
        }
        return origins;
    }

    private boolean isSafeMethod(String method) {
        return "GET".equalsIgnoreCase(method)
                || "HEAD".equalsIgnoreCase(method)
                || "OPTIONS".equalsIgnoreCase(method);
    }
}
