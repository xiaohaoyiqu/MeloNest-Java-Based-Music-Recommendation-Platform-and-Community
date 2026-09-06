package com.haoran.music.common.config;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.Http11NioProtocol;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

   
                      
                                       
   
@Configuration
public class TomcatConfig {

       
                                
          
                                   
                                   
                                                      
          
            
                                          
                                          
          
                                           
      
                                         
       
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatCustomizer() {
        return factory -> {
            factory.addConnectorCustomizers(connector -> {
                Http11NioProtocol protocol = (Http11NioProtocol) connector.getProtocolHandler();
                                                
                                                      
                protocol.setRelaxedQueryChars(
                        "<>[\\]^`{|}" +                        
                        "%20-%2B%2F%3F%5B%5D" +                 
                        "%E4-%E9" +                                           
                        "%C2-%DF"                                    
                );
                               
                protocol.setRelaxedPathChars(
                        "<>[\\]^`{|}"
                );
            });
        };
    }
}
