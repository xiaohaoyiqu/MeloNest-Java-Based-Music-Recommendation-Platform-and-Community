   
                      
   
package com.haoran.music.common.util;

import com.haoran.music.common.config.SecurityConfig;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Locale;

   
                                                                     
   
@Component
public class ClientIpResolver {

    @Resource
    private SecurityConfig securityConfig;

    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String remoteAddress = normalizeIp(request.getRemoteAddr());
        if (!isTrustedProxy(remoteAddress)) {
            return remoteAddress;
        }

        String forwarded = firstIp(request.getHeader("X-Forwarded-For"));
        if (forwarded != null) {
            return forwarded;
        }
        forwarded = normalizeIp(request.getHeader("X-Real-IP"));
        if (forwarded != null) {
            return forwarded;
        }
        forwarded = normalizeIp(request.getHeader("Proxy-Client-IP"));
        if (forwarded != null) {
            return forwarded;
        }
        forwarded = normalizeIp(request.getHeader("WL-Proxy-Client-IP"));
        return forwarded == null ? remoteAddress : forwarded;
    }

    private boolean isTrustedProxy(String remoteAddress) {
        if (remoteAddress == null || securityConfig.getTrustedProxies() == null) {
            return false;
        }
        for (String configured : securityConfig.getTrustedProxies()) {
            String trusted = normalizeIp(configured);
            if (trusted != null && trusted.equalsIgnoreCase(remoteAddress)) {
                return true;
            }
        }
        return false;
    }

    private String firstIp(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return normalizeIp(value.split(",", 2)[0]);
    }

    private String normalizeIp(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || "unknown".equalsIgnoreCase(normalized)
                || normalized.contains("%")) {
            return null;
        }
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return isIpv4(normalized) || isIpv6(normalized) ? normalized : null;
    }

    private boolean isIpv4(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) {
                return false;
            }
            for (int i = 0; i < part.length(); i++) {
                if (!Character.isDigit(part.charAt(i))) {
                    return false;
                }
            }
            try {
                if (Integer.parseInt(part) > 255) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    private boolean isIpv6(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        if (!lower.contains(":") || lower.length() > 45) {
            return false;
        }
        for (int i = 0; i < lower.length(); i++) {
            char ch = lower.charAt(i);
            if (!((ch >= '0' && ch <= '9')
                    || (ch >= 'a' && ch <= 'f')
                    || ch == ':'
                    || ch == '.')) {
                return false;
            }
        }
        return true;
    }
}

