




package com.haoran.music.enums;





public enum VideoQuality {





    SD(0, "sd", "标清", 480, false),





    HD(1, "hd", "高清", 720, false),





    FHD(2, "fhd", "全高清", 1080, false),





    UHD_4K(3, "4k", "4K超高清", 2160, true);




    private final int level;




    private final String code;




    private final String name;




    private final int resolution;




    private final boolean requireVip;

    VideoQuality(int level, String code, String name, int resolution, boolean requireVip) {
        this.level = level;
        this.code = code;
        this.name = name;
        this.resolution = resolution;
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

    public int getResolution() {
        return resolution;
    }

    public boolean isRequireVip() {
        return requireVip;
    }




    public static VideoQuality fromCode(String code) {
        for (VideoQuality quality : values()) {
            if (quality.code.equals(code)) {
                return quality;
            }
        }
        return SD;
    }




    public static VideoQuality fromLevel(int level) {
        for (VideoQuality quality : values()) {
            if (quality.level == level) {
                return quality;
            }
        }
        return SD;
    }




    public VideoQuality next() {
        int nextLevel = Math.min(level + 1, UHD_4K.level);
        return fromLevel(nextLevel);
    }




    public VideoQuality previous() {
        int prevLevel = Math.max(level - 1, SD.level);
        return fromLevel(prevLevel);
    }
}
