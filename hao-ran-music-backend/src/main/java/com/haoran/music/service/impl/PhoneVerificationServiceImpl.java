



package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.haoran.music.common.config.UserVerificationConfig;
import com.haoran.music.common.enums.VerificationType;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.entity.UserVerification;
import com.haoran.music.mapper.UserVerificationMapper;
import com.haoran.music.service.PhoneVerificationService;
import com.haoran.music.service.SmsSendResult;
import com.haoran.music.service.SmsSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;




@Slf4j
@Service
public class PhoneVerificationServiceImpl implements PhoneVerificationService {

    @Resource
    private UserVerificationMapper userVerificationMapper;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private UserVerificationConfig userVerificationConfig;

    @Resource
    private SmsSender smsSender;

    private static final String CODE_CACHE_PREFIX = "verification:phone:code:";
    private static final String SEND_LIMIT_PREFIX = "verification:phone:limit:";
    private static final String IP_SEND_LIMIT_PREFIX = "verification:phone:ip-limit:";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public Map<String, Object> sendCode(String phone, String scene, String clientIp, String userAgent) {
        String normalizedScene = normalizeScene(scene);
        Integer verificationType = toVerificationType(normalizedScene);
        validatePhone(phone);

        if (!canSendCode(phone, verificationType)) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "验证码发送过于频繁，请稍后再试");
        }

        UserVerification active = selectActive(phone, verificationType);
        if (active != null && Integer.valueOf(1).equals(active.getIsLocked())) {
            throw new BusinessException("验证码已锁定，请稍后再试");
        }


        reserveSendWindow(phone, verificationType, clientIp);

        String code = generateNumericCode(safeInt(userVerificationConfig.getCodeLength(), 6));
        String codeHash = hashCode(code);
        UserVerification verification = savePendingCode(active, phone, verificationType, codeHash, clientIp, userAgent);
        String cacheKey = getCodeCacheKey(phone, verificationType);
        redisTemplate.opsForValue().set(cacheKey, codeHash, safeInt(userVerificationConfig.getCodeExpireMinutes(), 5), TimeUnit.MINUTES);

        try {
            SmsSendResult sendResult = smsSender.send(phone, normalizedScene, code);
            if (!sendResult.isSuccess()) {
                invalidateById(verification.getId());
                redisTemplate.delete(cacheKey);
                throw new BusinessException(sendResult.getMessage());
            }

            Map<String, Object> result = new HashMap<>();
            result.put("channel", "sms");
            result.put("scene", normalizedScene);
            result.put("provider", sendResult.getProvider());
            result.put("expireIn", safeInt(userVerificationConfig.getCodeExpireMinutes(), 5) * 60);
            result.put("message", sendResult.getMessage());
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            invalidateById(verification.getId());
            redisTemplate.delete(cacheKey);
            log.error("发送手机验证码失败: phone={}, scene={}", maskPhone(phone), normalizedScene);
            throw new BusinessException("验证码发送失败，请稍后再试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void verifyCode(String phone, String scene, String code) {
        String normalizedScene = normalizeScene(scene);
        Integer verificationType = toVerificationType(normalizedScene);
        validatePhone(phone);
        if (ObjectUtils.isEmpty(code)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "验证码不能为空");
        }

        String cacheKey = getCodeCacheKey(phone, verificationType);
        LocalDateTime now = LocalDateTime.now();
        UserVerification verification = userVerificationMapper.selectValidCode(phone, verificationType, now);
        if (verification == null || ObjectUtils.isEmpty(verification.getCodeHash())) {
            redisTemplate.delete(cacheKey);
            throw new BusinessException("验证码不存在或已过期");
        }

        String inputHash = hashCode(code);
        if (!verification.getCodeHash().equals(inputHash)) {
            userVerificationMapper.recordFailedAttempt(
                    verification.getId(), safeInt(userVerificationConfig.getMaxFailCount(), 5), now);
            throw new BusinessException("验证码错误");
        }

        int consumed = userVerificationMapper.consumeValidCode(
                verification.getId(), inputHash, now, LocalDateTime.now());
        if (consumed != 1) {
            redisTemplate.delete(cacheKey);
            throw new BusinessException("验证码不存在或已过期");
        }
        redisTemplate.delete(cacheKey);
    }

    private UserVerification savePendingCode(UserVerification active, String phone, Integer type, String codeHash,
                                             String clientIp, String userAgent) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireTime = now.plusMinutes(safeInt(userVerificationConfig.getCodeExpireMinutes(), 5));
        if (active != null) {
            LambdaUpdateWrapper<UserVerification> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(UserVerification::getId, active.getId())
                    .set(UserVerification::getCode, null)
                    .set(UserVerification::getCodeHash, codeHash)
                    .set(UserVerification::getExpireTime, expireTime)
                    .set(UserVerification::getSendCount, safeInt(active.getSendCount(), 0) + 1)
                    .set(UserVerification::getLastSendTime, now)
                    .set(UserVerification::getClientIp, clientIp)
                    .set(UserVerification::getUserAgent, userAgent)
                    .set(UserVerification::getFailCount, 0)
                    .set(UserVerification::getIsLocked, 0);
            userVerificationMapper.update(null, updateWrapper);
            active.setCode(null);
            active.setCodeHash(codeHash);
            return active;
        }

        UserVerification verification = new UserVerification();
        verification.setTarget(phone);
        verification.setVerificationType(type);
        verification.setCode(null);
        verification.setCodeHash(codeHash);
        verification.setStatus(0);
        verification.setIsUsed(0);
        verification.setExpireTime(expireTime);
        verification.setSendCount(1);
        verification.setLastSendTime(now);
        verification.setClientIp(clientIp);
        verification.setUserAgent(userAgent);
        verification.setFailCount(0);
        verification.setIsLocked(0);
        verification.setDeleted(0);
        userVerificationMapper.insert(verification);
        return verification;
    }

    private UserVerification selectActive(String phone, Integer type) {
        LambdaQueryWrapper<UserVerification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserVerification::getTarget, phone)
                .eq(UserVerification::getVerificationType, type)
                .eq(UserVerification::getStatus, 0)
                .eq(UserVerification::getDeleted, 0)
                .gt(UserVerification::getExpireTime, LocalDateTime.now())
                .orderByDesc(UserVerification::getCreateTime)
                .last("LIMIT 1");
        return userVerificationMapper.selectOne(wrapper);
    }

    private boolean canSendCode(String phone, Integer type) {
        Boolean limited = redisTemplate.hasKey(getSendLimitKey(phone, type));
        if (Boolean.TRUE.equals(limited)) {
            return false;
        }
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        Integer count = userVerificationMapper.countRecentCodes(phone, type, oneHourAgo);
        return safeInt(count, 0) < safeInt(userVerificationConfig.getMaxSendCountPerHour(), 5);
    }


    private void reserveSendWindow(String phone, Integer type, String clientIp) {
        int ttlSeconds = safeInt(userVerificationConfig.getResendIntervalSeconds(), 60);
        String phoneLimitKey = getSendLimitKey(phone, type);
        String ipLimitKey = getIpSendLimitKey(clientIp, type);

        Boolean phoneReserved = redisTemplate.opsForValue().setIfAbsent(phoneLimitKey, "1", ttlSeconds, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(phoneReserved)) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "验证码发送过于频繁，请稍后再试");
        }

        Boolean ipReserved = redisTemplate.opsForValue().setIfAbsent(ipLimitKey, "1", ttlSeconds, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(ipReserved)) {
            redisTemplate.delete(phoneLimitKey);
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "验证码发送过于频繁，请稍后再试");
        }
    }

    private void invalidateById(Long id) {
        if (id == null) {
            return;
        }
        LambdaUpdateWrapper<UserVerification> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(UserVerification::getId, id)
                .set(UserVerification::getStatus, 2);
        userVerificationMapper.update(null, updateWrapper);
    }

    private String normalizeScene(String scene) {
        if (ObjectUtils.isEmpty(scene)) {
            return "reset";
        }
        String normalized = scene.trim().toLowerCase();
        if ("register".equals(normalized) || "reset".equals(normalized)
                || "login".equals(normalized) || "change_phone".equals(normalized)
                || "account_restriction_appeal".equals(normalized)) {
            return normalized;
        }
        throw new BusinessException(ResultCode.PARAM_ERROR, "验证码场景不正确");
    }

    private Integer toVerificationType(String scene) {
        if ("reset".equals(scene)) {
            return VerificationType.RESET_PASSWORD.getCode();
        }
        if ("change_phone".equals(scene)) {
            return VerificationType.CHANGE_PHONE.getCode();
        }
        if ("account_restriction_appeal".equals(scene)) {
            return VerificationType.ACCOUNT_RESTRICTION_APPEAL.getCode();
        }
        return VerificationType.PHONE.getCode();
    }

    private void validatePhone(String phone) {
        if (ObjectUtils.isEmpty(phone) || !phone.matches("^1[3-9]\\d{9}$")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "手机号格式不正确");
        }
    }

    private String generateNumericCode(int length) {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < length; i++) {
            code.append(RANDOM.nextInt(10));
        }
        return code.toString();
    }

    private String hashCode(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(code.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new BusinessException("验证码哈希计算失败");
        }
    }

    private String getCodeCacheKey(String phone, Integer type) {
        return CODE_CACHE_PREFIX + type + ":" + phone;
    }

    private String getSendLimitKey(String phone, Integer type) {
        return SEND_LIMIT_PREFIX + type + ":" + phone;
    }


    private String getIpSendLimitKey(String clientIp, Integer type) {
        String normalizedIp = ObjectUtils.isEmpty(clientIp) ? "unknown" : clientIp;
        return IP_SEND_LIMIT_PREFIX + type + ":" + normalizedIp;
    }

    private int safeInt(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "****";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
