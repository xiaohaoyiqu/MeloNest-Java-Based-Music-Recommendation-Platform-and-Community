package com.haoran.music.common.aspect;

import com.haoran.music.common.exception.RateLimitException;
import com.haoran.music.common.util.RedisUtils;
import com.haoran.music.common.util.IpRateLimiter;
import com.haoran.music.common.util.ClientIpResolver;
import com.haoran.music.service.VerifyCodeService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

   
                      
                        
   
@Slf4j
@Aspect
@Component
@Order(2)
@ConditionalOnProperty(prefix = "security.rate-limit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitAspect {

    @Resource
    private RedisUtils redisUtils;

    @Resource
    private IpRateLimiter ipRateLimiter;

    @Resource
    private VerifyCodeService verifyCodeService;

    @Resource
    private ClientIpResolver clientIpResolver;

    private static final String RATE_LIMIT_KEY_PREFIX = "rate:limit:";
    private static final String OPERATION_RECORD_KEY_PREFIX = "op:record:";

       
                                
       
    @Around("@annotation(com.haoran.music.common.aspect.RateLimit)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);

        if (rateLimit == null) {
            return joinPoint.proceed();
        }

                 
        Long userId = getUserIdFromRequest();

                 
        String limitKey = getLimitKey(joinPoint, rateLimit, userId);
        int maxRequests = rateLimit.maxRequests();
        int timeWindowSeconds = rateLimit.timeWindowSeconds();
        String operation = rateLimit.operation();
        if (operation.isEmpty()) {
            operation = joinPoint.getSignature().getName();
        }

                 
        if (!checkRateLimit(limitKey, maxRequests, timeWindowSeconds, rateLimit.failClosed())) {
            String clientIp = getClientIp();
            String captchaScene = "rate:" + operation;

            if (rateLimit.captchaBypass()
                    && ipRateLimiter.consumeCaptchaWhitelist(clientIp, captchaScene)) {
                resetRateLimit(limitKey);
                verifyCodeService.clearVerifyCodeRequirement(clientIp, captchaScene);
                log.info("event=rate_limit_captcha_passed userId={} operation={}",
                        userId, operation);
            } else {
                String message = rateLimit.message();
                if (message.isEmpty()) {
                    message = String.format("操作过于频繁，请在%d秒后重试", timeWindowSeconds);
                }
                if (rateLimit.captchaBypass() && clientIp != null && !clientIp.isEmpty()) {
                    verifyCodeService.requireVerifyCode(clientIp, captchaScene);
                }
                log.warn("event=rate_limit_rejected userId={} operation={} maxRequests={} "
                                + "timeWindowSeconds={} captchaScene={}",
                        userId, operation, maxRequests, timeWindowSeconds, captchaScene);
                throw new RateLimitException(message, rateLimit.captchaBypass(),
                        rateLimit.captchaBypass() ? captchaScene : null,
                        rateLimit.captchaBypass() ? "arithmetic" : null,
                        timeWindowSeconds);
            }
        }

                       
        if (userId != null && !operation.isEmpty()) {
            recordOperation(userId, operation, timeWindowSeconds);
        }

        return joinPoint.proceed();
    }

       
             
      
                     
                               
                                       
                               
       
    private boolean checkRateLimit(String key, int maxRequests, int timeWindowSeconds,
                                   boolean failClosed) {
        try {
            String redisKey = RATE_LIMIT_KEY_PREFIX + key;
            Long count = redisUtils.increment(redisKey);

                          
            if (count == 1) {
                redisUtils.expire(redisKey, timeWindowSeconds, TimeUnit.SECONDS);
            }

            log.debug("event=rate_limit_checked count={} maxRequests={}", count, maxRequests);
            return count <= maxRequests;

        } catch (Exception e) {
            log.error("event=rate_limit_check_failed errorType={}",
                    e.getClass().getSimpleName());
            if (failClosed) {
                throw new RateLimitException("上传保护服务暂时不可用，请稍后再试",
                        false, null, null, timeWindowSeconds);
            }
                                                                                          
            return true;
        }
    }

    private void resetRateLimit(String key) {
        try {
            redisUtils.delete(RATE_LIMIT_KEY_PREFIX + key);
        } catch (Exception e) {
            log.error("event=rate_limit_reset_failed errorType={}",
                    e.getClass().getSimpleName());
        }
    }

       
                   
      
                         
                            
                                    
       
    private void recordOperation(Long userId, String operation, int timeWindowSeconds) {
        try {
                                     
            long recordExpireSeconds = 86400;        
            String recordKey = OPERATION_RECORD_KEY_PREFIX + userId + ":" + operation;
            Long count = redisUtils.increment(recordKey);

            if (count == 1) {
                redisUtils.expire(recordKey, recordExpireSeconds, TimeUnit.SECONDS);
            }

                                  
            String timestampKey = OPERATION_RECORD_KEY_PREFIX + userId + ":" + operation + ":timestamps";
            long timestamp = System.currentTimeMillis();
            redisUtils.increment(timestampKey + ":" + timestamp);
            redisUtils.expire(timestampKey + ":" + timestamp, recordExpireSeconds, TimeUnit.SECONDS);

            log.debug("event=rate_limit_operation_recorded userId={} operation={} count={}",
                    userId, operation, count);
        } catch (Exception e) {
            log.error("event=rate_limit_operation_record_failed errorType={}",
                    e.getClass().getSimpleName());
        }
    }

       
                 
      
                               
       
    private Long getUserIdFromRequest() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return null;
            }
            HttpServletRequest request = attributes.getRequest();
            if (request == null) {
                return null;
            }

                                     
            Object userIdObj = request.getAttribute("userId");
            if (userIdObj != null) {
                if (userIdObj instanceof Long) {
                    return (Long) userIdObj;
                }
                if (userIdObj instanceof Integer) {
                    return ((Integer) userIdObj).longValue();
                }
                if (userIdObj instanceof String) {
                    return Long.parseLong((String) userIdObj);
                }
            }
            return null;
        } catch (Exception e) {
            log.debug("event=rate_limit_user_identity_unavailable errorType={}",
                    e.getClass().getSimpleName());
            return null;
        }
    }

       
            
                      
      
                          
                            
                         
                  
       
    private String getLimitKey(ProceedingJoinPoint joinPoint, RateLimit rateLimit, Long userId) {
        StringBuilder keyBuilder = new StringBuilder();

                 
        String operation = rateLimit.operation();
        if (operation.isEmpty()) {
            operation = joinPoint.getSignature().getName();
        }
        keyBuilder.append(operation);

                
        switch (rateLimit.scope()) {
            case USER:
                          
                keyBuilder.append(":user:").append(userId != null ? userId : "anonymous");
                break;
            case IP:
                        
                String ip = getClientIp();
                keyBuilder.append(":ip:").append(ip != null ? ip : "unknown");
                break;
            case GLOBAL:
            default:
                keyBuilder.append(":global");
                break;
        }

                  
        String prefix = rateLimit.keyPrefix();
        if (!prefix.isEmpty()) {
            keyBuilder.insert(0, prefix + ":");
        }

        return keyBuilder.toString();
    }

       
                
      
                   
       
    private String getClientIp() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return null;
            }
            HttpServletRequest request = attributes.getRequest();
            if (request == null) {
                return null;
            }

            return clientIpResolver.resolve(request);
        } catch (Exception e) {
            log.debug("event=rate_limit_client_identity_unavailable errorType={}",
                    e.getClass().getSimpleName());
            return null;
        }
    }
}
