



package com.haoran.music.service.impl;

import com.haoran.music.common.config.AuthRiskConfig;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.service.AuthRiskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;




@Slf4j
@Service
public class AuthRiskServiceImpl implements AuthRiskService {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private AuthRiskConfig authRiskConfig;

    private static final String ACCOUNT_FAIL_PREFIX = "auth:risk:login-fail:account:";
    private static final String IP_FAIL_PREFIX = "auth:risk:login-fail:ip:";
    private static final String FORCE_ACCOUNT_PREFIX = "auth:risk:force-account:";
    private static final String LOGOUT_USER_PREFIX = "auth:risk:logout:user:";
    private static final String LAST_IP_USER_PREFIX = "auth:risk:last-ip:user:";
    private static final String IP_CHANGE_USER_PREFIX = "auth:risk:ip-change:user:";

    @Override
    public boolean needCaptcha(String account, String clientIp, String userAgent) {
        if (!enabled()) {
            return false;
        }
        return !buildReasons(account, clientIp, userAgent).isEmpty();
    }

    @Override
    public boolean isCaptchaEnforced() {
        return Boolean.TRUE.equals(authRiskConfig.getEnforceCaptcha());
    }

    @Override
    public boolean isLoginTemporarilyBlocked(String account) {
        if (!enabled()) {
            return false;
        }
        return safeLong(redisTemplate.opsForValue().get(accountFailKey(account)))
                >= safeInt(authRiskConfig.getAccountBlockThreshold(), 10);
    }

    @Override
    public void recordLoginFailure(String account, String clientIp) {
        if (!enabled()) {
            return;
        }
        Long accountFailures = increment(accountFailKey(account), safeInt(authRiskConfig.getLoginFailureWindowMinutes(), 15), TimeUnit.MINUTES);
        Long ipFailures = increment(ipFailKey(clientIp), safeInt(authRiskConfig.getLoginFailureWindowMinutes(), 15), TimeUnit.MINUTES);
        if (safeLong(accountFailures) >= safeInt(authRiskConfig.getAccountFailureThreshold(), 3)) {
            forceAccountCaptcha(account, "login_failure");
        }
        if (safeLong(ipFailures) >= safeInt(authRiskConfig.getIpFailureThreshold(), 10)) {
            log.warn("IP login failure threshold reached: ip={}", maskIp(clientIp));
        }
    }

    @Override
    public void recordLoginSuccess(Long userId, String account, String clientIp, String userAgent) {
        if (!enabled()) {
            return;
        }
        redisTemplate.delete(accountFailKey(account));
        redisTemplate.delete(forceAccountKey(account));
        if (userId == null) {
            return;
        }

        String lastIpKey = LAST_IP_USER_PREFIX + userId;
        Object lastIpObj = redisTemplate.opsForValue().get(lastIpKey);
        String lastIp = lastIpObj == null ? null : lastIpObj.toString();
        String currentIp = normalizeIp(clientIp);
        if (ObjectUtils.isNotEmpty(lastIp) && !lastIp.equals(currentIp)) {
            Long changes = increment(IP_CHANGE_USER_PREFIX + userId, safeInt(authRiskConfig.getIpChangeWindowHours(), 24), TimeUnit.HOURS);
            if (safeLong(changes) >= safeInt(authRiskConfig.getIpChangeThreshold(), 3)) {
                forceAccountCaptcha(account, "frequent_ip_change");
            }
        }
        redisTemplate.opsForValue().set(lastIpKey, currentIp, 30, TimeUnit.DAYS);

        Object logoutObj = redisTemplate.opsForValue().get(LOGOUT_USER_PREFIX + userId);
        if (safeLong(logoutObj) >= safeInt(authRiskConfig.getLogoutLoginThreshold(), 3)) {
            forceAccountCaptcha(account, "logout_login_churn");
        }
    }

    @Override
    public void recordLogout(Long userId, String clientIp) {
        if (!enabled() || userId == null) {
            return;
        }
        increment(LOGOUT_USER_PREFIX + userId, safeInt(authRiskConfig.getLogoutWindowMinutes(), 10), TimeUnit.MINUTES);
    }

