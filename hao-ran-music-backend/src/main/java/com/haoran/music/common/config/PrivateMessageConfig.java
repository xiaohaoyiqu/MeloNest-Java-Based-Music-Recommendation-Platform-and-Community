package com.haoran.music.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;






@Data
@Component
@ConfigurationProperties(prefix = "message.private")
public class PrivateMessageConfig {

    private int recallTimeLimitMinutes = 2;
    private String unreadCountPrefix = "unread_count:";
    private int unreadCountCacheTtlSeconds = 300;
    private String conversationPrefix = "conversation:";
    private int textMaxLength = 500;
}
