   
                      
                        
   

package com.haoran.music.service.impl;

import com.haoran.music.common.config.CrawlerDetectionConfig;
import com.haoran.music.common.util.RedisUtils;
import com.haoran.music.common.util.SpiderDetector;
import com.haoran.music.common.util.ClientIpResolver;
import com.haoran.music.service.CrawlerDetectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

   
           
  
        
                           
                               
                      
                     
   
@Slf4j
@Service
public class CrawlerDetectionServiceImpl implements CrawlerDetectionService {

    @Resource
    private RedisUtils redisUtils;

    @Resource
    private SpiderDetector spiderDetector;

    @Resource
    private CrawlerDetectionConfig crawlerDetectionConfig;

    @Resource
    private ClientIpResolver clientIpResolver;

               
    private static final String IP_BAN_KEY = "crawler:ban:ip:";
    private static final String BEHAVIOR_KEY = "crawler:behavior:";
    private static final String ACCESS_PATTERN_KEY = "crawler:pattern:";


    @Override
    public boolean isCrawler(HttpServletRequest request, Long userId) {
        CrawlerDetectionResult result = detectAndGetDetails(request, userId);
        return result.isCrawler();
    }

    @Override
    public CrawlerDetectionResult detectAndGetDetails(HttpServletRequest request, Long userId) {
        List<String> riskFactors = new ArrayList<>();
        int riskScore = 0;

                               
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null || userAgent.isEmpty()) {
            riskFactors.add("无User-Agent");
            riskScore += crawlerDetectionConfig.getMissingUserAgentRisk();
        } else if (spiderDetector.isSpider(userAgent)) {
            riskFactors.add("已知爬虫UA");
            riskScore += crawlerDetectionConfig.getSpiderUserAgentRisk();
        }

                            
        String referer = request.getHeader("Referer");
        if (referer == null || referer.isEmpty()) {
            riskFactors.add("无Referer（可能是API直接调用）");
            riskScore += crawlerDetectionConfig.getMissingRefererRisk();
        } else {
                              
            boolean validReferer = false;
            for (String allowed : crawlerDetectionConfig.getAllowedReferrers()) {
                if (referer.contains(allowed)) {
                    validReferer = true;
                    break;
                }
            }
            if (!validReferer) {
                riskFactors.add("外部Referer");
                riskScore += crawlerDetectionConfig.getExternalRefererRisk();
            }
        }

                         
        String ip = getClientIp(request);
        String uri = request.getRequestURI();
        if (isSuspiciousAccessPattern(ip, uri)) {
            riskFactors.add("可疑访问模式");
            riskScore += crawlerDetectionConfig.getSuspiciousAccessRisk();
        }

                                
        if (userId != null && isReadOnlyUser(userId)) {
            riskFactors.add("只读访问（无互动）");
            riskScore += crawlerDetectionConfig.getReadOnlyRisk();
        }

                    
        if (isIpBanned(ip)) {
            return new CrawlerDetectionResult(true, "IP已封禁", 100, new String[]{"IP封禁"});
        }

                            
        boolean isCrawler = riskScore >= crawlerDetectionConfig.getCrawlerRiskThreshold();
        String reason = isCrawler ? "风险分数: " + riskScore + "，原因: " + String.join(", ", riskFactors) : "正常访问";

        return new CrawlerDetectionResult(isCrawler, reason, riskScore, riskFactors.toArray(new String[0]));
    }

    @Override
    public void recordNormalBehavior(HttpServletRequest request, Long userId, String action) {
        String ip = getClientIp(request);

        try {
                            
            String behaviorKey = BEHAVIOR_KEY + ip;
            redisUtils.increment(behaviorKey + ":" + action);
            redisUtils.expire(behaviorKey + ":" + action, crawlerDetectionConfig.getBehaviorExpireSeconds(), TimeUnit.SECONDS);

                             
            if (isInteractiveAction(action)) {
                String patternKey = ACCESS_PATTERN_KEY + ip;
                redisUtils.delete(patternKey);
            }

            log.debug("记录正常行为: ip={}, userId={}, action={}", ip, userId, action);

        } catch (Exception e) {
            log.error("记录正常行为失败");
        }
    }

    @Override
    public boolean isIpBanned(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }

        try {
            String key = IP_BAN_KEY + ip;
            return redisUtils.hasKey(key);
        } catch (Exception e) {
            log.error("检查IP封禁状态失败");
            return false;
        }
    }

    @Override
    public void banIp(String ip, int durationHours, String reason) {
        if (ip == null || ip.isEmpty()) {
            return;
        }

        try {
            String key = IP_BAN_KEY + ip;
            String value = "banned:" + reason + ":" + System.currentTimeMillis();
            redisUtils.set(key, value, durationHours * 3600L, TimeUnit.SECONDS);
            log.warn("封禁IP: ip={}, duration={}小时, reason={}", ip, durationHours, reason);
        } catch (Exception e) {
            log.error("封禁IP失败");
        }
    }

       
               
       
    private boolean isSuspiciousAccessPattern(String ip, String uri) {
        try {
            String patternKey = ACCESS_PATTERN_KEY + ip;

                      
            long timestamp = System.currentTimeMillis();
            String timeKey = patternKey + ":times";
            redisUtils.increment(timeKey);
            redisUtils.expire(timeKey, crawlerDetectionConfig.getAccessWindowSeconds(), TimeUnit.SECONDS);

                     
            Object countObj = redisUtils.get(timeKey);
            int count = countObj != null ? Integer.parseInt(countObj.toString()) : 0;

                               
            if (count > crawlerDetectionConfig.getAccessBurstThreshold()) {
                return true;
            }

                          
            String behaviorKey = BEHAVIOR_KEY + ip;
            boolean hasInteractiveBehavior = redisUtils.hasKey(behaviorKey + ":play")
                    || redisUtils.hasKey(behaviorKey + ":like")
                    || redisUtils.hasKey(behaviorKey + ":comment");

                            
            if (count > crawlerDetectionConfig.getAccessNoInteractionThreshold() && !hasInteractiveBehavior) {
                return true;
            }

            return false;

        } catch (Exception e) {
            log.error("检测访问模式失败");
            return false;
        }
    }

       
                       
       
    private boolean isReadOnlyUser(Long userId) {
        try {
            String behaviorKey = BEHAVIOR_KEY + userId;

                        
            boolean hasView = redisUtils.hasKey(behaviorKey + ":view");
            boolean hasInteractive = redisUtils.hasKey(behaviorKey + ":play")
                    || redisUtils.hasKey(behaviorKey + ":like")
                    || redisUtils.hasKey(behaviorKey + ":comment");

                                  
            if (hasView && !hasInteractive) {
                Object viewCountObj = redisUtils.get(behaviorKey + ":view");
                int viewCount = viewCountObj != null ? Integer.parseInt(viewCountObj.toString()) : 0;
                return viewCount > crawlerDetectionConfig.getReadOnlyViewThreshold();            
            }

            return false;

        } catch (Exception e) {
            log.error("检测只读用户失败");
            return false;
        }
    }

       
                
       
    private boolean isInteractiveAction(String action) {
        return "play".equals(action)
                || "like".equals(action)
                || "unlike".equals(action)
                || "comment".equals(action)
                || "follow".equals(action)
                || "share".equals(action);
    }

       
              
       
    private String getClientIp(HttpServletRequest request) {
        return clientIpResolver.resolve(request);
    }
}
