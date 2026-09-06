



package com.haoran.music.common.util;

import com.haoran.music.common.config.SecurityConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class IpRateLimiter {

    @Resource
    private RedisUtils redisUtils;

    @Resource
    private SecurityConfig securityConfig;

    private static final String IP_COUNT_KEY = "ip:count:";
    private static final String IP_CAPTCHA_WINDOW_KEY = "ip:captcha:window:";            
    private static final String CAPTCHA_WHITELIST = "captcha:whitelist:";             








    public boolean checkIpLimit(String ip, boolean isLoggedIn) {
        if (!securityConfig.isIpRateLimitEnabled()) {
            return true;
        }

        if (ip == null || ip.isEmpty()) {
            log.warn("[IpRateLimiter] IP地址为空");
            return false;
        }

        try {

            if (isCaptchaWhitelisted(ip)) {
                return true;
            }

            String key = IP_COUNT_KEY + ip;
            Long count = redisUtils.increment(key);


            if (count == 1) {
                long secondsUntilEndOfDay = getSecondsUntilEndOfDay();
                redisUtils.expire(key, secondsUntilEndOfDay, TimeUnit.SECONDS);
            }


            int limit = isLoggedIn ? securityConfig.getIpRateLimitPerDayForLoggedIn()
                                   : securityConfig.getIpRateLimitPerDay();

            if (count > limit) {
                log.warn("[IpRateLimiter] IP访问频率超限: ip={}, count={}, limit={}, loggedIn={}",
                    ip, count, limit, isLoggedIn);
                return false;
            }

            log.debug("[IpRateLimiter] IP访问计数: ip={}, count={}, limit={}, loggedIn={}",
                ip, count, limit, isLoggedIn);
            return true;

        } catch (Exception e) {
            log.error("[IpRateLimiter] 检查IP限制失败");
            return true;
        }
    }







    public boolean needCaptcha(String ip) {
        if (!securityConfig.isCaptchaEnabled()) {
            return false;
        }

        if (isCaptchaWhitelisted(ip)) {
            return false;
        }

        try {

            String windowKey = IP_CAPTCHA_WINDOW_KEY + ip;
            int windowSeconds = Math.max(1, securityConfig.getCaptchaTriggerWindowSeconds());
            Long count = redisUtils.increment(windowKey);
            if (count == 1) {
                redisUtils.expire(windowKey, windowSeconds, TimeUnit.SECONDS);
            }

            if (count >= securityConfig.getCaptchaTriggerThreshold()) {
                log.warn("[IpRateLimiter] 触发验证码检测: ip={}, count={}", ip, count);
                return true;
            }

            return false;
        } catch (Exception e) {
            log.error("[IpRateLimiter] 检查验证码触发失败");
            return false;
        }
    }




    public void addToCaptchaWhitelist(String ip) {
        if (ip == null || ip.isEmpty()) {
            return;
        }
        try {
            String key = CAPTCHA_WHITELIST + ip;
            int minutes = securityConfig.getCaptchaWhitelistMinutes();
            redisUtils.set(key, "1", minutes * 60L, TimeUnit.SECONDS);
            log.info("[IpRateLimiter] 验证码通过，加入白名单: ip={}, minutes={}", ip, minutes);
        } catch (Exception e) {
            log.error("[IpRateLimiter] 添加白名单失败");
        }
    }




    public void addToCaptchaWhitelist(String ip, String scene) {
        if (ip == null || ip.isEmpty() || scene == null || scene.isEmpty()) {
            return;
        }
        try {
            String key = CAPTCHA_WHITELIST + scene + ":" + ip;
            int minutes = securityConfig.getCaptchaWhitelistMinutes();
            redisUtils.set(key, "1", minutes * 60L, TimeUnit.SECONDS);
            log.info("[IpRateLimiter] 验证码通过，加入场景白名单: ip={}, scene={}, minutes={}", ip, scene, minutes);
        } catch (Exception e) {
            log.error("[IpRateLimiter] 添加场景白名单失败");
        }
    }




    public boolean consumeCaptchaWhitelist(String ip, String scene) {
        if (ip == null || ip.isEmpty() || scene == null || scene.isEmpty()) {
            return false;
        }
        try {
            String key = CAPTCHA_WHITELIST + scene + ":" + ip;
            if (!redisUtils.hasKey(key)) {
                return false;
            }
            redisUtils.delete(key);
            log.info("[IpRateLimiter] 消费场景验证码白名单: ip={}, scene={}", ip, scene);
            return true;
        } catch (Exception e) {
            log.error("[IpRateLimiter] 消费场景白名单失败");
            return false;
        }
    }




    private boolean isCaptchaWhitelisted(String ip) {
        try {
            String key = CAPTCHA_WHITELIST + ip;
            return redisUtils.hasKey(key);
        } catch (Exception e) {
            return false;
        }
    }




    public boolean checkIpLimit(String ip) {
        return checkIpLimit(ip, false);
    }




    private long getSecondsUntilEndOfDay() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime endOfDay = now.toLocalDate().atTime(23, 59, 59);
        return java.time.Duration.between(now, endOfDay).getSeconds() + 1;
    }




    public long getCurrentCount(String ip) {
        if (ip == null || ip.isEmpty()) {
            return 0;
        }

        try {
            String key = IP_COUNT_KEY + ip;
            Object countObj = redisUtils.get(key);
            if (countObj != null) {
                return Long.parseLong(countObj.toString());
            }
        } catch (Exception e) {
            log.error("[IpRateLimiter] 获取IP计数失败");
        }
        return 0;
    }




    public long getRemainingCount(String ip, boolean isLoggedIn) {
        int limit = isLoggedIn ? securityConfig.getIpRateLimitPerDayForLoggedIn()
                               : securityConfig.getIpRateLimitPerDay();
        long current = getCurrentCount(ip);
        return Math.max(0, limit - current);
    }




    public long getCurrentMinuteCount(String ip) {
        try {
            String windowKey = IP_CAPTCHA_WINDOW_KEY + ip;
            Object countObj = redisUtils.get(windowKey);
            if (countObj != null) {
                return Long.parseLong(countObj.toString());
            }
        } catch (Exception e) {
            log.error("[IpRateLimiter] 获取分钟计数失败");
        }
        return 0;
    }




    public long getRemainingCount(String ip) {
        return getRemainingCount(ip, false);
    }




    public boolean resetIpCount(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }

        try {
            String key = IP_COUNT_KEY + ip;
            redisUtils.delete(key);
            log.info("[IpRateLimiter] 重置IP访问计数: ip={}", ip);
            return true;
        } catch (Exception e) {
            log.error("[IpRateLimiter] 重置IP计数失败");
            return false;
        }
    }
}
