package com.haoran.music.common.aspect;

import java.lang.annotation.*;

   
                      
                                      
   
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

       
                  
       
    int maxRequests() default 10;

       
              
       
    int timeWindowSeconds() default 60;

       
            
       
    RateLimitScope scope() default RateLimitScope.USER;

       
             
       
    String operation() default "";

       
          
       
    String keyPrefix() default "";

       
               
       
    String message() default "操作过于频繁，请稍后再试";

       
                                                                          
                                                                     
       
    boolean captchaBypass() default true;

                                                                                  
    boolean failClosed() default false;
}
