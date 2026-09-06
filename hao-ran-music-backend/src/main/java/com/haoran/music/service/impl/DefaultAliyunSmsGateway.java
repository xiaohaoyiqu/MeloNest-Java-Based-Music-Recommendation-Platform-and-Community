   
                      
   
package com.haoran.music.service.impl;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import com.haoran.music.common.config.SmsConfig;
import com.haoran.music.service.AliyunSmsGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

   
                                                                                                      
                                                        
   
@Component
@ConditionalOnProperty(prefix = "sms", name = "enabled", havingValue = "true")
public class DefaultAliyunSmsGateway implements AliyunSmsGateway {

    private final Client client;
    private final RuntimeOptions runtimeOptions;

    public DefaultAliyunSmsGateway(SmsConfig smsConfig) throws Exception {
        requireAliyunConfiguration(smsConfig);
        Config sdkConfig = new Config()
                .setAccessKeyId(smsConfig.getAccessKeyId())
                .setAccessKeySecret(smsConfig.getAccessKeySecret());
        sdkConfig.endpoint = smsConfig.getEndpoint().trim();
        this.client = new Client(sdkConfig);
        this.runtimeOptions = new RuntimeOptions()
                .setAutoretry(false)
                .setConnectTimeout(safeTimeout(smsConfig.getConnectTimeoutMillis(), 3000))
                .setReadTimeout(safeTimeout(smsConfig.getReadTimeoutMillis(), 5000));
    }

    @Override
    public String send(String phone, String signName, String templateCode, String templateParam) throws Exception {
        SendSmsRequest request = new SendSmsRequest()
                .setPhoneNumbers(phone)
                .setSignName(signName)
                .setTemplateCode(templateCode)
                .setTemplateParam(templateParam);
        SendSmsResponse response = client.sendSmsWithOptions(request, runtimeOptions);
        return response == null || response.getBody() == null ? null : response.getBody().getCode();
    }

    private void requireAliyunConfiguration(SmsConfig config) {
        if (config == null || !"aliyun".equalsIgnoreCase(trim(config.getProvider()))) {
            throw new IllegalStateException("SMS_ENABLED=true currently requires SMS_PROVIDER=aliyun");
        }
        requireConfigured("SMS_ACCESS_KEY_ID", config.getAccessKeyId());
        requireConfigured("SMS_ACCESS_KEY_SECRET", config.getAccessKeySecret());
        requireConfigured("SMS_SIGN_NAME", config.getSignName());
        requireConfigured("SMS_TEMPLATE_CODE", config.getTemplateCode());
        requireConfigured("SMS_ENDPOINT", config.getEndpoint());
    }

    private void requireConfigured(String name, String value) {
        if (trim(value).isEmpty()) {
            throw new IllegalStateException(name + " must be configured when SMS is enabled");
        }
    }

    private int safeTimeout(Integer value, int fallback) {
        return value == null || value < 1000 ? fallback : Math.min(value, 30000);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
