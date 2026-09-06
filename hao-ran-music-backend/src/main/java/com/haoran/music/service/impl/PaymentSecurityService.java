package com.haoran.music.service.impl;

import com.haoran.music.common.config.PaymentConfig;
import com.haoran.music.common.exception.BusinessException;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

   
                      
                              
   
@Service
public class PaymentSecurityService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String CURRENT_HASH_PREFIX = "h2:";
    private static final String LEGACY_HASH_PREFIX = "h1:";
    private static final int MIN_SECRET_LENGTH = 32;

    private final PaymentConfig paymentConfig;
    private final SecureRandom secureRandom = new SecureRandom();

    public PaymentSecurityService(PaymentConfig paymentConfig) {
        this.paymentConfig = paymentConfig;
    }

    public String generateVerificationCode() {
        int length = paymentConfig.getVerifyCodeLength() == null
                ? 6 : paymentConfig.getVerifyCodeLength();
        if (length < 4 || length > 12) {
            throw new BusinessException("支付验证码长度配置无效");
        }
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(secureRandom.nextInt(10));
        }
        return code.toString();
    }

    public String hashOrderCode(Long orderId, String verificationCode) {
        if (orderId == null) {
            throw new BusinessException("支付订单ID不能为空");
        }
        return hash("order:" + orderId, verificationCode);
    }

    public boolean matchesOrderCode(Long orderId, String verificationCode, String storedHash) {
        if (orderId == null) {
            throw new BusinessException("支付订单ID不能为空");
        }
        return matches("order:" + orderId, verificationCode, storedHash);
    }

    public String hashConfigCode(String verificationCode) {
        return hash("payment-config", verificationCode);
    }

    public boolean matchesConfigCode(String verificationCode, String storedHash) {
        return matches("payment-config", verificationCode, storedHash);
    }

    public boolean isProtectedHash(String value) {
        return value != null
                && (value.startsWith(CURRENT_HASH_PREFIX) || value.startsWith(LEGACY_HASH_PREFIX));
    }

    private String hash(String context, String value) {
        return hashWithSecret(context, value, paymentConfig.getVerificationHmacSecret(), CURRENT_HASH_PREFIX);
    }

    private String hashWithSecret(String context, String value, String secret, String prefix) {
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessException("支付验证码不能为空");
        }
        if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
            throw new BusinessException("支付验证码HMAC密钥未配置或长度不足");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] digest = mac.doFinal((context + "\n" + value.trim()).getBytes(StandardCharsets.UTF_8));
            return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("无法计算支付验证码HMAC", e);
        }
    }

    private boolean matches(String context, String value, String actual) {
        if (!isProtectedHash(actual)) {
            return false;
        }
        String secret = paymentConfig.getVerificationHmacSecret();
        String prefix = CURRENT_HASH_PREFIX;
        if (actual.startsWith(LEGACY_HASH_PREFIX)) {
            secret = paymentConfig.getPreviousVerificationHmacSecret();
            if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
                secret = paymentConfig.getVerificationHmacSecret();
            }
            prefix = LEGACY_HASH_PREFIX;
        }
        String expected = hashWithSecret(context, value, secret, prefix);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII));
    }
}
