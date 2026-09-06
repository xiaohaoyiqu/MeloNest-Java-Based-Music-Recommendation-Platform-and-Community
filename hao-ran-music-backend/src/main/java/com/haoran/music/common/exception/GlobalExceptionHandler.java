package com.haoran.music.common.exception;

import com.haoran.music.common.result.Result;
import com.haoran.music.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.ServletRequestBindingException;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.stream.Collectors;

   
                      
                                               
   
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

       
                       
      
                        
                                               
                    
       
    @ExceptionHandler(ServletRequestBindingException.class)
    public Result<?> handleServletRequestBindingException(ServletRequestBindingException e, HttpServletRequest request) {
        log.warn("event=request_binding_rejected path={} errorType={}",
                request.getRequestURI(), e.getClass().getSimpleName());
        return Result.error(ResultCode.UNAUTHORIZED);
    }

       
                               
      
                    
                                
                     
       
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("event=business_request_rejected path={} code={} errorType={}",
                request.getRequestURI(), e.getCode(), e.getClass().getSimpleName());
        return Result.error(e.getCode(), e.getMessage());
    }

       
                                
      
                      
                                
                     
       
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleMethodArgumentNotValidException(MethodArgumentNotValidException e, HttpServletRequest request) {
        String errorMsg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining("; "));
        log.warn("event=request_validation_rejected path={} fieldErrorCount={}",
                request.getRequestURI(), e.getBindingResult().getFieldErrorCount());
        return Result.error(ResultCode.PARAM_ERROR.getCode(), errorMsg);
    }

       
                
      
                      
                                
                     
       
    @ExceptionHandler(BindException.class)
    public Result<?> handleBindException(BindException e, HttpServletRequest request) {
        String errorMsg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining("; "));
        log.warn("event=request_binding_validation_rejected path={} fieldErrorCount={}",
                request.getRequestURI(), e.getBindingResult().getFieldErrorCount());
        return Result.error(ResultCode.PARAM_ERROR.getCode(), errorMsg);
    }

       
                                    
      
                      
                                
                     
       
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<?> handleConstraintViolationException(ConstraintViolationException e, HttpServletRequest request) {
        String errorMsg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining("; "));
        log.warn("event=request_constraint_rejected path={} violationCount={}",
                request.getRequestURI(), e.getConstraintViolations().size());
        return Result.error(ResultCode.PARAM_ERROR.getCode(), errorMsg);
    }

       
                      
      
                        
                                
                      
       
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<?> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        log.warn("event=request_method_rejected path={} method={}", request.getRequestURI(), e.getMethod());
        return Result.error(ResultCode.METHOD_NOT_ALLOWED);
    }

       
                 
      
                    
                                
                           
       
    @ExceptionHandler(RateLimitException.class)
    public Result<Map<String, Object>> handleRateLimitException(RateLimitException e, HttpServletRequest request) {
        log.warn("event=request_rate_limited path={} needCaptcha={} captchaScene={} retryAfterSeconds={}",
                request.getRequestURI(), e.isNeedCaptcha(), e.getCaptchaScene(), e.getRetryAfterSeconds());

        Map<String, Object> data = new HashMap<>();
        data.put("needCaptcha", e.isNeedCaptcha());
        data.put("captchaScene", e.getCaptchaScene());
        data.put("captchaType", e.getCaptchaType());
        data.put("retryAfterSeconds", e.getRetryAfterSeconds());
        Result<Map<String, Object>> result = Result.error(ResultCode.TOO_MANY_REQUESTS.getCode(), e.getMessage());
        result.setData(data);
        return result;
    }

       
                          
      
                     
                                
                         
       
    @ExceptionHandler(RuntimeException.class)
    public Result<?> handleRuntimeException(RuntimeException e, HttpServletRequest request) {
        log.error("event=runtime_exception path={} errorType={}",
                request.getRequestURI(), e.getClass().getSimpleName());

                                     
        String message = e.getMessage();
        if (message != null) {
            if (message.contains("JWT") || message.contains("Token") ||
                message.contains("userId") || message.contains("Authentication")) {
                return Result.error(ResultCode.UNAUTHORIZED);
            }
        }
        return Result.error(ResultCode.SYSTEM_BUSY);
    }

       
               
      
                     
                                
                     
       
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e, HttpServletRequest request) {
        log.error("event=unhandled_exception path={} errorType={}",
                request.getRequestURI(), e.getClass().getSimpleName());
        return Result.error(ResultCode.SYSTEM_BUSY);
    }
}
