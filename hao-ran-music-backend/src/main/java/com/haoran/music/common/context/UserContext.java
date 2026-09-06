package com.haoran.music.common.context;

import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.util.ClientIpResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;

   
                      
                                     
   
@Slf4j
@Component
public class UserContext {

    @Autowired
    private ClientIpResolver clientIpResolver;

    private static UserContext instance;

    @PostConstruct
    public void init() {
        instance = this;
    }

       
               
      
                             
       
    public static Long getCurrentUserId() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                log.debug("[UserContext] RequestAttributes为空，无法获取用户ID");
                return null;
            }

            HttpServletRequest request = attributes.getRequest();

                                         
            Object userId = request.getAttribute(CommonConstants.USER_ID_KEY);
            if (userId != null) {
                try {
                    return Long.valueOf(userId.toString());
                } catch (NumberFormatException e) {
                    log.warn("[UserContext] 用户ID格式错误: {}", userId);
                }
            }

        } catch (Exception e) {
            log.error("[UserContext] 获取用户ID失败");
        }
        return null;
    }

       
                      
      
                              
                   
       
    public static Long getCurrentUserIdOrDefault(Long defaultValue) {
        Long userId = getCurrentUserId();
        return userId != null ? userId : defaultValue;
    }

       
                
      
                                 
       
    public static boolean isLoggedIn() {
        return getCurrentUserId() != null;
    }

       
                
      
                   
       
    public static String getClientIp() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return null;
            }

            HttpServletRequest request = attributes.getRequest();

            return instance == null ? request.getRemoteAddr() : instance.clientIpResolver.resolve(request);

        } catch (Exception e) {
            log.error("[UserContext] 获取客户端IP失败");
            return null;
        }
    }
}
