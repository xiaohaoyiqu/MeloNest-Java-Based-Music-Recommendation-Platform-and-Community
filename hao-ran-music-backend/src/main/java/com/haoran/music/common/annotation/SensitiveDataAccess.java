




package com.haoran.music.common.annotation;

import java.lang.annotation.*;





@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SensitiveDataAccess {








    int requireLevel() default 1;










    String dataType() default "";





    boolean requireReVerify() default true;




    boolean logAccess() default true;




    boolean maskData() default true;
}
