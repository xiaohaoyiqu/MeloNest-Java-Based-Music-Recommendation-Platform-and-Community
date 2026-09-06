   
                      
   

package com.haoran.music.common.util;

import com.haoran.music.common.constant.CommonConstants;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

                                                                                           
public final class AuthCookieUtil {

    public static final String COOKIE_NAME = "HAORAN_SESSION";

    private AuthCookieUtil() {
    }

    public static String resolveToken(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String header = request.getHeader(CommonConstants.TOKEN_HEADER);
        if (StringUtils.isNotBlank(header)
                && header.regionMatches(true, 0, CommonConstants.TOKEN_PREFIX, 0,
                CommonConstants.TOKEN_PREFIX.length())) {
            return header.substring(CommonConstants.TOKEN_PREFIX.length()).trim();
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName()) && StringUtils.isNotBlank(cookie.getValue())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public static boolean hasSessionCookie(HttpServletRequest request) {
        if (request == null || request.getCookies() == null) {
            return false;
        }
        for (Cookie cookie : request.getCookies()) {
            if (COOKIE_NAME.equals(cookie.getName()) && StringUtils.isNotBlank(cookie.getValue())) {
                return true;
            }
        }
        return false;
    }

    public static String resolveCookieHeader(String cookieHeader) {
        if (StringUtils.isBlank(cookieHeader)) {
            return null;
        }
        for (String part : cookieHeader.split(";")) {
            String trimmed = part.trim();
            int separator = trimmed.indexOf('=');
            if (separator > 0 && COOKIE_NAME.equals(trimmed.substring(0, separator).trim())) {
                String value = trimmed.substring(separator + 1).trim();
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }
}
