




package com.haoran.music.enums;





public enum SoundQuality {





    STANDARD(0, "standard", "标准音质", 128, "mp3", false),





    HIGH(1, "high", "高品质音质", 320, "mp3", false),





    LOSSLESS(2, "lossless", "无损音质", 1411, "flac", true),





    HIRES(3, "hires", "Hi-Res音质", 3000, "flac", true),





    MASTER(4, "master", "母带音质", 9000, "flac", true),





    INSTRUMENTAL(5, "instrumental", "伴奏/原轨", 0, "", true);




    private final int level;




    private final String code;




    private final String name;




    private final int bitrate;




    private final String format;




    private final boolean requireVip;

    SoundQuality(int level, String code, String name, int bitrate, String format, boolean requireVip) {
        this.level = level;
        this.code = code;
        this.name = name;
        this.bitrate = bitrate;
        this.format = format;
        this.requireVip = requireVip;
    }

    public int getLevel() {
        return level;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getBitrate() {
        return bitrate;
    }

    public String getFormat() {
        return format;
    }

    public boolean isRequireVip() {
        return requireVip;
    }




    public static SoundQuality fromCode(String code) {
        for (SoundQuality quality : values()) {
            if (quality.code.equals(code)) {
                return quality;
            }
        }
        return STANDARD;
    }




    public static SoundQuality fromLevel(int level) {
        for (SoundQuality quality : values()) {
            if (quality.level == level) {
                return quality;
            }
        }
        return STANDARD;
    }




    public SoundQuality next() {
        int nextLevel = Math.min(level + 1, MASTER.level);
        return fromLevel(nextLevel);
    }




    public SoundQuality previous() {
        int prevLevel = Math.max(level - 1, STANDARD.level);
        return fromLevel(prevLevel);
    }




    public String getInstrumentalCode() {
        if (this == INSTRUMENTAL) {
            return "instrumental";
        }
        return this.code + "_instrumental";
    }




    public boolean isInstrumental() {
        return this == INSTRUMENTAL;
    }
}
