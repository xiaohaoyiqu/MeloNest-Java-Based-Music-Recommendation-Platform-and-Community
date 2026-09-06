package com.haoran.music.spider;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;

   
                      
                        
  
                            
   
@Slf4j
public abstract class AbstractMusicSpider {

       
                 
       
    protected static final int DEFAULT_TIMEOUT = 30000;

       
                   
       
    protected static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

       
              
      
                           
                         
                   
       
    protected String doGet(String url, HttpHeaders headers) {
        log.debug("event=spider_get_started");

        HttpRequest request = HttpUtil.createGet(url)
                .timeout(DEFAULT_TIMEOUT)
                .header("User-Agent", DEFAULT_USER_AGENT);

                   
        if (headers != null) {
            headers.forEach((key, values) -> {
                if (values != null && !values.isEmpty()) {
                    request.header(key, values.get(0));
                }
            });
        }

        String body;
        try (cn.hutool.http.HttpResponse response = request.execute()) {
            body = response.body();
            log.debug("响应状态: {}, 内容长度: {}", response.getStatus(), body != null ? body.length() : 0);
        }
        return body;
    }

       
               
      
                           
                         
                         
                   
       
    protected String doPost(String url, String body, HttpHeaders headers) {
        log.debug("event=spider_post_started");

        HttpRequest request = HttpUtil.createPost(url)
                .timeout(DEFAULT_TIMEOUT)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Content-Type", "application/json");

                   
        if (headers != null) {
            headers.forEach((key, values) -> {
                if (values != null && !values.isEmpty()) {
                    request.header(key, values.get(0));
                }
            });
        }

        if (StrUtil.isNotBlank(body)) {
            request.body(body);
        }

        String responseBody;
        try (cn.hutool.http.HttpResponse response = request.execute()) {
            responseBody = response.body();
            log.debug("响应状态: {}, 内容长度: {}", response.getStatus(), responseBody != null ? responseBody.length() : 0);
        }
        return responseBody;
    }

       
               
      
                                    
                         
       
    protected JSONObject parseJson(String responseJson) {
        if (StrUtil.isBlank(responseJson)) {
            return null;
        }
        try {
            return JSONUtil.parseObj(responseJson);
        } catch (Exception e) {
            log.error("JSON解析失败: {}", responseJson);
            return null;
        }
    }

       
               
      
                          
                     
       
    protected String cleanHtml(String html) {
        if (StrUtil.isBlank(html)) {
            return "";
        }
        return html.replaceAll("<[^>]+>", "").trim();
    }

       
                        
      
                        
                      
       
    protected String formatDuration(Integer seconds) {
        if (seconds == null || seconds < 0) {
            return "00:00";
        }
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }

       
                       
      
                            
                 
       
    protected Integer parseDuration(String duration) {
        if (StrUtil.isBlank(duration)) {
            return 0;
        }
        try {
            String[] parts = duration.split(":");
            if (parts.length == 2) {
                int minutes = Integer.parseInt(parts[0]);
                int seconds = Integer.parseInt(parts[1]);
                return minutes * 60 + seconds;
            }
        } catch (Exception e) {
            log.warn("时长解析失败: {}", duration);
        }
        return 0;
    }

       
                 
      
                    
       
    protected abstract String getBaseUrl();

       
               
      
                                 
                   
       
    protected abstract boolean isSuccess(JSONObject responseJson);

       
                 
      
                                 
                   
       
    protected abstract String getErrorMessage(JSONObject responseJson);
}
