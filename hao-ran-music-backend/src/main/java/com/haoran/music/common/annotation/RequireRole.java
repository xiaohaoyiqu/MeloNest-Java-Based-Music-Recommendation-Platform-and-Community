package com.haoran.music.common.annotation;

import com.haoran.music.enums.UserRole;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;





@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {






    UserRole[] value() default {UserRole.ADMIN};








    LogicalType logical() default LogicalType.OR;




    enum LogicalType {



        AND,




        OR
    }
}
