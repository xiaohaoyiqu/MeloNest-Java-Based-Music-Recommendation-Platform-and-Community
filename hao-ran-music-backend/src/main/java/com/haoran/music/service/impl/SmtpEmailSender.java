   
                      
   
package com.haoran.music.service.impl;

import com.haoran.music.common.config.EmailConfig;
import com.haoran.music.common.util.DataMaskingUtil;
import com.haoran.music.service.EmailSendResult;
import com.haoran.music.service.EmailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

   
                                                                                
   
@Slf4j
@Service
@ConditionalOnProperty(prefix = "email", name = "enabled", havingValue = "true")
public class SmtpEmailSender implements EmailSender {

    private static final String PROVIDER = "smtp";
    private static final String GENERIC_FAILURE = "验证码发送失败，请稍后再试";

    private final EmailConfig emailConfig;
    private final JavaMailSender mailSender;

    @Autowired
    public SmtpEmailSender(EmailConfig emailConfig, JavaMailSender mailSender, MailProperties mailProperties) {
        this(emailConfig, mailSender, mailProperties, true);
    }

    SmtpEmailSender(EmailConfig emailConfig, JavaMailSender mailSender) {
        this(emailConfig, mailSender, null, false);
    }

    private SmtpEmailSender(EmailConfig emailConfig, JavaMailSender mailSender,
                            MailProperties mailProperties, boolean validateTransport) {
        if (emailConfig == null || !"smtp".equalsIgnoreCase(emailConfig.getProvider())) {
            throw new IllegalStateException("EMAIL_ENABLED=true currently requires EMAIL_PROVIDER=smtp");
        }
        if (isBlank(emailConfig.getFrom()) || isBlank(emailConfig.getSubject())) {
            throw new IllegalStateException("EMAIL_FROM and EMAIL_SUBJECT must be configured when email is enabled");
        }
        if (validateTransport && (mailProperties == null || isBlank(mailProperties.getHost()))) {
            throw new IllegalStateException("SMTP_HOST must be configured when email is enabled");
        }
        this.emailConfig = emailConfig;
        this.mailSender = mailSender;
    }

    @Override
    public EmailSendResult send(String email, String scene, String code) {
        if (!DataMaskingUtil.isValidEmail(email) || code == null || !code.matches("^\\d{4,8}$")) {
            return EmailSendResult.failure(PROVIDER, GENERIC_FAILURE);
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(emailConfig.getFrom());
            message.setTo(email);
            message.setSubject(emailConfig.getSubject());
            message.setText(buildBody(scene, code));
            mailSender.send(message);
            log.info("event=verification_email_accepted provider={} scene={}", PROVIDER, scene);
            return EmailSendResult.success(PROVIDER, "验证码已发送到邮箱");
        } catch (Exception e) {
            log.error("event=verification_email_delivery_failed provider={} scene={} errorType={}",
                    PROVIDER, scene, e.getClass().getSimpleName());
            return EmailSendResult.failure(PROVIDER, GENERIC_FAILURE);
        }
    }

    private String buildBody(String scene, String code) {
        int expireMinutes = emailConfig.getCodeExpireMinutes() == null || emailConfig.getCodeExpireMinutes() <= 0
                ? 5 : Math.min(emailConfig.getCodeExpireMinutes(), 30);
        return "您的浩然音乐验证码是：" + code + "\n"
                + "有效期 " + expireMinutes + " 分钟。请勿向任何人泄露此验证码。\n"
                + "场景：" + safeScene(scene);
    }

    private String safeScene(String scene) {
        if (scene == null || !scene.matches("^[a-z_]{1,32}$")) {
            return "verification";
        }
        return scene;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
