   
                      
   
package com.haoran.music.common.util;

   
                                                                                
                                                                 
   
public final class ValidPlayPolicy {

    private static final int DEFAULT_THRESHOLD_SECONDS = 30;
    private static final int MIN_SHORT_TRACK_THRESHOLD_SECONDS = 5;

    private ValidPlayPolicy() {
    }

    public static boolean isQualified(Integer progressSeconds, Integer durationSeconds) {
        if (progressSeconds == null || progressSeconds < 0) {
            return false;
        }
        int threshold = DEFAULT_THRESHOLD_SECONDS;
        if (durationSeconds != null && durationSeconds > 0) {
            threshold = Math.min(DEFAULT_THRESHOLD_SECONDS,
                    Math.max(MIN_SHORT_TRACK_THRESHOLD_SECONDS, (durationSeconds + 1) / 2));
            if (progressSeconds > durationSeconds + 5) {
                return false;
            }
        }
        return progressSeconds >= threshold;
    }
}
