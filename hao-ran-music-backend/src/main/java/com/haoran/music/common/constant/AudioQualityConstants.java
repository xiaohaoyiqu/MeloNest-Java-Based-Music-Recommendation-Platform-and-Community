package com.haoran.music.common.constant;







public class AudioQualityConstants {




    public static final int QUALITY_STANDARD = 1;


    public static final int QUALITY_HIGH = 2;


    public static final int QUALITY_LOSSLESS = 3;


    public static final int QUALITY_HIRES = 4;


    public static final int QUALITY_MASTER = 5;




    public static final String NAME_STANDARD = "标准音质";


    public static final String NAME_HIGH = "高品质音质";


    public static final String NAME_LOSSLESS = "无损音质";


    public static final String NAME_HIRES = "Hi-Res音质";


    public static final String NAME_MASTER = "母带音质";




    public static final int MASTER_MIN_SAMPLE_RATE = 96000;


    public static final int MASTER_MIN_BIT_DEPTH = 24;


    public static final int HIRES_MIN_SAMPLE_RATE = 48000;


    public static final int HIRES_MIN_BIT_DEPTH = 24;


    public static final int HIGH_MIN_BITRATE = 192;


    public static final String[] LOSSLESS_CODECS = {"flac", "alac", "pcm", "wav"};


    public static final String[] HIGH_QUALITY_FORMATS = {"m4a", "aac", "ogg", "opus"};




    public static final int DEFAULT_DETECT_TIMEOUT = 30000;


    public static final int FFMPEG_VERSION_TIMEOUT = 5;


    public static final int BITRATE_CONVERT_FACTOR = 1000;




    public static final String FORMAT_FLAC = "flac";


    public static final String FORMAT_WAV = "wav";


    public static final String FORMAT_APE = "ape";


    public static final String FORMAT_WV = "wv";


    public static final String FORMAT_MP3 = "mp3";


    public static final String FORMAT_M4A = "m4a";


    public static final String FORMAT_AAC = "aac";


    public static final String FORMAT_OGG = "ogg";


    public static final String FORMAT_OPUS = "opus";








    public static String getQualityName(int qualityCode) {
        switch (qualityCode) {
            case QUALITY_MASTER:
                return NAME_MASTER;
            case QUALITY_HIRES:
                return NAME_HIRES;
            case QUALITY_LOSSLESS:
                return NAME_LOSSLESS;
            case QUALITY_HIGH:
                return NAME_HIGH;
            case QUALITY_STANDARD:
            default:
                return NAME_STANDARD;
        }
    }






    public static boolean isLosslessCodec(String codec) {
        if (codec == null) {
            return false;
        }
        String lowerCodec = codec.toLowerCase();
        for (String lossless : LOSSLESS_CODECS) {
            if (lowerCodec.contains(lossless)) {
                return true;
            }
        }
        return false;
    }






    public static boolean isHighQualityFormat(String format) {
        if (format == null) {
            return false;
        }
        String lowerFormat = format.toLowerCase();
        for (String highFormat : HIGH_QUALITY_FORMATS) {
            if (lowerFormat.equals(highFormat)) {
                return true;
            }
        }
        return false;
    }


    private AudioQualityConstants() {
    }
}
