   
                      
   
package com.haoran.music.service.impl;

import com.haoran.music.common.config.SmsConfig;
import com.haoran.music.service.AliyunSmsGateway;
import com.haoran.music.service.SmsSendResult;
import com.haoran.music.service.SmsSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

   
                                                                                   
   
@Slf4j
@Service
@ConditionalOnProperty(prefix = "sms", name = "enabled", havingValue = "true")
public class AliyunSmsSender implements SmsSender {

    private static final String PROVIDER = "aliyun";
    private static final String GENERIC_FAILURE = "验证码发送失败，请稍后再试";

    private final SmsConfig smsConfig;
    private final AliyunSmsGateway gateway;

    public AliyunSmsSender(SmsConfig smsConfig, AliyunSmsGateway gateway) {
        this.smsConfig = smsConfig;
        this.gateway = gateway;
    }

    @Override
    public SmsSendResult send(String phone, String scene, String code) {
        if (!isValidPhone(phone) || !isValidCode(code) || !isValidParamName(smsConfig.getCodeParamName())) {
            return SmsSendResult.failure(PROVIDER, GENERIC_FAILURE);
        }
        String templateParam = "{\"" + smsConfig.getCodeParamName() + "\":\"" + code + "\"}";
        try {
            String providerCode = gateway.send(phone, smsConfig.getSignName(), smsConfig.getTemplateCode(), templateParam);
            if (!"OK".equalsIgnoreCase(providerCode)) {
                log.warn("event=verification_sms_rejected provider={} scene={}", PROVIDER, scene);
                return SmsSendResult.failure(PROVIDER, GENERIC_FAILURE);
            }
            log.info("event=verification_sms_accepted provider={} scene={}", PROVIDER, scene);
            return SmsSendResult.success(PROVIDER, "验证码已发送");
        } catch (Exception e) {
            log.error("event=verification_sms_delivery_failed provider={} scene={} errorType={}",
                    PROVIDER, scene, e.getClass().getSimpleName());
            return SmsSendResult.failure(PROVIDER, GENERIC_FAILURE);
        }
    }

    private boolean isValidPhone(String phone) {
        return phone != null && phone.matches("^1[3-9]\\d{9}$");
    }

    private boolean isValidCode(String code) {
        return code != null && code.matches("^\\d{4,8}$");
    }

    private boolean isValidParamName(String name) {
        return name != null && name.matches("^[A-Za-z][A-Za-z0-9_]{0,31}$");
    }

}
