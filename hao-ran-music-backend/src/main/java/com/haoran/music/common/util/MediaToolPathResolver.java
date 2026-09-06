   
                      
   
package com.haoran.music.common.util;

   
                                                                              
                                                                           
                                                
   
public final class MediaToolPathResolver {

    public static final String DEFAULT_FFMPEG_PATH =
            "/usr/local/soft/ffmpeg-6.1.1/bin/ffmpeg";
    public static final String DEFAULT_FFPROBE_PATH =
            "/usr/local/soft/ffmpeg-6.1.1/bin/ffprobe";

    private MediaToolPathResolver() {
    }

    public static String ffmpeg() {
        return resolve("haoran.media.ffmpeg.path", "FFMPEG_PATH", DEFAULT_FFMPEG_PATH);
    }

    public static String ffprobe() {
        return resolve("haoran.media.ffprobe.path", "FFPROBE_PATH", DEFAULT_FFPROBE_PATH);
    }

    private static String resolve(String propertyName, String environmentName, String defaultPath) {
        String propertyValue = System.getProperty(propertyName);
        if (hasText(propertyValue)) {
            return propertyValue.trim();
        }
        String environmentValue = System.getenv(environmentName);
        return hasText(environmentValue) ? environmentValue.trim() : defaultPath;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
