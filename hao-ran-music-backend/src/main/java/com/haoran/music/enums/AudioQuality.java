package com.haoran.music.enums;

import lombok.Getter;





@Getter
public enum AudioQuality {



    STANDARD(1, "标准音质", 0, 128),




    HIGH(2, "高品质", 128, 320),




    LOSSLESS(3, "无损音质", 320, Integer.MAX_VALUE);

    private final Integer code;
    private final String name;
    private final Integer minBitrate;
    private final Integer maxBitrate;

    AudioQuality(Integer code, String name, Integer minBitrate, Integer maxBitrate) {
        this.code = code;
        this.name = name;
        this.minBitrate = minBitrate;
        this.maxBitrate = maxBitrate;
    }







    public static AudioQuality fromBitrate(Integer bitrate, boolean isLossless) {
        if (isLossless) {
            return LOSSLESS;
        }
        if (bitrate == null) {
            return STANDARD;
        }
        for (AudioQuality quality : values()) {
            if (bitrate >= quality.minBitrate && bitrate < quality.maxBitrate) {
                return quality;
            }
        }
        return LOSSLESS;
    }




    public static AudioQuality fromCode(Integer code) {
        if (code == null) {
            return STANDARD;
        }
        for (AudioQuality quality : values()) {
            if (quality.code.equals(code)) {
                return quality;
            }
        }
        return STANDARD;
    }
}
