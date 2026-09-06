




package com.haoran.music.common.aspect;

import com.haoran.music.common.annotation.SensitiveDataAccess;
import com.haoran.music.entity.User;
import com.haoran.music.entity.SensitiveDataLog;
import com.haoran.music.mapper.SensitiveDataLogMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.common.util.DataEncryptionUtil;
import com.haoran.music.common.util.ClientIpResolver;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;






@Aspect
@Component
public class SensitiveDataAccessAspect {

    private static final Logger log = LoggerFactory.getLogger(SensitiveDataAccessAspect.class);

    @Autowired(required = false)
    private SensitiveDataLogMapper sensitiveDataLogMapper;

    @Autowired(required = false)
    private UserMapper userMapper;

    @Autowired
    private ClientIpResolver clientIpResolver;









    @Around("@annotation(sensitiveAccess)")
    public Object controlSensitiveDataAccess(ProceedingJoinPoint joinPoint,
                                             SensitiveDataAccess sensitiveAccess) throws Throwable {
        long startTime = System.currentTimeMillis();


        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            log.warn("event=sensitive_data_access_request_context_missing action=proceed_without_audit");
            return joinPoint.proceed();
        }

        HttpServletRequest request = attributes.getRequest();


        Long userId = getUserIdFromRequest(request);
        String requestUri = request.getRequestURI();
        String ipAddress = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");


        int requireLevel = sensitiveAccess.requireLevel();
        if (!checkPermission(userId, requireLevel)) {
            log.warn("event=sensitive_data_access_denied userId={} requireLevel={} dataType={}",
                    userId, requireLevel, sensitiveAccess.dataType());


            if (sensitiveAccess.logAccess()) {
                asyncLogAccess(userId, null, "blocked", sensitiveAccess.dataType(),
                        requestUri, ipAddress, userAgent, "权限不足");
            }

            return createErrorResponse("权限不足，无法访问敏感数据");
        }


