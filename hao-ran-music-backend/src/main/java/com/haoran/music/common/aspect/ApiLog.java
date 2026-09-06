package com.haoran.music.common.aspect;

import java.lang.annotation.*;

   
                      
                      
   
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiLog {

       
           
       
    String value() default "";

       
           
       
    String module() default "";

       
             
       
    boolean logArgs() default true;

       
              
       
    boolean logReturn() default true;
}
