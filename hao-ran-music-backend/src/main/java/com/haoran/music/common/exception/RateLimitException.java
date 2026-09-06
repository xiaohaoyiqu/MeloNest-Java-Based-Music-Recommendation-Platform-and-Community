package com.haoran.music.common.exception;





public class RateLimitException extends RuntimeException {

    private final boolean needCaptcha;
    private final String captchaScene;
    private final String captchaType;
    private final Integer retryAfterSeconds;

    public RateLimitException(String message) {
        super(message);
        this.needCaptcha = false;
        this.captchaScene = null;
        this.captchaType = null;
        this.retryAfterSeconds = null;
    }

    public RateLimitException(String message, boolean needCaptcha, String captchaScene,
                              String captchaType, Integer retryAfterSeconds) {
        super(message);
        this.needCaptcha = needCaptcha;
        this.captchaScene = captchaScene;
        this.captchaType = captchaType;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public RateLimitException(String message, Throwable cause) {
        super(message, cause);
        this.needCaptcha = false;
        this.captchaScene = null;
        this.captchaType = null;
        this.retryAfterSeconds = null;
    }

    public boolean isNeedCaptcha() {
        return needCaptcha;
    }

    public String getCaptchaScene() {
        return captchaScene;
    }

    public String getCaptchaType() {
        return captchaType;
    }

    public Integer getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}