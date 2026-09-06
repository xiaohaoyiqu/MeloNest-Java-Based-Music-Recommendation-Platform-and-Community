   
                      
                        
   

package com.haoran.music.common.annotation;

import java.lang.annotation.*;

   
           
                  
   
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SensitiveInfo {

       
           
       
    SensitiveType value() default SensitiveType.CUSTOM;

       
              
                                               
                  
       
    String pattern() default "";

       
           
       
    String replacement() default "****";

       
             
       
    enum SensitiveType {
                            
        PHONE,
                            
        ID_CARD,
                          
        REAL_NAME,
                             
        EMAIL,
                         
        ADDRESS,
                            
        BANK_CARD,
                    
        CUSTOM
    }
}
