   
                      
                                                                    
   
package com.haoran.music.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

   
                                                                                  
   
@Data
@Component
@ConfigurationProperties(prefix = "verify-code")
public class VerifyCodeConfig {

                                       
    private Boolean enabled = true;

       
                                       
       
    private Integer expireSeconds = 300;

       
                                                               
       
    private Integer arithmeticMaxOperand = 10;

       
                               
       
    private Integer smsLength = 6;

       
                                 
       
    private Integer emailLength = 6;

                                        
    private Image image = new Image();

                                                                            
    private Slide slide = new Slide();

    @Data
    public static class Image {
        private Boolean enabled = false;
        private String provider = "hutool";
        private Integer width = 130;
        private Integer height = 48;
        private Integer length = 4;
        private Integer interferenceCount = 80;
    }

    @Data
    public static class Slide {
        private Boolean enabled = false;
        private String provider = "disabled";
    }
}
