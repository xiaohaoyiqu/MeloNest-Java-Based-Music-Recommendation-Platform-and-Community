




package com.haoran.music.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.haoran.music.common.config.UserVerificationConfig;
import com.haoran.music.common.result.Result;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.entity.UserVerification;
import com.haoran.music.mapper.UserVerificationMapper;
import com.haoran.music.service.UserVerificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.TimeUnit;




@Slf4j
@Service
public class UserVerificationServiceImpl implements UserVerificationService {

    @Autowired
    private UserVerificationMapper userVerificationMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private UserVerificationConfig userVerificationConfig;





    private static final String CODE_CACHE_PREFIX = "verification:code:";
    private static final String SEND_LIMIT_PREFIX = "verification:limit:";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> sendVerificationCode(String target, Integer type, String clientIp) {

        if (StrUtil.isBlank(target)) {
            return Result.error("目标不能为空");
        }
        if (ObjectUtils.isEmpty(type)) {
            return Result.error("验证类型不能为空");
        }


        if (!canSendCode(target, type)) {
            return Result.error("发送过于频繁，请稍后再试");
        }


        if (!isValidTarget(target, type)) {
            return Result.error("目标格式不正确");
        }


        String code = generateNumericCode(userVerificationConfig.getCodeLength());


        LambdaQueryWrapper<UserVerification> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserVerification::getTarget, target)
                .eq(UserVerification::getVerificationType, type)
                .eq(UserVerification::getStatus, 0)
                .eq(UserVerification::getIsLocked, 0);
        UserVerification existing = userVerificationMapper.selectOne(queryWrapper);

        if (ObjectUtils.isNotEmpty(existing)) {

            LambdaUpdateWrapper<UserVerification> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(UserVerification::getId, existing.getId())
                    .set(UserVerification::getCode, null)
                    .set(UserVerification::getCodeHash, hashCode(code))
                    .set(UserVerification::getExpireTime, LocalDateTime.now().plusMinutes(userVerificationConfig.getCodeExpireMinutes()))
                    .set(UserVerification::getSendCount, existing.getSendCount() + 1)
                    .set(UserVerification::getLastSendTime, LocalDateTime.now())
                    .set(UserVerification::getClientIp, clientIp);
            userVerificationMapper.update(null, updateWrapper);
        } else {

            UserVerification verification = new UserVerification();
            verification.setTarget(target);
            verification.setVerificationType(type);
            verification.setCode(null);
            verification.setCodeHash(hashCode(code));
            verification.setStatus(0);
            verification.setIsUsed(0);
            verification.setExpireTime(LocalDateTime.now().plusMinutes(userVerificationConfig.getCodeExpireMinutes()));
            verification.setSendCount(1);
            verification.setLastSendTime(LocalDateTime.now());
            verification.setClientIp(clientIp);
            verification.setFailCount(0);
            verification.setIsLocked(0);
            verification.setDeleted(0);
            userVerificationMapper.insert(verification);
        }


        String cacheKey = CODE_CACHE_PREFIX + target + ":" + type;
        redisTemplate.opsForValue().set(cacheKey, hashCode(code), userVerificationConfig.getCodeExpireMinutes(), TimeUnit.MINUTES);


        String limitKey = SEND_LIMIT_PREFIX + target + ":" + type;
        redisTemplate.opsForValue().set(limitKey, "1", userVerificationConfig.getResendIntervalSeconds(), TimeUnit.SECONDS);