    @Override
    public Map<String, Object> getRiskStatus(String account, String clientIp, String userAgent) {
        List<String> reasons = buildReasons(account, clientIp, userAgent);
        int riskScore = calculateRiskScore(reasons);
        Map<String, Object> result = new HashMap<>();
        result.put("riskNeedCaptcha", !reasons.isEmpty());
        result.put("captchaEnforced", isCaptchaEnforced());
        result.put("riskScore", riskScore);
        result.put("riskLevel", resolveRiskLevel(riskScore));
        result.put("reasons", reasons);
        result.put("accountFailureCount", safeLong(redisTemplate.opsForValue().get(accountFailKey(account))));
        result.put("ipFailureCount", safeLong(redisTemplate.opsForValue().get(ipFailKey(clientIp))));
        return result;
    }

    private List<String> buildReasons(String account, String clientIp, String userAgent) {
        List<String> reasons = new ArrayList<>();
        if (Boolean.TRUE.equals(redisTemplate.hasKey(forceAccountKey(account)))) {
            reasons.add("force_account_captcha");
        }
        if (safeLong(redisTemplate.opsForValue().get(accountFailKey(account))) >= safeInt(authRiskConfig.getAccountFailureThreshold(), 3)) {
            reasons.add("account_login_failures");
        }
        if (safeLong(redisTemplate.opsForValue().get(ipFailKey(clientIp))) >= safeInt(authRiskConfig.getIpFailureThreshold(), 10)) {
            reasons.add("ip_login_failures");
        }
        if (ObjectUtils.isEmpty(userAgent)) {
            reasons.add("missing_user_agent");
        }
        return reasons;
    }

    private int calculateRiskScore(List<String> reasons) {
        if (reasons == null || reasons.isEmpty()) {
            return 0;
        }
        int score = 0;
        for (String reason : reasons) {
            switch (reason) {
                case "force_account_captcha":
                    score += 40;
                    break;
                case "account_login_failures":
                    score += 30;
                    break;
                case "ip_login_failures":
                    score += 25;
                    break;
                case "missing_user_agent":
                    score += 10;
                    break;
                default:
                    score += 10;
                    break;
            }
        }
        return Math.min(score, 100);
    }

    private String resolveRiskLevel(int riskScore) {
        if (riskScore >= 70) {
            return "high";
        }
        if (riskScore >= 40) {
            return "medium";
        }
        if (riskScore > 0) {
            return "low";
        }
        return "normal";
    }

    private void forceAccountCaptcha(String account, String reason) {
        redisTemplate.opsForValue().set(forceAccountKey(account), reason,
                safeInt(authRiskConfig.getForceCaptchaMinutes(), 30), TimeUnit.MINUTES);
    }

    private Long increment(String key, int ttl, TimeUnit unit) {
        Long count = redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, ttl, unit);
        return count;
    }

    private String accountFailKey(String account) {
        return ACCOUNT_FAIL_PREFIX + hashIdentifier(normalizeAccount(account));
    }

    private String ipFailKey(String clientIp) {
        return IP_FAIL_PREFIX + hashIdentifier(normalizeIp(clientIp));
    }

    private String forceAccountKey(String account) {
        return FORCE_ACCOUNT_PREFIX + hashIdentifier(normalizeAccount(account));
    }

    private String normalizeAccount(String account) {
        return ObjectUtils.isEmpty(account) ? "unknown" : account.trim().toLowerCase();
    }

    private String normalizeIp(String clientIp) {
        return ObjectUtils.isEmpty(clientIp) ? "unknown" : clientIp.trim();
    }

    private String hashIdentifier(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private boolean enabled() {
        return Boolean.TRUE.equals(authRiskConfig.getEnabled());
    }

    private int safeInt(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private long safeLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (Exception e) {
            return 0L;
        }
    }

    private String maskIp(String clientIp) {
        if (ObjectUtils.isEmpty(clientIp)) {
            return "unknown";
        }
        int idx = clientIp.lastIndexOf('.');
        return idx > 0 ? clientIp.substring(0, idx) + ".*" : "***";
    }
}
