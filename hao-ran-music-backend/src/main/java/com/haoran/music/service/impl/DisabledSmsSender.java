   
                      
                                                                             
   
package com.haoran.music.service.impl;

import com.haoran.music.common.config.SmsConfig;
import com.haoran.music.service.SmsSendResult;
import com.haoran.music.service.SmsSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

   
                                                                                                         
   
@Slf4j
@Service
@ConditionalOnProperty(prefix = "sms", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DisabledSmsSender implements SmsSender {

    @Resource
    private SmsConfig smsConfig;

    @Override
    public SmsSendResult send(String phone, String scene, String code) {
        String provider = smsConfig.getProvider() == null ? "disabled" : smsConfig.getProvider();
        log.warn("SMS provider is not configured: provider={}, phone={}, scene={}", provider, maskPhone(phone), scene);
        return SmsSendResult.failure(provider, smsConfig.getDisabledMessage());
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "****";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
