


package com.haoran.music.common.config;

import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.util.JwtUtils;
import com.haoran.music.common.util.AuthCookieUtil;
import com.haoran.music.service.UserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;








@Component
public class WebSocketAuthHandshakeInterceptor implements HandshakeInterceptor {

    public static final String AUTH_SUBPROTOCOL = "haoran-auth";

    private final JwtUtils jwtUtils;
    private final SecurityConfig securityConfig;
    private final UserService userService;

    public WebSocketAuthHandshakeInterceptor(JwtUtils jwtUtils, SecurityConfig securityConfig,
                                             UserService userService) {
        this.jwtUtils = jwtUtils;
        this.securityConfig = securityConfig;
        this.userService = userService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String token = extractBearerToken(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        String requestedProtocol = request.getHeaders().getFirst("Sec-WebSocket-Protocol");
        if (isBlank(token)) {
            token = AuthCookieUtil.resolveCookieHeader(request.getHeaders().getFirst(HttpHeaders.COOKIE));
        }
        if (isBlank(token)) {
            token = extractSubprotocolToken(requestedProtocol);
        }
        if (isBlank(token) && securityConfig != null && securityConfig.isQueryTokenEnabled()) {
            token = UriComponentsBuilder.fromUri(request.getURI())
                    .build()
                    .getQueryParams()
                    .getFirst("token");
        }

        if (isBlank(token) || jwtUtils == null || userService == null || !userService.validateToken(token)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        try {
            Long userId = jwtUtils.getUserIdFromToken(token);
            if (userId == null) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            attributes.put(CommonConstants.USER_ID_KEY, userId);
            if (containsAuthSubprotocol(requestedProtocol)) {
                response.getHeaders().set("Sec-WebSocket-Protocol", AUTH_SUBPROTOCOL);
            }
            return true;
        } catch (Exception e) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {

    }

    static String extractSubprotocolToken(String rawHeader) {
        if (isBlank(rawHeader)) {
            return null;
        }
        String[] protocols = rawHeader.split(",");
        for (int i = 0; i < protocols.length - 1; i++) {
            if (AUTH_SUBPROTOCOL.equals(protocols[i].trim())) {
                String token = protocols[i + 1].trim();
                return token.isEmpty() ? null : token;
            }
        }
        return null;
    }

    static boolean containsAuthSubprotocol(String rawHeader) {
        if (isBlank(rawHeader)) {
            return false;
        }
        for (String protocol : rawHeader.split(",")) {
            if (AUTH_SUBPROTOCOL.equals(protocol.trim())) {
                return true;
            }
        }
        return false;
    }

    private static String extractBearerToken(String header) {
        if (isBlank(header)) {
            return null;
        }
        String normalized = header.trim();
        if (normalized.regionMatches(true, 0, CommonConstants.TOKEN_PREFIX, 0,
                CommonConstants.TOKEN_PREFIX.length())) {
            return normalized.substring(CommonConstants.TOKEN_PREFIX.length()).trim();
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
