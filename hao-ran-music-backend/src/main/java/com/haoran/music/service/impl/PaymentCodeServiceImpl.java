   
                      
                       
   

package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.haoran.music.entity.PaymentConfigEntity;
import com.haoran.music.common.config.PaymentConfig;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.util.ExternalUrlGuard;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.PaymentSshSessionFactory;
import com.haoran.music.common.util.UrlHelper;
import com.haoran.music.entity.PaymentCodeLog;
import com.haoran.music.entity.User;
import com.haoran.music.mapper.PaymentCodeLogMapper;
import com.haoran.music.mapper.PaymentConfigEntityMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.PaymentCodeService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.io.File;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.Session;

   
          
   
@Slf4j
@Service
public class PaymentCodeServiceImpl implements PaymentCodeService {

    @javax.annotation.Resource
    private com.haoran.music.service.CreatorEligibilityService creatorEligibilityService;

    private static final long MAX_PAYMENT_IMAGE_BYTES = 10L * 1024L * 1024L;

    private final PaymentCodeLogMapper paymentCodeLogMapper;
    private final PaymentConfigEntityMapper paymentConfigEntityMapper;
    private final UserMapper userMapper;
    private final PaymentConfig paymentConfig;
    private final OkHttpClient imageHttpClient;
    private final PaymentSecurityService paymentSecurityService;
    private final PaymentSshSessionFactory paymentSshSessionFactory;

