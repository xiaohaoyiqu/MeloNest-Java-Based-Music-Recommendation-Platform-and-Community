package com.haoran.music.enums;

import lombok.Getter;

   
                      
                        
   
@Getter
public enum VersionType {
       
         
       
    ORIGINAL("original", "原版"),

       
          
       
    REMIX("remix", "混音版"),

       
          
       
    LIVE("live", "现场版"),

       
          
       
    INSTRUMENTAL("instrumental", "伴奏版"),

       
           
       
    ACOUSTIC("acoustic", "不插电版"),

       
          
       
    EXTENDED("extended", "扩展版");

    private final String code;
    private final String name;

    VersionType(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static VersionType fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return ORIGINAL;
        }
        for (VersionType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return ORIGINAL;
    }

    public static VersionType fromName(String name) {
        if (name == null || name.isEmpty()) {
            return ORIGINAL;
        }
        for (VersionType type : values()) {
            if (type.name.equals(name)) {
                return type;
            }
        }
        return ORIGINAL;
    }
}
