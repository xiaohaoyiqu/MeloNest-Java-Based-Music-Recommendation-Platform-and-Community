   
                      
                                     
   
package com.haoran.music.service.impl;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import com.haoran.music.common.config.VerifyCodeConfig;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.RedisUtils;
import com.haoran.music.service.VerifyCodeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

   
          
  
                                               
                            
   
@Slf4j
@Service
public class VerifyCodeServiceImpl implements VerifyCodeService {

    @Resource
    private RedisUtils redisUtils;

    @Resource
    private VerifyCodeConfig verifyCodeConfig;

    private static final String CODE_PREFIX = "verify:code:";
    private static final String NEED_CHECK_PREFIX = "verify:need:";
    private static final SecureRandom random = new SecureRandom();

    @Override
    public Map<String, Object> generateVerifyCode(String type, String target, String scene) {
        requireHumanCaptchaInput(type, target, scene);
        Map<String, Object> result = new HashMap<>();

        switch (type.toLowerCase(Locale.ROOT)) {
            case "arithmetic":
                result = generateArithmeticCode(target, scene);
                break;
            case "image":
                result = generateImageCode(target, scene);
                break;
            case "sms":
            case "email":
                throw new BusinessException(ResultCode.PARAM_ERROR, "短信或邮箱验证码请使用专用发送接口");
            case "slide":
                result = generateSlideCode(target, scene);
                break;
            default:
                throw new BusinessException("不支持的验证码类型: " + type);
        }

        return result;
    }

    @Override
    public boolean verifyCode(String type, String target, String scene, String code) {
        if (!isValidHumanCaptchaVerification(type, target, scene, code)) {
            return false;
        }
        String normalizedType = type.toLowerCase(Locale.ROOT);
        String key = getCodeKey(normalizedType, target, scene);
        Object cachedObj = redisUtils.get(key);
        String cachedCode = cachedObj != null ? cachedObj.toString() : null;

        if (ObjectUtils.isEmpty(cachedCode)) {
            log.warn("验证码已过期: type={}, scene={}", normalizedType, scene);
            return false;
        }

        String expectedHash = hashCaptcha(normalizedType, target, scene, code);
        boolean valid = redisUtils.compareAndDelete(key, expectedHash);
        if (valid) {
            clearVerifyCodeRequirement(target, scene);
            log.info("验证码验证成功: type={}, scene={}", normalizedType, scene);
        } else {
                                                                                              
                                                                                          
            redisUtils.delete(key);
            log.warn("验证码验证失败: type={}, scene={}", normalizedType, scene);
        }

        return valid;
    }

    @Override
    public boolean needVerifyCode(String target, String scene) {
        if (ObjectUtils.isEmpty(target) || ObjectUtils.isEmpty(scene)) {
            return false;
        }
        return redisUtils.hasKey(getNeedCheckKey(target, scene));
    }

    @Override
    public void requireVerifyCode(String target, String scene) {
        if (ObjectUtils.isEmpty(target) || ObjectUtils.isEmpty(scene)) {
            return;
        }
        Integer expireSeconds = verifyCodeConfig.getExpireSeconds();
        long ttl = ObjectUtils.isEmpty(expireSeconds) || expireSeconds <= 0
                ? 300L : Math.max(getExpireSeconds(), 300L);
        redisUtils.set(getNeedCheckKey(target, scene), "1", ttl, TimeUnit.SECONDS);
        log.info("要求完成验证码: scene={}, ttl={}", scene, ttl);
    }

