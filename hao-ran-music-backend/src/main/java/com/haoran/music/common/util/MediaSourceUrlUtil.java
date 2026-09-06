   
                      
   
package com.haoran.music.common.util;

import cn.hutool.core.util.StrUtil;

import java.net.URI;

   
                                                                   
   
public final class MediaSourceUrlUtil {

    private MediaSourceUrlUtil() {
    }

    public static String toInternalUrl(String sourceUrl, String internalPrefix, String mediaRoot) {
        if (StrUtil.isBlank(sourceUrl) || StrUtil.isBlank(internalPrefix) || StrUtil.isBlank(mediaRoot)) {
            return sourceUrl;
        }
        String normalizedRoot = mediaRoot.startsWith("/") ? mediaRoot : "/" + mediaRoot;
        String pathAndQuery = extractKnownPath(sourceUrl, normalizedRoot);
        if (pathAndQuery == null) {
            return sourceUrl;
        }
        return trimTrailingSlash(internalPrefix) + pathAndQuery;
    }

    private static String extractKnownPath(String sourceUrl, String mediaRoot) {
        String value = sourceUrl.trim();
        if (value.startsWith(mediaRoot)) {
            return value;
        }
        try {
                                                                               
                                                                                
                                                                          
            URI uri = URI.create(UrlHelper.encodePath(value));
            String path = uri.getRawPath();
            if (path == null || !path.startsWith(mediaRoot)) {
                return null;
            }
            return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String trimTrailingSlash(String value) {
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
