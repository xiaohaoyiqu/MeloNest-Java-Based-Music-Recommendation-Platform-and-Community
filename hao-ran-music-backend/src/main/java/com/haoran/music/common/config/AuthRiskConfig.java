   
                      
                                                          
   
package com.haoran.music.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

   
                                                                                          
   
@Data
@Component
@ConfigurationProperties(prefix = "auth-risk")
public class AuthRiskConfig {

                                                                        
    private Boolean enabled = true;

                                                                                              
    private Boolean enforceCaptcha = true;

    private Integer loginFailureWindowMinutes = 15;
    private Integer accountFailureThreshold = 3;
    private Integer accountBlockThreshold = 10;
    private Integer ipFailureThreshold = 10;
    private Integer forceCaptchaMinutes = 30;
    private Integer logoutWindowMinutes = 10;
    private Integer logoutLoginThreshold = 3;
    private Integer ipChangeWindowHours = 24;
    private Integer ipChangeThreshold = 3;
}
