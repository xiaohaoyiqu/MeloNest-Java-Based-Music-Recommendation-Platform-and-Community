   
                      
                                        
   

package com.haoran.music.common.aspect;

import java.lang.annotation.*;

   
            
  
        
                                   
                                        
                                   
  
           
                                     
                        
                         
                         
   
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface BlockCrawlerContent {

       
           
                   
                       
                     
                  
       
    String type() default "all";

       
                      
                 
       
    String typeParam() default "resourceType";

       
                      
             
       
    String idParam() default "resourceId";

       
               
                        
       
    boolean checkPurchased() default true;

       
                  
                       
                       
                       
       
    boolean checkVipSource() default true;

       
               
       
    String message() default "访问受限，此内容禁止批量获取";
}
