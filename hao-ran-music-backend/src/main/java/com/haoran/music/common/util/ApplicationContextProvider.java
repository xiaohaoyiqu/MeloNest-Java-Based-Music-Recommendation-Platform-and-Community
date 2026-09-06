package com.haoran.music.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;





@Slf4j
@Component
public class ApplicationContextProvider implements ApplicationContextAware {

    private static ApplicationContext applicationContext;





    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }






    public static Object getBean(String name) {
        return applicationContext.getBean(name);
    }







    public static <T> T getBean(Class<T> clazz) {
        return applicationContext.getBean(clazz);
    }








    public static <T> T getBean(String name, Class<T> clazz) {
        return applicationContext.getBean(name, clazz);
    }






    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        applicationContext = context;
        log.info("ApplicationContext初始化完成");
    }






    public static boolean containsBean(String name) {
        return applicationContext.containsBean(name);
    }






    public static boolean isSingleton(String name) {
        return applicationContext.isSingleton(name);
    }






    public static Class<?> getType(String name) {
        return applicationContext.getType(name);
    }
}
