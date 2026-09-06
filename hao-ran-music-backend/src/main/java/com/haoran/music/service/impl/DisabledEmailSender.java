   
                      
                                                                               
   
package com.haoran.music.service.impl;

import com.haoran.music.common.config.EmailConfig;
import com.haoran.music.common.util.DataMaskingUtil;
import com.haoran.music.service.EmailSendResult;
import com.haoran.music.service.EmailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

   
                                                                                                           
   
@Slf4j
@Service
@ConditionalOnProperty(prefix = "email", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DisabledEmailSender implements EmailSender {

    @Resource
    private EmailConfig emailConfig;

    @Override
    public EmailSendResult send(String email, String scene, String code) {
        String provider = emailConfig.getProvider() == null ? "disabled" : emailConfig.getProvider();
        log.warn("Email provider is not configured: provider={}, email={}, scene={}",
                provider, DataMaskingUtil.maskEmail(email), scene);
        return EmailSendResult.failure(provider, emailConfig.getDisabledMessage());
    }
}
