   
                      
   
package com.haoran.music.service;

import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
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
public class MediaAccessGrantService {

    private static final String VERSION = "v1";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long MAX_CLOCK_SKEW_SECONDS = 5L;

    private final byte[] signingKey;
    private final long ttlSeconds;
    private final Clock clock;

    @Autowired
    public MediaAccessGrantService(
            @Value("${jwt.secret}") String secret,
            @Value("${media.playback.grant-ttl-seconds:300}") long ttlSeconds) {
        this(secret, ttlSeconds, Clock.systemUTC());
    }

    MediaAccessGrantService(String secret, long ttlSeconds, Clock clock) {
        if (secret == null || secret.trim().length() < 32) {
            throw new IllegalStateException("media playback signing secret must contain at least 32 characters");
        }
        if (ttlSeconds < 30 || ttlSeconds > 900) {
            throw new IllegalStateException("media playback grant TTL must be between 30 and 900 seconds");
        }
        this.signingKey = ("media-playback:" + secret.trim()).getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = ttlSeconds;
        this.clock = clock;
    }

    public String issue(String mediaType, Long resourceId, String quality, Long userId) {
        validateClaims(mediaType, resourceId, quality, userId);
        long expiresAt = clock.instant().getEpochSecond() + ttlSeconds;
        String payload = String.join("|", VERSION, mediaType, resourceId.toString(), quality,
                userId.toString(), Long.toString(expiresAt));
        String encodedPayload = encode(payload.getBytes(StandardCharsets.UTF_8));
        return encodedPayload + "." + encode(sign(payload));
    }

    public Long verify(String grant, String mediaType, Long resourceId, String quality) {
        try {
            if (grant == null || grant.length() > 1024) {
                throw invalidGrant();
            }
            String[] tokenParts = grant.split("\\.", -1);
            if (tokenParts.length != 2) {
                throw invalidGrant();
            }

            String payload = new String(Base64.getUrlDecoder().decode(tokenParts[0]), StandardCharsets.UTF_8);
            byte[] providedSignature = Base64.getUrlDecoder().decode(tokenParts[1]);
            if (!MessageDigest.isEqual(sign(payload), providedSignature)) {
                throw invalidGrant();
            }

            String[] claims = payload.split("\\|", -1);
            if (claims.length != 6 || !VERSION.equals(claims[0])
                    || !mediaType.equals(claims[1]) || !resourceId.toString().equals(claims[2])
                    || !quality.equals(claims[3])) {
                throw invalidGrant();
            }

            Long userId = Long.valueOf(claims[4]);
            long expiresAt = Long.parseLong(claims[5]);
            long now = clock.instant().getEpochSecond();
            if (expiresAt + MAX_CLOCK_SKEW_SECONDS < now || expiresAt > now + ttlSeconds + MAX_CLOCK_SKEW_SECONDS) {
                throw invalidGrant();
            }
            validateClaims(mediaType, resourceId, quality, userId);
            return userId;
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            throw invalidGrant();
        }
    }

    private void validateClaims(String mediaType, Long resourceId, String quality, Long userId) {
        if (!("song".equals(mediaType) || "mv".equals(mediaType))
                || resourceId == null || resourceId <= 0 || userId == null || userId <= 0
                || quality == null || !quality.matches("[a-z0-9_-]{2,24}")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "播放授权参数无效");
        }
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signingKey, HMAC_ALGORITHM));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("cannot create media playback signature", e);
        }
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static BusinessException invalidGrant() {
        return new BusinessException(ResultCode.FORBIDDEN, "播放授权无效或已过期，请重新获取");
    }
}
