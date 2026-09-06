package com.haoran.music.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;





@Data
@Component
@ConfigurationProperties(prefix = "deepseek")
public class DeepSeekConfig {





    private String baseUrl = "";






    private String model = "";




    private String apiKey = "";




    private Integer timeout = 600;




    private Integer maxRetries = 0;




    private Double temperature = 0.7;




    private Integer maxTokens = 4096;
}
