package com.haoran.music.common.config;

import com.haoran.music.common.util.ObjectUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

   
                      
                    
   
@Configuration
public class CorsConfig {

    private final SecurityConfig securityConfig;

    public CorsConfig(SecurityConfig securityConfig) {
        this.securityConfig = securityConfig;
    }

       
              
      
                         
       
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

                                 
        List<String> allowedOrigins = securityConfig.getAllowedOrigins();
        if (ObjectUtils.isEmpty(allowedOrigins)) {
                                                                           
            config.addAllowedOriginPattern("*");
            config.setAllowCredentials(false);
        } else {
            for (String origin : allowedOrigins) {
                if (origin != null && !origin.trim().isEmpty()) {
                    config.addAllowedOrigin(origin.trim());
                }
            }
            config.setAllowCredentials(true);
        }

                  
        config.addAllowedHeader("*");

                   
        config.addAllowedMethod("*");

                
        config.addExposedHeader("Content-Disposition");
        config.addExposedHeader("Authorization");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }
}
