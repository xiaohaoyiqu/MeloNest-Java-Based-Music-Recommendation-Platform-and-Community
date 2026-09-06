



package com.haoran.music.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "security")
public class SecurityConfig {

    private boolean hideIp = true;
    private boolean ipRateLimitEnabled = false;
    private boolean userAgentCheckEnabled = true;
    private boolean sensitiveLogEnabled = true;



    private int ipRateLimitPerDay = 300;
    private int ipRateLimitPerDayForLoggedIn = 5000;



    private boolean behaviorCheckEnabled = true;
    private int fastAccessThresholdPerMinute = 100;
    private int sustainedFastThresholdPerMinute = 50;



    private boolean captchaEnabled = true;
    private int captchaTriggerThreshold = 80;
    private int captchaTriggerWindowSeconds = 120;
    private int captchaWhitelistMinutes = 30;



    private int paidContentDailyViewLimit = 200;
    private int paidContentDownloadLimit = 5;
    private int creatorBatchAccessThresholdSeconds = 60;
    private int creatorBatchAccessThresholdCount = 30;
    private int paidContentDistinctWindowSeconds = 120;
    private int paidContentDistinctWarnThreshold = 40;
    private int paidContentDistinctBlockThreshold = 80;
    private boolean paidContentHardBlockEnabled = true;



    private ContentTypeConfig contentType = new ContentTypeConfig();



    private List<String> allowedOrigins;





    private boolean queryTokenEnabled = false;
    private boolean authCookieSecure = false;
    private int authCookieMaxAgeSeconds = 604800;




    private List<String> trustedProxies = new ArrayList<>();



    public boolean isHideIp() { return hideIp; }
    public void setHideIp(boolean hideIp) { this.hideIp = hideIp; }

    public boolean isIpRateLimitEnabled() { return ipRateLimitEnabled; }
    public void setIpRateLimitEnabled(boolean ipRateLimitEnabled) { this.ipRateLimitEnabled = ipRateLimitEnabled; }

    public int getIpRateLimitPerDay() { return ipRateLimitPerDay; }
    public void setIpRateLimitPerDay(int ipRateLimitPerDay) { this.ipRateLimitPerDay = ipRateLimitPerDay; }

    public int getIpRateLimitPerDayForLoggedIn() { return ipRateLimitPerDayForLoggedIn; }
    public void setIpRateLimitPerDayForLoggedIn(int ipRateLimitPerDayForLoggedIn) { this.ipRateLimitPerDayForLoggedIn = ipRateLimitPerDayForLoggedIn; }

    public boolean isUserAgentCheckEnabled() { return userAgentCheckEnabled; }
    public void setUserAgentCheckEnabled(boolean userAgentCheckEnabled) { this.userAgentCheckEnabled = userAgentCheckEnabled; }

    public boolean isSensitiveLogEnabled() { return sensitiveLogEnabled; }
    public void setSensitiveLogEnabled(boolean sensitiveLogEnabled) { this.sensitiveLogEnabled = sensitiveLogEnabled; }

    public boolean isBehaviorCheckEnabled() { return behaviorCheckEnabled; }
    public void setBehaviorCheckEnabled(boolean behaviorCheckEnabled) { this.behaviorCheckEnabled = behaviorCheckEnabled; }

    public int getFastAccessThresholdPerMinute() { return fastAccessThresholdPerMinute; }
    public void setFastAccessThresholdPerMinute(int fastAccessThresholdPerMinute) { this.fastAccessThresholdPerMinute = fastAccessThresholdPerMinute; }

    public int getSustainedFastThresholdPerMinute() { return sustainedFastThresholdPerMinute; }
    public void setSustainedFastThresholdPerMinute(int sustainedFastThresholdPerMinute) { this.sustainedFastThresholdPerMinute = sustainedFastThresholdPerMinute; }

    public boolean isCaptchaEnabled() { return captchaEnabled; }
    public void setCaptchaEnabled(boolean captchaEnabled) { this.captchaEnabled = captchaEnabled; }

    public int getCaptchaTriggerThreshold() { return captchaTriggerThreshold; }
    public void setCaptchaTriggerThreshold(int captchaTriggerThreshold) { this.captchaTriggerThreshold = captchaTriggerThreshold; }

    public int getCaptchaTriggerWindowSeconds() { return captchaTriggerWindowSeconds; }
    public void setCaptchaTriggerWindowSeconds(int captchaTriggerWindowSeconds) { this.captchaTriggerWindowSeconds = captchaTriggerWindowSeconds; }