    @Override
    public void clearVerifyCodeRequirement(String target, String scene) {
        if (ObjectUtils.isEmpty(target) || ObjectUtils.isEmpty(scene)) {
            return;
        }
        redisUtils.delete(getNeedCheckKey(target, scene));
    }
    @Override
    public Map<String, Object> generateImageCode(String target, String scene) {
        VerifyCodeConfig.Image imageConfig = verifyCodeConfig.getImage();
        if (imageConfig == null || !Boolean.TRUE.equals(imageConfig.getEnabled())) {
            throw new BusinessException("图形验证码未启用");
        }
        if (!"hutool".equalsIgnoreCase(imageConfig.getProvider())) {
            throw new BusinessException("图形验证码供应商配置不受支持");
        }
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(
                bounded(imageConfig.getWidth(), 80, 400, 130),
                bounded(imageConfig.getHeight(), 32, 160, 48),
                bounded(imageConfig.getLength(), 4, 8, 4),
                bounded(imageConfig.getInterferenceCount(), 20, 200, 80));

        String key = getCodeKey("image", target, scene);
        redisUtils.set(key, hashCaptcha("image", target, scene, captcha.getCode()),
                getExpireSeconds(), TimeUnit.SECONDS);

        Map<String, Object> result = new HashMap<>();
        result.put("type", "image");
        result.put("imageBase64", captcha.getImageBase64Data());
        result.put("expireAt", System.currentTimeMillis() + getExpireSeconds() * 1000L);

        log.info("生成图形验证码: scene={}", scene);

        return result;
    }

    @Override
    public Map<String, Object> generateSlideCode(String target, String scene) {
        throw new BusinessException("滑块验证码未配置真实供应商");
    }

                                                     

       
              
       
    private Map<String, Object> generateArithmeticCode(String target, String scene) {
        int a = random.nextInt(getArithmeticMaxOperand());
        int b = random.nextInt(getArithmeticMaxOperand());
        String captchaText = a + " + " + b + " = ?";
        int answer = a + b;

        String key = getCodeKey("arithmetic", target, scene);
        redisUtils.set(key, hashCaptcha("arithmetic", target, scene, String.valueOf(answer)),
                getExpireSeconds(), TimeUnit.SECONDS);

        Map<String, Object> result = new HashMap<>();
        result.put("type", "arithmetic");
        result.put("captcha", captchaText);
        result.put("expireAt", System.currentTimeMillis() + getExpireSeconds() * 1000L);

        log.info("生成算术验证码: scene={}", scene);

        return result;
    }

       
                                                   
      
  
    private int getArithmeticMaxOperand() {
        Integer maxOperand = verifyCodeConfig.getArithmeticMaxOperand();
        return ObjectUtils.isEmpty(maxOperand) || maxOperand <= 0 ? 10 : maxOperand;
    }

    private String getCodeKey(String type, String target, String scene) {
        return CODE_PREFIX + type + ":" + scene + ":" + target;
    }

    private void requireHumanCaptchaInput(String type, String target, String scene) {
        if (!Boolean.TRUE.equals(verifyCodeConfig.getEnabled())) {
            throw new BusinessException("验证码服务未启用");
        }
        if (ObjectUtils.isEmpty(type) || ObjectUtils.isEmpty(target) || ObjectUtils.isEmpty(scene)
                || target.length() > 128 || !scene.matches("^[A-Za-z0-9:_-]{1,64}$")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "验证码参数不正确");
        }
    }

    private boolean isValidHumanCaptchaVerification(String type, String target, String scene, String code) {
        if (!Boolean.TRUE.equals(verifyCodeConfig.getEnabled())
                || ObjectUtils.isEmpty(type) || ObjectUtils.isEmpty(target)
                || ObjectUtils.isEmpty(scene) || ObjectUtils.isEmpty(code)
                || target.length() > 128 || !scene.matches("^[A-Za-z0-9:_-]{1,64}$")
                || !code.matches("^[A-Za-z0-9]{1,16}$")) {
            return false;
        }
        String normalizedType = type.toLowerCase(Locale.ROOT);
        return "arithmetic".equals(normalizedType) || "image".equals(normalizedType);
    }

    private String hashCaptcha(String type, String target, String scene, String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((type + "\n" + scene + "\n" + target + "\n" + code)
                    .getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (Exception e) {
            throw new BusinessException("验证码处理失败");
        }
    }

    private long getExpireSeconds() {
        Integer value = verifyCodeConfig.getExpireSeconds();
        return value == null || value <= 0 ? 300L : Math.min(value.longValue(), 1800L);
    }

    private int bounded(Integer value, int min, int max, int fallback) {
        if (value == null) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    private String getNeedCheckKey(String target, String scene) {
        return NEED_CHECK_PREFIX + scene + ":" + target;
    }
}