        if (sensitiveAccess.requireReVerify() && requireLevel >= 1) {
            String reVerifyToken = request.getHeader("X-Reverify-Token");
            if (reVerifyToken == null || reVerifyToken.isEmpty()) {
                log.warn("event=sensitive_data_access_reverify_required userId={} dataType={}",
                        userId, sensitiveAccess.dataType());


                if (sensitiveAccess.logAccess()) {
                    asyncLogAccess(userId, null, "blocked", sensitiveAccess.dataType(),
                            requestUri, ipAddress, userAgent, "需要二次验证");
                }

                return createErrorResponse("需要二次验证才能访问此数据");
            }
        }


        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Exception e) {
            log.error("event=sensitive_data_access_operation_failed userId={} dataType={} errorType={}",
                    userId, sensitiveAccess.dataType(), e.getClass().getSimpleName());
            if (sensitiveAccess.logAccess()) {
                asyncLogAccess(userId, null, "failed", sensitiveAccess.dataType(),
                        requestUri, ipAddress, userAgent, e.getClass().getSimpleName());
            }
            throw e;
        }


        if (sensitiveAccess.maskData() && result != null) {
            result = maskSensitiveData(result);
        }


        if (sensitiveAccess.logAccess()) {
            long duration = System.currentTimeMillis() - startTime;
            asyncLogAccess(userId, null, "success", sensitiveAccess.dataType(),
                    requestUri, ipAddress, userAgent, null);
            log.info("event=sensitive_data_access_completed userId={} dataType={} durationMs={}",
                    userId, sensitiveAccess.dataType(), duration);
        }

        return result;
    }




    private Long getUserIdFromRequest(HttpServletRequest request) {
        try {

            Object userIdAttr = request.getAttribute("userId");
            if (userIdAttr instanceof Long) {
                return (Long) userIdAttr;
            }
            if (userIdAttr instanceof Integer) {
                return ((Integer) userIdAttr).longValue();
            }
            if (userIdAttr instanceof String) {
                return Long.parseLong((String) userIdAttr);
            }

            return null;
        } catch (Exception e) {
            log.error("event=sensitive_data_access_user_resolution_failed errorType={}",
                    e.getClass().getSimpleName());
            return null;
        }
    }




    private boolean checkPermission(Long userId, int requireLevel) {
        if (userId == null) {
            return false;
        }


        if (requireLevel == 0) {
            return true;
        }


        if (requireLevel == 1) {
            return userId != null && userId > 0;
        }


        if (requireLevel == 2) {
            if (userMapper != null) {
                try {
                    User user = userMapper.selectById(userId);
                    if (user != null && "ADMIN".equals(user.getRole())) {
                        return true;
                    }
                } catch (Exception e) {
                    log.warn("event=sensitive_data_access_admin_check_failed errorType={}",
                            e.getClass().getSimpleName());
                }
            }
            return false;
        }


        if (requireLevel == 3) {
            if (userMapper != null) {
                try {
                    User user = userMapper.selectById(userId);
                    if (user != null && "ADMIN".equals(user.getRole())) {

                        return user.getUsername() != null &&
                               (user.getUsername().equals("admin") || user.getUsername().equals("superadmin"));
                    }
                } catch (Exception e) {
                    log.warn("event=sensitive_data_access_super_admin_check_failed errorType={}",
                            e.getClass().getSimpleName());
                }
            }
            return false;
        }

        return false;
    }




    private Object maskSensitiveData(Object data) {
        if (data == null) {
            return null;
        }


        if (data instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) data;
            Map<String, Object> maskedMap = new HashMap<>(map);


            if (maskedMap.containsKey("phone")) {
                Object phone = maskedMap.get("phone");
                if (phone instanceof String) {
                    maskedMap.put("phone", DataEncryptionUtil.maskPhone((String) phone));
                }
            }
            if (maskedMap.containsKey("idCard")) {
                Object idCard = maskedMap.get("idCard");
                if (idCard instanceof String) {
                    maskedMap.put("idCard", DataEncryptionUtil.maskIdCard((String) idCard));
                }
            }
            if (maskedMap.containsKey("realName")) {
                Object realName = maskedMap.get("realName");
                if (realName instanceof String) {
                    maskedMap.put("realName", DataEncryptionUtil.maskRealName((String) realName));
                }
            }
            if (maskedMap.containsKey("email")) {
                Object email = maskedMap.get("email");
                if (email instanceof String) {
                    maskedMap.put("email", DataEncryptionUtil.maskEmail((String) email));
                }
            }

            return maskedMap;
        }


        return data;
    }




    private String getClientIp(HttpServletRequest request) {
        return clientIpResolver.resolve(request);
    }




    @Async
    private void asyncLogAccess(Long userId, Long operatorId, String result, String dataType,
                              String requestUri, String ipAddress, String userAgent, String errorMsg) {
        try {
            if (sensitiveDataLogMapper != null) {
                SensitiveDataLog logEntity = new SensitiveDataLog();
                logEntity.setUserId(userId != null ? userId : 0L);
                logEntity.setOperatorId(operatorId);
                logEntity.setOperationType("read");           
                logEntity.setDataType(dataType);
                logEntity.setTargetId(userId);                      
                logEntity.setRequestUri(requestUri);
                logEntity.setIpAddress(ipAddress);
                logEntity.setUserAgent(userAgent != null && userAgent.length() > 500 ? userAgent.substring(0, 500) : userAgent);
                logEntity.setResult(result);
                logEntity.setErrorMessage(errorMsg != null && errorMsg.length() > 500 ? errorMsg.substring(0, 500) : errorMsg);
                logEntity.setCreateTime(LocalDateTime.now());

                sensitiveDataLogMapper.insert(logEntity);
            }
        } catch (Exception e) {
            log.error("event=sensitive_data_access_audit_persist_failed errorType={}",
                    e.getClass().getSimpleName());
        }

        log.info("event=sensitive_data_access_audit_recorded userId={} result={} dataType={}",
                userId, result, dataType);
    }




    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", 403);
        response.put("message", message);
        response.put("data", null);
        return response;
    }
}