    public int getCaptchaWhitelistMinutes() { return captchaWhitelistMinutes; }
    public void setCaptchaWhitelistMinutes(int captchaWhitelistMinutes) { this.captchaWhitelistMinutes = captchaWhitelistMinutes; }

    public ContentTypeConfig getContentType() { return contentType; }
    public void setContentType(ContentTypeConfig contentType) { this.contentType = contentType; }

    public int getPaidContentDailyViewLimit() { return paidContentDailyViewLimit; }
    public void setPaidContentDailyViewLimit(int paidContentDailyViewLimit) { this.paidContentDailyViewLimit = paidContentDailyViewLimit; }

    public int getPaidContentDownloadLimit() { return paidContentDownloadLimit; }
    public void setPaidContentDownloadLimit(int paidContentDownloadLimit) { this.paidContentDownloadLimit = paidContentDownloadLimit; }

    public int getCreatorBatchAccessThresholdSeconds() { return creatorBatchAccessThresholdSeconds; }
    public void setCreatorBatchAccessThresholdSeconds(int creatorBatchAccessThresholdSeconds) { this.creatorBatchAccessThresholdSeconds = creatorBatchAccessThresholdSeconds; }

    public int getCreatorBatchAccessThresholdCount() { return creatorBatchAccessThresholdCount; }
    public void setCreatorBatchAccessThresholdCount(int creatorBatchAccessThresholdCount) { this.creatorBatchAccessThresholdCount = creatorBatchAccessThresholdCount; }


    public int getPaidContentDistinctWindowSeconds() { return paidContentDistinctWindowSeconds; }
    public void setPaidContentDistinctWindowSeconds(int paidContentDistinctWindowSeconds) { this.paidContentDistinctWindowSeconds = paidContentDistinctWindowSeconds; }

    public int getPaidContentDistinctWarnThreshold() { return paidContentDistinctWarnThreshold; }
    public void setPaidContentDistinctWarnThreshold(int paidContentDistinctWarnThreshold) { this.paidContentDistinctWarnThreshold = paidContentDistinctWarnThreshold; }

    public int getPaidContentDistinctBlockThreshold() { return paidContentDistinctBlockThreshold; }
    public void setPaidContentDistinctBlockThreshold(int paidContentDistinctBlockThreshold) { this.paidContentDistinctBlockThreshold = paidContentDistinctBlockThreshold; }

    public boolean isPaidContentHardBlockEnabled() { return paidContentHardBlockEnabled; }
    public void setPaidContentHardBlockEnabled(boolean paidContentHardBlockEnabled) { this.paidContentHardBlockEnabled = paidContentHardBlockEnabled; }

    public List<String> getAllowedOrigins() { return allowedOrigins; }
    public void setAllowedOrigins(List<String> allowedOrigins) { this.allowedOrigins = allowedOrigins; }

    public boolean isQueryTokenEnabled() { return queryTokenEnabled; }
    public void setQueryTokenEnabled(boolean queryTokenEnabled) { this.queryTokenEnabled = queryTokenEnabled; }

    public boolean isAuthCookieSecure() { return authCookieSecure; }
    public void setAuthCookieSecure(boolean authCookieSecure) { this.authCookieSecure = authCookieSecure; }

    public int getAuthCookieMaxAgeSeconds() { return authCookieMaxAgeSeconds; }
    public void setAuthCookieMaxAgeSeconds(int authCookieMaxAgeSeconds) {
        this.authCookieMaxAgeSeconds = authCookieMaxAgeSeconds;
    }

    public List<String> getTrustedProxies() { return trustedProxies; }
    public void setTrustedProxies(List<String> trustedProxies) {
        this.trustedProxies = trustedProxies == null ? new ArrayList<>() : trustedProxies;
    }

    public static class ContentTypeConfig {
        private String[] paidContentTypes = {"paid", "premium", "vip"};
        private String[] creatorContentTypes = {"creator", "creator-work", "music-square-work"};
        private String[] vipContentTypes = {"vip-exclusive", "vip-only", "vip-feature"};

        public String[] getPaidContentTypes() { return paidContentTypes; }
        public void setPaidContentTypes(String[] paidContentTypes) { this.paidContentTypes = paidContentTypes; }

        public String[] getCreatorContentTypes() { return creatorContentTypes; }
        public void setCreatorContentTypes(String[] creatorContentTypes) { this.creatorContentTypes = creatorContentTypes; }

        public String[] getVipContentTypes() { return vipContentTypes; }
        public void setVipContentTypes(String[] vipContentTypes) { this.vipContentTypes = vipContentTypes; }
    }
}
