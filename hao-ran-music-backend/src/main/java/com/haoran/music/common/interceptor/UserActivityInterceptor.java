package com.haoran.music.common.interceptor;

import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.service.OnlineStatusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;






@Slf4j
@Component
public class UserActivityInterceptor implements HandlerInterceptor {

    @Autowired
    private OnlineStatusService onlineStatusService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                           Object handler) throws Exception {

        Object authenticatedUserId = request.getAttribute(CommonConstants.USER_ID_KEY);
        if (authenticatedUserId == null) {
            return true;
        }

        try {
            Long userId = Long.valueOf(authenticatedUserId.toString());
            if (userId != null) {
                onlineStatusService.updateUserOnlineActivity(userId);
            }
        } catch (Exception e) {
            log.debug("event=user_activity_identity_invalid errorType={}",
                    e.getClass().getSimpleName());
        }

        return true;
    }
}
