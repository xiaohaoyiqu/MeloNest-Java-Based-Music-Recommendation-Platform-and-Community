




package com.haoran.music.common.aspect;

import java.lang.annotation.*;















@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DetectCrawler {





    boolean checkReferer() default true;





    boolean checkBehavior() default true;





    boolean checkAccessPattern() default true;





    int riskThreshold() default 60;




    String operation() default "";




    String message() default "访问异常，请使用正常浏览器访问";
}
