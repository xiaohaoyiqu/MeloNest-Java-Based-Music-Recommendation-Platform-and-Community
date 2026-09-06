package com.haoran.music.common.config;

import java.util.ArrayList;
import java.util.List;

import com.haoran.music.websocket.ModerationWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

   
                      
                            
   
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ModerationWebSocketHandler moderationWebSocketHandler;
    private final SecurityConfig securityConfig;
    private final WebSocketAuthHandshakeInterceptor authHandshakeInterceptor;

    public WebSocketConfig(ModerationWebSocketHandler moderationWebSocketHandler,
                           SecurityConfig securityConfig,
                           WebSocketAuthHandshakeInterceptor authHandshakeInterceptor) {
        this.moderationWebSocketHandler = moderationWebSocketHandler;
        this.securityConfig = securityConfig;
        this.authHandshakeInterceptor = authHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
                         
        String[] allowedOrigins = resolveAllowedOrigins();
        registry.addHandler(moderationWebSocketHandler, "/ws/moderation")
                .addInterceptors(authHandshakeInterceptor)
                .setAllowedOrigins(allowedOrigins);

        registry.addHandler(moderationWebSocketHandler, "/ws/notifications")
                .addInterceptors(authHandshakeInterceptor)
                .setAllowedOrigins(allowedOrigins);
    }

    private String[] resolveAllowedOrigins() {
        List<String> configured = securityConfig == null ? null : securityConfig.getAllowedOrigins();
        if (configured == null || configured.isEmpty()) {
            return new String[]{"http://localhost:3000", "http://127.0.0.1:3000"};
        }
        List<String> safeOrigins = new ArrayList<>();
        for (String origin : configured) {
            if (origin != null && !origin.trim().isEmpty() && !"*".equals(origin.trim())) {
                safeOrigins.add(origin.trim());
            }
        }
        return safeOrigins.isEmpty()
                ? new String[]{"http://localhost:3000", "http://127.0.0.1:3000"}
                : safeOrigins.toArray(new String[0]);
    }
}
