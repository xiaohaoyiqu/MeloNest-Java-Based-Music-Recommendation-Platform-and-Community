package com.haoran.music.service;

import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;






@Service
public class PrivateAttachmentGrantService {

    private static final String VERSION = "v1";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long MAX_CLOCK_SKEW_SECONDS = 5L;

    private final byte[] signingKey;
    private final long ttlSeconds;
    private final Clock clock;







    @Autowired
    public PrivateAttachmentGrantService(
            @Value("${jwt.secret}") String secret,
            @Value("${music.upload.private-attachment-grant-ttl-seconds:300}") long ttlSeconds) {
        this(secret, ttlSeconds, Clock.systemUTC());
    }








    PrivateAttachmentGrantService(String secret, long ttlSeconds, Clock clock) {
        if (ObjectUtils.isEmpty(secret) || secret.trim().length() < 32) {
            throw new IllegalStateException("private attachment signing secret must contain at least 32 characters");
        }
        if (ttlSeconds < 30 || ttlSeconds > 900) {
            throw new IllegalStateException("private attachment grant TTL must be between 30 and 900 seconds");
        }
        this.signingKey = ("private-attachment:" + secret.trim()).getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = ttlSeconds;
        this.clock = clock;
    }








    public String issue(Long assetId, Long userId) {
        validateClaims(assetId, userId);
        long expiresAt = clock.instant().getEpochSecond() + ttlSeconds;
        String payload = String.join("|", VERSION, assetId.toString(), userId.toString(),
                Long.toString(expiresAt));
        return encode(payload.getBytes(StandardCharsets.UTF_8)) + "." + encode(sign(payload));
    }








    public Long verify(String grant, Long assetId) {
        try {
            if (ObjectUtils.isEmpty(grant) || grant.length() > 1024) {
                throw invalidGrant();
            }
            String[] tokenParts = grant.split("\\.", -1);
            if (tokenParts.length != 2) {
                throw invalidGrant();
            }
            String payload = new String(Base64.getUrlDecoder().decode(tokenParts[0]), StandardCharsets.UTF_8);
            byte[] signature = Base64.getUrlDecoder().decode(tokenParts[1]);
            if (!MessageDigest.isEqual(sign(payload), signature)) {
                throw invalidGrant();
            }
            String[] claims = payload.split("\\|", -1);
            if (claims.length != 4 || !VERSION.equals(claims[0]) || !assetId.toString().equals(claims[1])) {
                throw invalidGrant();
            }
            Long userId = Long.valueOf(claims[2]);
            long expiresAt = Long.parseLong(claims[3]);
            long now = clock.instant().getEpochSecond();
            if (expiresAt + MAX_CLOCK_SKEW_SECONDS < now
                    || expiresAt > now + ttlSeconds + MAX_CLOCK_SKEW_SECONDS) {
                throw invalidGrant();
            }
            validateClaims(assetId, userId);
            return userId;
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            throw invalidGrant();
        }
    }







    private void validateClaims(Long assetId, Long userId) {
        if (ObjectUtils.isEmpty(assetId) || assetId <= 0
                || ObjectUtils.isEmpty(userId) || userId <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "私有附件授权参数无效");
        }
    }







    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signingKey, HMAC_ALGORITHM));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("cannot create private attachment signature", e);
        }
    }







    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }






    private static BusinessException invalidGrant() {
        return new BusinessException(ResultCode.FORBIDDEN, "私有附件授权无效或已过期，请重新获取");
    }
}
