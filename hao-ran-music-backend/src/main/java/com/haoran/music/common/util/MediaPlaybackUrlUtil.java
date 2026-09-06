   
                      
   
package com.haoran.music.common.util;

import cn.hutool.core.util.StrUtil;

   
                                                                   
   
public final class MediaPlaybackUrlUtil {

    private static final String API_PREFIX = "/api";

    private MediaPlaybackUrlUtil() {
    }

    public static String songUrl(Long songId, String quality, String sourceUrl) {
        if (songId == null || StrUtil.isBlank(sourceUrl)) {
            return null;
        }
        return API_PREFIX + "/song/stream/" + songId + "?quality=" + quality;
    }

    public static String mvUrl(Long mvId, String quality, String sourceUrl) {
        if (mvId == null || StrUtil.isBlank(sourceUrl)) {
            return null;
        }
        return API_PREFIX + "/mv/stream/" + mvId + "?quality=" + quality;
    }
}