    public PaymentCodeServiceImpl(
                                PaymentConfigEntityMapper paymentConfigEntityMapper,
                                PaymentCodeLogMapper paymentCodeLogMapper,
                                UserMapper userMapper,
                                PaymentConfig paymentConfig,
                                PaymentSecurityService paymentSecurityService,
                                PaymentSshSessionFactory paymentSshSessionFactory) {
        this.paymentCodeLogMapper = paymentCodeLogMapper;
        this.paymentConfigEntityMapper = paymentConfigEntityMapper;
        this.userMapper = userMapper;
        this.paymentConfig = paymentConfig;
        this.paymentSecurityService = paymentSecurityService;
        this.paymentSshSessionFactory = paymentSshSessionFactory;
        this.imageHttpClient = new OkHttpClient.Builder()
                .connectTimeout(paymentConfig.getSsh().getConnectTimeout(), TimeUnit.MILLISECONDS)
                .readTimeout(paymentConfig.getSsh().getReadTimeout(), TimeUnit.MILLISECONDS)
                .writeTimeout(paymentConfig.getSsh().getReadTimeout(), TimeUnit.MILLISECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> uploadPaymentCode(Long userId, String paymentType,
                                               String imageUrl, Long operatorId) {
                 
        if (!isValidPaymentType(paymentType)) {
            throw new BusinessException("不支持的支付类型");
        }

                
        if (userId != null) {
            creatorEligibilityService.requireEligible(userId, "上传创作者收款码");
        }

                
        String md5Hash = calculateMd5(imageUrl);

                                                        
        LambdaQueryWrapper<com.haoran.music.entity.PaymentConfigEntity> wrapper = new LambdaQueryWrapper<>();
        applyPaymentOwnerFilter(wrapper, userId);
        wrapper.eq(com.haoran.music.entity.PaymentConfigEntity::getPaymentType, paymentType);

        com.haoran.music.entity.PaymentConfigEntity existing = paymentConfigEntityMapper.selectOne(wrapper);

                
        String verifyCode = generateVerifyCode();

                  
        String qrCodeWithVerify = compositeVerifyCode(imageUrl, verifyCode);

        if (existing != null) {
                     
            String oldUrl = existing.getQrCodeUrl();
            String oldMd5 = existing.getMd5Hash();

            existing.setQrCodeUrl(imageUrl);
            existing.setQrCodeWithVerify(qrCodeWithVerify);
            existing.setVerifyCode(paymentSecurityService.hashConfigCode(verifyCode));
            existing.setMd5Hash(md5Hash);
            existing.setIsEnabled(1);
            existing.setLastScanTime(LocalDateTime.now());

            paymentConfigEntityMapper.updateById(existing);

                   
            logOperation(existing.getId(), operatorId, "update",
                    oldUrl, imageUrl, oldMd5, md5Hash,
                    paymentSecurityService.hashConfigCode(verifyCode), "更新付款码");

            log.info("更新付款码: userId={}, paymentType={}", userId, paymentType);

        } else {
                    
            com.haoran.music.entity.PaymentConfigEntity config = new com.haoran.music.entity.PaymentConfigEntity();
            config.setUserId(userId);
            config.setUserType(userId == null ? "platform" : "creator");
            config.setPaymentType(paymentType);
            config.setQrCodeUrl(imageUrl);
            config.setQrCodeWithVerify(qrCodeWithVerify);
            config.setVerifyCode(paymentSecurityService.hashConfigCode(verifyCode));
            config.setMd5Hash(md5Hash);
            config.setIsEnabled(1);
            config.setDailyLimit(new BigDecimal("999999"));
            config.setTodayReceived(BigDecimal.ZERO);
            config.setLastResetDate(LocalDate.now());

            paymentConfigEntityMapper.insert(config);

                   
            logOperation(config.getId(), operatorId, "create",
                    null, imageUrl, null, md5Hash,
                    paymentSecurityService.hashConfigCode(verifyCode), "上传付款码");

            log.info("创建付款码: userId={}, paymentType={}", userId, paymentType);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("verifyCode", verifyCode);
        result.put("qrCodeUrl", imageUrl);
        result.put("qrCodeWithVerify", qrCodeWithVerify);
        result.put("md5Hash", md5Hash);
        result.put("message", "付款码上传成功");

        return result;
    }

    @Override
    public Map<String, Object> getPaymentConfig(Long userId, String paymentType) {
        LambdaQueryWrapper<com.haoran.music.entity.PaymentConfigEntity> wrapper = new LambdaQueryWrapper<>();
        applyPaymentOwnerFilter(wrapper, userId);
        wrapper.eq(com.haoran.music.entity.PaymentConfigEntity::getPaymentType, paymentType)
                .eq(com.haoran.music.entity.PaymentConfigEntity::getIsEnabled, 1);

        com.haoran.music.entity.PaymentConfigEntity config = paymentConfigEntityMapper.selectOne(wrapper);

        if (config == null) {
            throw new BusinessException("付款码配置不存在");
        }

        return buildConfigResult(config);
    }

    @Override
    public Map<String, Object> getPlatformPaymentCode(String paymentType) {
        return getPaymentConfig(null, paymentType);
    }

    @Override
    public Map<String, Object> getCreatorPaymentCode(Long creatorId, String paymentType) {
        return getPaymentConfig(creatorId, paymentType);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> scanPaymentCodes(String scanPath) {
        Map<String, Object> result = new HashMap<>();
        Session session = null;
        int wechatCount = 0;
        int alipayCount = 0;

        try {
            session = paymentSshSessionFactory.connect();

            String basePath = resolveScanBasePath(scanPath);

                                                                                            
            wechatCount = scanPaymentCodePath(session, basePath + "platform/wechat/", null, "wechat", result);
            alipayCount = scanPaymentCodePath(session, basePath + "platform/alipay/", null, "alipay", result);
            scanCreatorPaymentCodes(session, basePath + "creator/", result);

            result.putIfAbsent("created", 0);
            result.putIfAbsent("updated", 0);
            result.putIfAbsent("deleted", 0);
            result.put("wechatCount", wechatCount);
            result.put("alipayCount", alipayCount);
            result.put("message", "扫描完成");

            log.info("扫描付款码完成: created={}, updated={}, deleted={}",
                    result.get("created"), result.get("updated"), result.get("deleted"));

        } catch (Exception e) {
            log.error("SSH扫描付款码失败: {}", e.getClass().getSimpleName());
            result.put("error", e.getMessage());
            result.put("message", "扫描失败: " + e.getMessage());
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }

        return result;
    }
    private String resolveScanBasePath(String scanPath) {
        String basePath = ObjectUtils.isNotEmpty(scanPath) ? scanPath : paymentConfig.getCode().getScanBasePath();
        if (ObjectUtils.isEmpty(basePath) || basePath.endsWith("/")) {
            return basePath;
        }
        return basePath + "/";
    }

    @Override
    public Boolean verifyCode(Long configId, String verifyCode) {
        com.haoran.music.entity.PaymentConfigEntity config = paymentConfigEntityMapper.selectById(configId);
        if (config == null) {
            return false;
        }

        return ObjectUtils.isNotEmpty(verifyCode)
                && paymentSecurityService.matchesConfigCode(verifyCode, config.getVerifyCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> refreshVerifyCode(Long configId, Long operatorId) {
        com.haoran.music.entity.PaymentConfigEntity config = paymentConfigEntityMapper.selectById(configId);
        if (config == null) {
            throw new BusinessException("配置不存在");
        }

        String newVerifyCode = generateVerifyCode();

                      
        String newQrCodeWithVerify = compositeVerifyCode(config.getQrCodeUrl(), newVerifyCode);

        String newVerifyCodeHash = paymentSecurityService.hashConfigCode(newVerifyCode);
        config.setVerifyCode(newVerifyCodeHash);
        config.setQrCodeWithVerify(newQrCodeWithVerify);
        paymentConfigEntityMapper.updateById(config);

               
        logOperation(configId, operatorId, "update",
                config.getQrCodeUrl(), config.getQrCodeUrl(),
                config.getMd5Hash(), config.getMd5Hash(),
                newVerifyCodeHash, "刷新付款码验证码");

        log.info("刷新付款码验证码: configId={}", configId);

        Map<String, Object> result = new HashMap<>();
        result.put("verifyCode", newVerifyCode);
        result.put("qrCodeWithVerify", newQrCodeWithVerify);
        result.put("message", "验证码已刷新");

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean disablePaymentCode(Long configId, Long operatorId, String reason) {
        com.haoran.music.entity.PaymentConfigEntity config = paymentConfigEntityMapper.selectById(configId);
        if (config == null) {
            throw new BusinessException("配置不存在");
        }

        config.setIsEnabled(0);
        paymentConfigEntityMapper.updateById(config);

               
        logOperation(configId, operatorId, "disable",
                null, null, null, null, null, "禁用付款码: " + reason);

        log.info("禁用付款码: configId={}, reason={}", configId, reason);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean enablePaymentCode(Long configId, Long operatorId) {
        com.haoran.music.entity.PaymentConfigEntity config = paymentConfigEntityMapper.selectById(configId);
        if (config == null) {
            throw new BusinessException("配置不存在");
        }

        config.setIsEnabled(1);
        paymentConfigEntityMapper.updateById(config);

               
        logOperation(configId, operatorId, "scan",
                null, null, null, null, null, "启用付款码");

        log.info("启用付款码: configId={}", configId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean resetDailyLimit(Long configId) {
        com.haoran.music.entity.PaymentConfigEntity config = paymentConfigEntityMapper.selectById(configId);
        if (config == null) {
            return false;
        }

        config.setTodayReceived(BigDecimal.ZERO);
        config.setLastResetDate(LocalDate.now());
        paymentConfigEntityMapper.updateById(config);

        return true;
    }

    @Override
    public Boolean checkDailyLimit(Long configId, BigDecimal amount) {
        com.haoran.music.entity.PaymentConfigEntity config = paymentConfigEntityMapper.selectById(configId);
        if (config == null) {
            return false;
        }

                   
        if (!config.getLastResetDate().equals(LocalDate.now())) {
            resetDailyLimit(configId);
            config = paymentConfigEntityMapper.selectById(configId);
        }

        BigDecimal todayReceived = config.getTodayReceived() != null ?
                config.getTodayReceived() : BigDecimal.ZERO;
        BigDecimal remaining = config.getDailyLimit().subtract(todayReceived);

        return remaining.compareTo(amount) >= 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean addTodayReceived(Long configId, BigDecimal amount) {
        com.haoran.music.entity.PaymentConfigEntity config = paymentConfigEntityMapper.selectById(configId);
        if (config == null) {
            return false;
        }

        BigDecimal todayReceived = config.getTodayReceived() != null ?
                config.getTodayReceived() : BigDecimal.ZERO;
        config.setTodayReceived(todayReceived.add(amount));
        paymentConfigEntityMapper.updateById(config);

        return true;
    }

    @Override
    public Map<String, Object> getPaymentCodeLogs(Long configId, Integer page, Integer size) {
        Page<PaymentCodeLog> pageParam = new Page<>(page, size);

        LambdaQueryWrapper<PaymentCodeLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaymentCodeLog::getConfigId, configId)
                .orderByDesc(PaymentCodeLog::getCreateTime);

        Page<PaymentCodeLog> resultPage = paymentCodeLogMapper.selectPage(pageParam, wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("list", resultPage.getRecords());
        result.put("total", resultPage.getTotal());
        result.put("page", page);
        result.put("size", size);

        return result;
    }

    @Override
    public String compositeVerifyCode(String originalUrl, String verifyCode) {
        try {
                       
            byte[] imageBytes = downloadImage(originalUrl);

            if (imageBytes == null || imageBytes.length == 0) {
                log.warn("event=payment_code_image_download_empty");
                return originalUrl;
            }

                   
            BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (originalImage == null) {
                log.warn("event=payment_code_image_decode_failed");
                return originalUrl;
            }

                              
            int newHeight = originalImage.getHeight() + 40;
            BufferedImage watermarkedImage = new BufferedImage(
                    originalImage.getWidth(),
                    newHeight,
                    BufferedImage.TYPE_INT_RGB
            );

            Graphics2D g = watermarkedImage.createGraphics();

                     
            g.drawImage(originalImage, 0, 0, null);

                        
            g.setColor(new Color(240, 240, 240));
            g.fillRect(0, originalImage.getHeight(), originalImage.getWidth(), 40);

                      
            g.setColor(new Color(100, 100, 100));
            g.setFont(new Font("Arial", Font.BOLD, 16));
            FontMetrics fm = g.getFontMetrics();
            String text = "验证码: " + verifyCode;
            int textWidth = fm.stringWidth(text);
            int x = (originalImage.getWidth() - textWidth) / 2;
            g.drawString(text, x, originalImage.getHeight() + 25);

            g.dispose();

                                               
                                                     
            log.debug("event=payment_code_watermark_rendered");
            return originalUrl;

        } catch (Exception e) {
            log.error("event=payment_code_watermark_failed errorType={}", e.getClass().getSimpleName());
            return originalUrl;
        }
    }

    @Override
    public String calculateMd5(String imageUrl) {
        try {
                             
            byte[] imageBytes = downloadImage(imageUrl);

            if (imageBytes != null && imageBytes.length > 0) {
                MessageDigest md = MessageDigest.getInstance("MD5");
                byte[] digest = md.digest(imageBytes);

                StringBuilder sb = new StringBuilder();
                for (byte b : digest) {
                    sb.append(String.format("%02x", b));
                }

                log.info("event=payment_code_digest_calculated");
                return sb.toString();
            }

                                     
            String input = imageUrl + System.currentTimeMillis();
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }

            return sb.toString();

        } catch (Exception e) {
            log.error("event=payment_code_digest_failed errorType={}", e.getClass().getSimpleName());
            return UUID.randomUUID().toString().replace("-", "");
        }
    }

                                                     

    private byte[] downloadImage(String imageUrl) {
        String resolvedUrl = resolvePaymentImageUrl(imageUrl);
        ExternalUrlGuard.Validation validation = ExternalUrlGuard.validate(
                resolvedUrl, paymentConfig.getUrlPrefix());
        if (!validation.isAllowed()) {
            log.warn("拒绝下载不安全的付款码图片: reason={}", validation.getReason());
            return null;
        }

        Request request = new Request.Builder()
                .url(resolvedUrl)
                .get()
                .build();

        try (Response response = imageHttpClient.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                return null;
            }
            if (body.contentLength() > MAX_PAYMENT_IMAGE_BYTES) {
                log.warn("付款码图片超过大小限制: bytes={}", body.contentLength());
                return null;
            }

            try (InputStream input = body.byteStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                long total = 0;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_PAYMENT_IMAGE_BYTES) {
                        log.warn("付款码图片读取过程中超过大小限制: bytes={}", total);
                        return null;
                    }
                    output.write(buffer, 0, read);
                }
                return output.toByteArray();
            }
        } catch (Exception e) {
            log.warn("下载付款码图片失败: error={}", e.getClass().getSimpleName());
            return null;
        }
    }

    private String resolvePaymentImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            return imageUrl;
        }
        String value = imageUrl.trim();
        if (value.startsWith("/") && !value.startsWith("//")) {
            return UrlHelper.toFullUrl(value, paymentConfig.getUrlPrefix());
        }
        return value;
    }

    private List<String> executeRemoteLines(Session session, String command) throws Exception {
        ChannelExec channel = null;
        List<String> lines = new ArrayList<>();

        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);

            try (java.io.InputStream in = channel.getInputStream();
                 java.io.BufferedReader reader = new java.io.BufferedReader(
                         new java.io.InputStreamReader(in, StandardCharsets.UTF_8))) {
                channel.connect();

                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            }
            return lines;
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
        }
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

       
                       
      
                                                           
                           
       
    private int scanPaymentCodePath(Session session, String path, Long userId,
                                     String paymentType, Map<String, Object> result) {
        int count = 0;

        try {
            String command = "find " + shellQuote(path)
                    + " -maxdepth 1 -type f \\( -iname '*.png' -o -iname '*.jpg' -o -iname '*.jpeg' \\) -printf '%f\\n' 2>/dev/null";

            for (String filename : executeRemoteLines(session, command)) {
                if (ObjectUtils.isEmpty(filename)) {
                    continue;
                }

                String md5Hash = filename.substring(0, filename.lastIndexOf('.'));
                processScannedFile(path + filename, userId, paymentType, md5Hash, result);
                count++;
            }

        } catch (Exception e) {
            log.error("event=payment_code_scan_path_failed errorType={}", e.getClass().getSimpleName());
        }

        return count;
    }
       
               
       
    private void scanCreatorPaymentCodes(Session session, String creatorPath,
                                         Map<String, Object> result) {
        try {
            String command = "find " + shellQuote(creatorPath)
                    + " -mindepth 1 -maxdepth 1 -type d -printf '%f\\n' 2>/dev/null";

            for (String creatorDir : executeRemoteLines(session, command)) {
                try {
                    Long creatorId = Long.parseLong(creatorDir);

                    scanPaymentCodePath(session, creatorPath + creatorDir + "/wechat/",
                            creatorId, "wechat", result);
                    scanPaymentCodePath(session, creatorPath + creatorDir + "/alipay/",
                            creatorId, "alipay", result);

                } catch (NumberFormatException e) {
                                                    
                }
            }

        } catch (Exception e) {
            log.error("扫描创作者付款码失败: error={}", e.getClass().getSimpleName());
        }
    }

       
               
       
    private void processScannedFile(String filePath, Long userId, String paymentType,
                                    String md5Hash, Map<String, Object> result) {
        try {
            String publicUrl = resolvePaymentCodePublicUrl(filePath);

            LambdaQueryWrapper<com.haoran.music.entity.PaymentConfigEntity> wrapper =
                    new LambdaQueryWrapper<>();
            applyPaymentOwnerFilter(wrapper, userId);
            wrapper.eq(com.haoran.music.entity.PaymentConfigEntity::getPaymentType, paymentType);

            com.haoran.music.entity.PaymentConfigEntity existing =
                    paymentConfigEntityMapper.selectOne(wrapper);

            if (existing == null) {
                com.haoran.music.entity.PaymentConfigEntity config =
                        new com.haoran.music.entity.PaymentConfigEntity();
                String verifyCode = generateVerifyCode();

                config.setUserId(userId);
                config.setUserType(userId == null ? "platform" : "creator");
                config.setPaymentType(paymentType);
                config.setQrCodeUrl(publicUrl);
                config.setQrCodeWithVerify(compositeVerifyCode(publicUrl, verifyCode));
                config.setVerifyCode(paymentSecurityService.hashConfigCode(verifyCode));
                config.setMd5Hash(md5Hash);
                config.setIsEnabled(1);
                config.setDailyLimit(new BigDecimal("999999"));
                config.setTodayReceived(BigDecimal.ZERO);
                config.setLastResetDate(LocalDate.now());
                config.setLastScanTime(LocalDateTime.now());

                paymentConfigEntityMapper.insert(config);

                Integer created = (Integer) result.getOrDefault("created", 0);
                result.put("created", created + 1);

                log.info("event=payment_code_discovered userId={} paymentType={}", userId, paymentType);
            } else {
                String verifyCode = generateVerifyCode();

                existing.setQrCodeUrl(publicUrl);
                existing.setQrCodeWithVerify(compositeVerifyCode(publicUrl, verifyCode));
                existing.setVerifyCode(paymentSecurityService.hashConfigCode(verifyCode));
                existing.setMd5Hash(md5Hash);
                existing.setIsEnabled(1);
                existing.setLastScanTime(LocalDateTime.now());
                paymentConfigEntityMapper.updateById(existing);

                Integer updated = (Integer) result.getOrDefault("updated", 0);
                result.put("updated", updated + 1);
            }

        } catch (Exception e) {
            log.error("event=payment_code_scanned_file_failed userId={} paymentType={} errorType={}",
                    userId, paymentType, e.getClass().getSimpleName());
        }
    }

    private String resolvePaymentCodePublicUrl(String filePath) {
        if (ObjectUtils.isEmpty(filePath) || ObjectUtils.isEmpty(paymentConfig.getUrlPrefix())) {
            return filePath;
        }

        String basePath = resolveScanBasePath(paymentConfig.getScanPath()).replace("\\", "/");
        String normalizedFilePath = filePath.replace("\\", "/");
        String urlPrefix = paymentConfig.getUrlPrefix().endsWith("/")
                ? paymentConfig.getUrlPrefix() : paymentConfig.getUrlPrefix() + "/";

        if (normalizedFilePath.startsWith(basePath)) {
            return urlPrefix + normalizedFilePath.substring(basePath.length());
        }
        return normalizedFilePath;
    }
       
             
       
    private Boolean isValidPaymentType(String paymentType) {
        return "wechat".equals(paymentType) || "alipay".equals(paymentType);
    }

    private void applyPaymentOwnerFilter(LambdaQueryWrapper<com.haoran.music.entity.PaymentConfigEntity> wrapper,
                                         Long userId) {
        if (userId == null) {
            wrapper.isNull(com.haoran.music.entity.PaymentConfigEntity::getUserId);
        } else {
            wrapper.eq(com.haoran.music.entity.PaymentConfigEntity::getUserId, userId);
        }
    }

       
            
       
    private String generateVerifyCode() {
        return paymentSecurityService.generateVerificationCode();
    }

       
             
       
    private void logOperation(Long configId, Long operatorId, String actionType,
                             String oldUrl, String newUrl, String oldMd5, String newMd5,
                             String verifyCode, String remark) {
        PaymentCodeLog operationLog = new PaymentCodeLog();
        operationLog.setConfigId(configId);
        operationLog.setOperatorId(operatorId);
        operationLog.setActionType(actionType);
        operationLog.setOldUrl(oldUrl);
        operationLog.setNewUrl(newUrl);
        operationLog.setOldMd5(oldMd5);
        operationLog.setNewMd5(newMd5);
        operationLog.setVerifyCode(verifyCode);
        operationLog.setRemark(remark);

        paymentCodeLogMapper.insert(operationLog);
    }

       
             
       
    private Map<String, Object> buildConfigResult(com.haoran.music.entity.PaymentConfigEntity config) {
        Map<String, Object> result = new HashMap<>();
        result.put("configId", config.getId());
        result.put("userId", config.getUserId());
        result.put("userType", config.getUserType());
        result.put("paymentType", config.getPaymentType());
        result.put("qrCodeUrl", config.getQrCodeUrl());
        result.put("qrCodeWithVerify", config.getQrCodeWithVerify());
        result.put("isEnabled", config.getIsEnabled());
        result.put("dailyLimit", config.getDailyLimit());
        result.put("todayReceived", config.getTodayReceived());
        result.put("lastScanTime", config.getLastScanTime());
        return result;
    }
}

