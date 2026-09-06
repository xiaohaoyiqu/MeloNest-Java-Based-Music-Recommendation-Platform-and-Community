package com.haoran.music.common.aspect;

import com.haoran.music.service.ApiPerformanceMetricsService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.annotation.Resource;
import java.lang.reflect.Method;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;

   
                      
                      
   
@Slf4j
@Aspect
@Component
public class ApiLogAspect {

    @Value("${haoran.api.slow-threshold-ms:800}")
    private long slowThresholdMs;

    @Resource
    private ApiPerformanceMetricsService apiPerformanceMetricsService;

       
                           
       
    @Pointcut("@annotation(com.haoran.music.common.aspect.ApiLog)")
    public void apiLogPointcut() {
    }

       
           
      
                           
                     
                           
       
    @Around("apiLogPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

                 
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        ApiLog apiLog = method.getAnnotation(ApiLog.class);

                 
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = method.getName();
        String module = StringUtils.isNotBlank(apiLog.module()) ? apiLog.module() : className;

               
        Object[] args = joinPoint.getArgs();
        String params = "";
        if (apiLog.logArgs() && args != null && args.length > 0) {
            params = summarizeArguments(args);
        }

                 
        log.debug("【{}】{}.{}() 开始执行 - 参数: {}", module, className, methodName, params);

        Object result = null;
        try {
                     
            result = joinPoint.proceed();

                     
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            if (apiLog.logReturn() && result != null) {
                String returnStr = summarizeValue(result);
                log.debug("【{}】{}.{}() 执行成功 - 耗时: {}ms, 返回: {}",
                        module, className, methodName, duration, returnStr);
            } else {
                log.debug("【{}】{}.{}() 执行成功 - 耗时: {}ms", module, className, methodName, duration);
            }
            if (duration >= slowThresholdMs) {
                log.warn("【慢接口】{}.{}() 耗时: {}ms, threshold={}ms",
                        className, methodName, duration, slowThresholdMs);
            }
            apiPerformanceMetricsService.record(module, className, methodName, duration, slowThresholdMs,
                    true, null);

            return result;
        } catch (Throwable e) {
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            log.error("【{}】{}.{}() 执行失败 - 耗时: {}ms, 异常类型: {}",
                    module, className, methodName, duration, e.getClass().getSimpleName());
            apiPerformanceMetricsService.record(module, className, methodName, duration, slowThresholdMs,
                    false, e.getClass().getSimpleName());
            throw e;
        }
    }

    String summarizeArguments(Object[] args) {
        StringBuilder summary = new StringBuilder();
        for (Object arg : args) {
            if (arg instanceof HttpServletRequest
                    || arg instanceof HttpServletResponse
                    || arg instanceof MultipartFile) {
                continue;
            }
            if (summary.length() > 0) {
                summary.append(", ");
            }
            summary.append(summarizeValue(arg));
        }
        return summary.toString();
    }

    String summarizeValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean || value.getClass().isEnum()) {
            return value.getClass().getSimpleName() + "(" + value + ")";
        }
        if (value instanceof CharSequence) {
            return "String(length=" + ((CharSequence) value).length() + ")";
        }
        if (value instanceof Collection) {
            return value.getClass().getSimpleName() + "(size=" + ((Collection<?>) value).size() + ")";
        }
        if (value instanceof Map) {
            return value.getClass().getSimpleName() + "(size=" + ((Map<?, ?>) value).size() + ")";
        }
        if (value.getClass().isArray()) {
            return value.getClass().getComponentType().getSimpleName()
                    + "[](length=" + Array.getLength(value) + ")";
        }
        return value.getClass().getSimpleName();
    }
}
