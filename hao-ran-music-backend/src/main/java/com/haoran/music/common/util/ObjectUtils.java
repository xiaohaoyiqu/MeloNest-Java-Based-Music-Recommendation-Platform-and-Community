package com.haoran.music.common.util;

import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.Map;

   
                      
                         
   
public class ObjectUtils {

       
               
      
                    
                                
       
    public static boolean isEmpty(Object obj) {
        if (obj == null) {
            return true;
        }
        if (obj instanceof String) {
            return StringUtils.isEmpty((String) obj);
        }
        if (obj instanceof Collection) {
            return ((Collection<?>) obj).isEmpty();
        }
        if (obj instanceof Map) {
            return ((Map<?, ?>) obj).isEmpty();
        }
        if (obj.getClass().isArray()) {
            return java.lang.reflect.Array.getLength(obj) == 0;
        }
        return false;
    }

       
                
      
                    
                                
       
    public static boolean isNotEmpty(Object obj) {
        return !isEmpty(obj);
    }

       
                  
      
                          
                                      
       
    public static boolean isAllEmpty(Object... objects) {
        if (objects == null) {
            return true;
        }
        for (Object obj : objects) {
            if (isNotEmpty(obj)) {
                return false;
            }
        }
        return true;
    }

       
                    
      
                          
                                      
       
    public static boolean isAnyEmpty(Object... objects) {
        if (objects == null) {
            return true;
        }
        for (Object obj : objects) {
            if (isEmpty(obj)) {
                return true;
            }
        }
        return false;
    }

       
                          
      
                           
                            
                             
                     
       
    public static <T> T defaultIfNull(T obj, T defaultObj) {
        return isEmpty(obj) ? defaultObj : obj;
    }

       
                 
      
                      
                      
                                
       
    public static boolean equals(Object obj1, Object obj2) {
        if (obj1 == obj2) {
            return true;
        }
        if (obj1 == null || obj2 == null) {
            return false;
        }
        return obj1.equals(obj2);
    }

       
                    
      
                      
                        
                        
                       
  
    @SuppressWarnings("unchecked")
    public static <T> java.util.List<T> castList(Object obj, Class<T> clazz) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof java.util.List) {
            return (java.util.List<T>) obj;
        }
        return null;
    }

       
                   
      
                    
                      
  
    @SuppressWarnings("unchecked")
    public static <K, V> java.util.Map<K, V> castMap(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof java.util.Map) {
            return (java.util.Map<K, V>) obj;
        }
        return null;
    }

       
                      
      
                    
                         
  
    public static String castString(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof String) {
            return (String) obj;
        }
        return obj.toString();
    }
}
