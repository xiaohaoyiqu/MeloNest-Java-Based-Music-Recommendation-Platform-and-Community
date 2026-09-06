package com.haoran.music.enums;





public enum VerifiedLevel {

    NORMAL("normal", "普通认证", 1, "#909399"),
    PREMIUM("premium", "优质认证", 2, "#e6a23c"),
    GOLD("gold", "金牌认证", 3, "#f1c40f");

    private final String code;
    private final String name;
    private final Integer level;
    private final String color;

    VerifiedLevel(String code, String name, Integer level, String color) {
        this.code = code;
        this.name = name;
        this.level = level;
        this.color = color;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Integer getLevel() {
        return level;
    }

    public String getColor() {
        return color;
    }




    public static VerifiedLevel fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (VerifiedLevel level : values()) {
            if (level.code.equals(code)) {
                return level;
            }
        }
        return null;
    }
}
