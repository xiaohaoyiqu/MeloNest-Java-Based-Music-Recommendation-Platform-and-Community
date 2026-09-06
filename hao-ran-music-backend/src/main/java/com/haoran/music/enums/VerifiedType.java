package com.haoran.music.enums;

   
                      
                      
   
public enum VerifiedType {

    INDIVIDUAL("individual", "个人音乐人", "User", "#409eff"),
    BAND("band", "乐队", "Microphone", "#67c23a"),
    LABEL("label", "唱片公司", "OfficeBuilding", "#e6a23c");

    private final String code;
    private final String name;
    private final String icon;
    private final String color;

    VerifiedType(String code, String name, String icon, String color) {
        this.code = code;
        this.name = name;
        this.icon = icon;
        this.color = color;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getIcon() {
        return icon;
    }

    public String getColor() {
        return color;
    }

       
                 
       
    public static VerifiedType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (VerifiedType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
