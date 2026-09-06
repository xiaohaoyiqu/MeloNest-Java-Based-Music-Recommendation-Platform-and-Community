package com.haoran.music.common.util;

import com.haoran.music.common.config.SecurityConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

   
                      
                                  
   
@Slf4j
@Component
public class SpiderDetector {

    @Resource
    private SecurityConfig securityConfig;

       
                        
       
    private static final Set<String> BLOCKED_AGENTS = new HashSet<>(Arrays.asList(
        "python-requests",               
        "scrapy",                         
        "curl",                       
        "wget",                       
        "apache-httpclient",                     
        "okhttp",                     
        "retrofit",                     
        "axios",                         
        "fetch",                             
        "httpie",                     
        "postman",                           
        "swagger",                           
        "insomnia",                           
        "bot",                       
        "spider",                       
        "crawler",                       
        "scraper"                        
    ));

       
                            
       
    private static final Set<String> WHITELIST_AGENTS = new HashSet<>(Arrays.asList(
        "mozilla",                        
        "chrome",                        
        "safari",                        
        "firefox",                        
        "edge",                        
        "opr/",                         
        "microMessenger",              
        "qqbrowser",                 
        "ucbrowser",                 
        "baidubrowser",              
        "sogoumobile",               
        "xiaomi",                    
        "huaweibrowser",             
        "samsungbrowser",            
        "opr"                        
    ));

       
                        
      
                                     
                                        
       
    public boolean isSpider(String userAgent) {
        if (!securityConfig.isUserAgentCheckEnabled()) {
            return false;          
        }

        if (userAgent == null || userAgent.isEmpty()) {
            log.warn("[SpiderDetector] User-Agent为空，判定为爬虫");
            return true;            
        }

        String ua = userAgent.toLowerCase();

                
        for (String whitelist : WHITELIST_AGENTS) {
            if (ua.contains(whitelist)) {
                log.debug("[SpiderDetector] User-Agent在白名单中: {}", userAgent);
                return false;           
            }
        }

                
        for (String blocked : BLOCKED_AGENTS) {
            if (ua.contains(blocked)) {
                log.warn("[SpiderDetector] 检测到爬虫User-Agent: {}, 关键词: {}", userAgent, blocked);
                return true;        
            }
        }

                                
        log.debug("[SpiderDetector] 未识别的User-Agent: {}", userAgent);
        return false;
    }

       
                             
      
                                     
                             
       
    public String getUserAgentType(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return "Unknown";
        }

        String ua = userAgent.toLowerCase();

                  
        if (ua.contains("chrome") && !ua.contains("edge")) {
            return "Chrome";
        } else if (ua.contains("safari") && !ua.contains("chrome")) {
            return "Safari";
        } else if (ua.contains("firefox")) {
            return "Firefox";
        } else if (ua.contains("edge") || ua.contains("edg")) {
            return "Edge";
        } else if (ua.contains("opr/") || ua.contains("opera")) {
            return "Opera";
        } else if (ua.contains("micromessenger")) {
            return "WeChat";
        } else if (ua.contains("qqbrowser")) {
            return "QQBrowser";
        } else {
            return "Unknown";
        }
    }

       
                  
      
                         
       
    public void addBlockedKeyword(String keyword) {
        BLOCKED_AGENTS.add(keyword.toLowerCase());
        log.info("[SpiderDetector] 添加黑名单关键词: {}", keyword);
    }

       
                  
      
                         
       
    public void addWhitelistKeyword(String keyword) {
        WHITELIST_AGENTS.add(keyword.toLowerCase());
        log.info("[SpiderDetector] 添加白名单关键词: {}", keyword);
    }
}
