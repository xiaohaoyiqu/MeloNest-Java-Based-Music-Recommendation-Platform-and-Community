package com.haoran.music.common.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;





@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "security.xss", name = "enabled", havingValue = "true", matchIfMissing = true)
class XssFilterConfig {

    @Bean
    public FilterRegistrationBean<XssFilter> xssFilterRegistration() {
        FilterRegistrationBean<XssFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new XssFilter());
        registration.addUrlPatterns("/*");
        registration.setName("xssFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}




@Slf4j
@WebFilter(filterName = "xssFilter", urlPatterns = "/*")
class XssFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("XSS过滤器初始化完成");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        XssHttpServletRequestWrapper xssRequest = new XssHttpServletRequestWrapper(httpRequest);
        chain.doFilter(xssRequest, response);
    }

    @Override
    public void destroy() {
        log.info("XSS过滤器销毁");
    }
}
