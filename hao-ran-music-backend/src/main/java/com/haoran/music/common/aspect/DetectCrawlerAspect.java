   
                      
                                                   
   

package com.haoran.music.common.aspect;

import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.util.ClientIpResolver;
import com.haoran.music.service.CrawlerDetectionService;
import com.haoran.music.service.CrawlerDetectionService.CrawlerDetectionResult;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

   
          
  
        
               
                  
               
            
            
                   
  
                           
   
@Slf4j
@Aspect
@Component
public class DetectCrawlerAspect {

    private static final String[] PUBLIC_API_PATHS = {
        "/api/song/hot", "/api/song/new", "/api/song/page",
        "/api/album/hot", "/api/album/page", "/api/album/info",
        "/api/artist/page", "/api/artist/list", "/api/artist/hot", "/api/artist/info",
        "/api/artist/letter", "/api/artist/search", "/api/artist/letters",
        "/api/playlist/hot", "/api/playlist/new", "/api/playlist/page", "/api/playlist/info",
        "/api/mv/hot", "/api/mv/newest", "/api/mv/page", "/api/mv/info",
        "/api/search",
        "/api/recommend/daily", "/api/recommend/discover", "/api/recommend/personal",
        "/api/hybrid/",
        "/api/song/stream", "/api/song/url", "/api/song/info",
        "/api/comment/page", "/api/comment/hot",
        "/api/decoration/shop", "/api/decoration/types", "/api/decoration/detail"
    };

    @Resource
    private CrawlerDetectionService crawlerDetectionService;

    @Resource
    private ClientIpResolver clientIpResolver;

       
                            
       
    @Around("@annotation(detectCrawler)")
    public Object detectCrawler(ProceedingJoinPoint joinPoint, DetectCrawler detectCrawler) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return joinPoint.proceed();
        }

        HttpServletRequest request = attributes.getRequest();
        String uri = request.getRequestURI();

                                   
        if (isPublicApiPath(uri, request.getMethod())) {
            log.debug("event=crawler_detection_skipped reason=public_api");
            return joinPoint.proceed();
        }

                                    
        Long userId = (Long) request.getAttribute("userId");

               
        CrawlerDetectionResult result = crawlerDetectionService.detectAndGetDetails(request, userId);

                 
        String operation = detectCrawler.operation();
        String ip = clientIpResolver.resolve(request);

        int riskFactorCount = result.getRiskFactors().length;
        log.debug("event=crawler_detection_completed operation={} userId={} riskScore={} factorCount={}",
                operation, userId, result.getRiskScore(), riskFactorCount);

                  
        if (result.isCrawler()) {
            log.warn("event=crawler_request_blocked userId={} riskScore={}",
                    userId, result.getRiskScore());

                            
            if (result.getRiskScore() >= 90) {
                crawlerDetectionService.banIp(ip, 24, "高风险爬虫行为");
                log.warn("event=crawler_ip_banned durationHours=24 riskScore={}", result.getRiskScore());
            }

                          
            String message = detectCrawler.message();
            throw new BusinessException(403, message);
        }

                    
        return joinPoint.proceed();
    }

       
                            
          
       
    private boolean isPublicApiPath(String uri, String method) {
        if ("GET".equalsIgnoreCase(method)
                && (uri.matches("^/api/lyric/\\d+(?:/all)?$")
                    || "/api/lyric/languages".equals(uri))) {
            return true;
        }
        for (String path : PUBLIC_API_PATHS) {
            if (uri.contains(path)) {
                return true;
            }
        }
        return false;
    }

}