        log.info("Verification code delivery is disabled: target={}, type={}", maskTarget(target), type);
        redisTemplate.delete(limitKey);
        invalidateCode(target, type);
        return Result.error("短信服务暂未配置，请稍后再试");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Boolean> verifyCode(String target, Integer type, String code) {

        if (StrUtil.isBlank(target) || StrUtil.isBlank(code)) {
            return Result.error("参数不能为空");
        }
        if (ObjectUtils.isEmpty(type)) {
            return Result.error("验证类型不能为空");
        }

        String cacheKey = CODE_CACHE_PREFIX + target + ":" + type;
        String inputHash = hashCode(code);
        LocalDateTime now = LocalDateTime.now();
        UserVerification verification = userVerificationMapper.selectValidCode(target, type, now);

        if (ObjectUtils.isEmpty(verification)) {
            redisTemplate.delete(cacheKey);
            return Result.error("验证码不存在或已过期");
        }


        if (!inputHash.equals(verification.getCodeHash())) {
            userVerificationMapper.recordFailedAttempt(
                    verification.getId(), safeMaxFailCount(), now);
            return Result.error("验证码错误");
        }

        int consumed = userVerificationMapper.consumeValidCode(
                verification.getId(), inputHash, now, LocalDateTime.now());
        redisTemplate.delete(cacheKey);
        return consumed == 1
                ? Result.success(true)
                : Result.error("验证码不存在或已过期");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Boolean> verifyCodeByUserId(Long userId, Integer type, String code) {

        if (ObjectUtils.isEmpty(userId) || StrUtil.isBlank(code)) {
            return Result.error("参数不能为空");
        }


        List<UserVerification> verifications = userVerificationMapper.selectByUserAndType(userId, type, 1);

        if (ObjectUtils.isEmpty(verifications) || verifications.isEmpty()) {
            return Result.error("验证码不存在");
        }

        UserVerification verification = verifications.get(0);


        if (verification.getExpireTime().isBefore(LocalDateTime.now())) {
            return Result.error("验证码已过期");
        }


        if (ObjectUtils.isNotEmpty(verification.getIsUsed()) && verification.getIsUsed() == 1) {
            return Result.error("验证码已使用");
        }

        LocalDateTime now = LocalDateTime.now();
        String inputHash = hashCode(code);
        if (!inputHash.equals(verification.getCodeHash())) {
            userVerificationMapper.recordFailedAttempt(
                    verification.getId(), safeMaxFailCount(), now);
            return Result.error("验证码错误");
        }

        int consumed = userVerificationMapper.consumeValidCode(
                verification.getId(), inputHash, now, LocalDateTime.now());
        return consumed == 1
                ? Result.success(true)
                : Result.error("验证码不存在或已过期");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void invalidateCode(String target, Integer type) {
        LambdaUpdateWrapper<UserVerification> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(UserVerification::getTarget, target)
                .eq(UserVerification::getVerificationType, type)
                .eq(UserVerification::getStatus, 0)
                .set(UserVerification::getStatus, 2);
        userVerificationMapper.update(null, updateWrapper);


        String cacheKey = CODE_CACHE_PREFIX + target + ":" + type;
        redisTemplate.delete(cacheKey);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cleanExpiredCodes() {
        LocalDateTime now = LocalDateTime.now();
        List<UserVerification> expiredCodes = userVerificationMapper.selectExpiredCodes(now);

        if (ObjectUtils.isEmpty(expiredCodes) || expiredCodes.isEmpty()) {
            return;
        }

        for (UserVerification verification : expiredCodes) {
            LambdaUpdateWrapper<UserVerification> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(UserVerification::getId, verification.getId())
                    .set(UserVerification::getStatus, 2);
            userVerificationMapper.update(null, updateWrapper);
        }

        log.info("清理过期验证码完成: count={}", expiredCodes.size());
    }

    @Override
    public Boolean canSendCode(String target, Integer type) {

        String limitKey = SEND_LIMIT_PREFIX + target + ":" + type;
        Boolean exists = redisTemplate.hasKey(limitKey);
        if (Boolean.TRUE.equals(exists)) {
            return false;
        }


        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        Integer count = userVerificationMapper.countRecentCodes(target, type, oneHourAgo);

        return count < userVerificationConfig.getMaxSendCountPerHour();
    }

    @Override
    public List<UserVerification> getUserVerifications(Long userId, Integer type, Integer limit) {
        if (ObjectUtils.isEmpty(limit)) {
            limit = userVerificationConfig.getDefaultQueryLimit();
        }
        return userVerificationMapper.selectByUserAndType(userId, type, limit);
    }




    private Boolean isValidTarget(String target, Integer type) {

        if (type == 0 || type == 4) {
            return target.matches("^[A-Za-z0-9+_.-]+@(.+)$");
        }

        if (type == 1 || type == 2 || type == 3) {
            return target.matches("^1[3-9]\\d{9}$");
        }
        return false;
    }





    private String generateNumericCode(Integer length) {
        int realLength = ObjectUtils.isEmpty(length) || length <= 0 ? 6 : length;
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < realLength; i++) {
            code.append(RANDOM.nextInt(10));
        }
        return code.toString();
    }

    private String maskTarget(String target) {
        if (target == null || target.length() < 7) {
            return "****";
        }
        if (target.contains("@")) {
            int index = target.indexOf('@');
            return target.substring(0, 1) + "****" + target.substring(index);
        }
        return target.substring(0, 3) + "****" + target.substring(target.length() - 4);
    }
    private String hashCode(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(code.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("哈希计算失败: {}", e.getClass().getSimpleName());

            return String.valueOf(code.hashCode());
        }
    }

    private int safeMaxFailCount() {
        Integer configured = userVerificationConfig.getMaxFailCount();
        return configured == null || configured <= 0 ? 5 : configured;
    }
}
